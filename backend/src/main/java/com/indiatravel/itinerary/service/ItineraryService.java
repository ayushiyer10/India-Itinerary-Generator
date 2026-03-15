package com.indiatravel.itinerary.service;

import com.indiatravel.itinerary.dto.GenerateTripRequest;
import com.indiatravel.itinerary.dto.ExternalPlaceDto;
import com.indiatravel.itinerary.dto.PlaceDto;
import com.indiatravel.itinerary.dto.RegenerateDayRequest;
import com.indiatravel.itinerary.dto.TripDetailDto;
import com.indiatravel.itinerary.repository.ItineraryRepository;
import com.indiatravel.itinerary.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItineraryService {
    private final ItineraryRepository itineraryRepository;
    private final UserRepository userRepository;
    private final OpenMapService openMapService;
    private final boolean enableExternalEnrichment;

    public ItineraryService(
            ItineraryRepository itineraryRepository,
            UserRepository userRepository,
            OpenMapService openMapService,
            @Value("${maps.external-enrichment-enabled:true}") boolean enableExternalEnrichment
    ) {
        this.itineraryRepository = itineraryRepository;
        this.userRepository = userRepository;
        this.openMapService = openMapService;
        this.enableExternalEnrichment = enableExternalEnrichment;
    }

    @Transactional
    public long generateTrip(GenerateTripRequest request) {
        validateDates(request.startDate(), request.endDate());
        if (!userRepository.existsById(request.userId())) {
            throw new NoSuchElementException("User not found for id " + request.userId());
        }
        long tripId = itineraryRepository.createTrip(
                request.userId(),
                request.title(),
                request.startDate(),
                request.endDate(),
                request.budgetInr(),
                request.pace(),
                request.preferredTravelMode()
        );

        int totalDays = (int) ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        int dailyBudget = Math.max(500, request.budgetInr() / Math.max(totalDays, 1));
        int placeCount = getPlaceCountByPace(request.pace());
        List<String> optimizedCities = optimizeCitySequence(request.cities());

        Map<String, Integer> cityDayCounts = new LinkedHashMap<>();
        Map<String, String> cityOriginalName = new LinkedHashMap<>();
        for (int i = 0; i < totalDays; i++) {
            String city = optimizedCities.get(i % optimizedCities.size());
            String cityKey = normalizeCityKey(city);
            cityDayCounts.merge(cityKey, 1, Integer::sum);
            cityOriginalName.putIfAbsent(cityKey, city.trim());
        }
        List<String> dayCitySequence = buildDayCitySequence(cityDayCounts, cityOriginalName, optimizedCities);

        Map<String, List<PlaceDto>> cityPools = new LinkedHashMap<>();
        Map<String, Integer> cityDayTargets = new LinkedHashMap<>();
        Map<String, Integer> cityCursor = new LinkedHashMap<>();
        Map<String, Set<Long>> cityUsedPlaceIds = new LinkedHashMap<>();
        Map<String, Set<String>> cityUsedPlaceNames = new LinkedHashMap<>();
        for (String cityKey : cityDayCounts.keySet()) {
            int requiredUnique = cityDayCounts.get(cityKey) * placeCount + 2;
            List<PlaceDto> cityPool = buildCityPool(cityOriginalName.get(cityKey), List.of(), requiredUnique);
            cityPools.put(cityKey, cityPool);
            cityDayTargets.put(cityKey, computeCityDayTarget(placeCount, cityDayCounts.get(cityKey), cityPool));
            cityCursor.put(cityKey, 0);
            cityUsedPlaceIds.put(cityKey, new LinkedHashSet<>());
            cityUsedPlaceNames.put(cityKey, new LinkedHashSet<>());
        }

        for (int i = 0; i < totalDays; i++) {
            int dayNumber = i + 1;
            String city = dayCitySequence.get(i);
            String cityKey = normalizeCityKey(city);
            List<PlaceDto> pool = cityPools.getOrDefault(cityKey, List.of());
            Set<Long> usedPlaceIds = cityUsedPlaceIds.computeIfAbsent(cityKey, ignored -> new LinkedHashSet<>());
            Set<String> usedPlaceNames = cityUsedPlaceNames.computeIfAbsent(cityKey, ignored -> new LinkedHashSet<>());
            String normalizedTheme = "tour";
            int dayPlaceCount = cityDayTargets.getOrDefault(cityKey, placeCount);
            int minimumStops = computeGuaranteedMinimumStops(pool, dayPlaceCount);
            int requestedStops = Math.max(dayPlaceCount, minimumStops);
            pool = ensureThemePool(city, pool, normalizedTheme, dayPlaceCount + 2, false);
            cityPools.put(cityKey, pool);
            int cursor = cityCursor.getOrDefault(cityKey, 0);
            List<PlaceDto> places = selectPlacesForTheme(pool, normalizedTheme, requestedStops, cursor, usedPlaceIds, usedPlaceNames);
            if (places.size() < requestedStops) {
                pool = ensureThemePool(city, pool, normalizedTheme, dayPlaceCount + 6, false);
                pool = expandCityPoolFromLive(city, pool, dayPlaceCount + 2);
                cityPools.put(cityKey, pool);
                cityDayTargets.put(cityKey, computeCityDayTarget(placeCount, cityDayCounts.getOrDefault(cityKey, 1), pool));
                dayPlaceCount = cityDayTargets.getOrDefault(cityKey, dayPlaceCount);
                minimumStops = computeGuaranteedMinimumStops(pool, dayPlaceCount);
                requestedStops = Math.max(dayPlaceCount, minimumStops);
                places = selectPlacesForTheme(pool, normalizedTheme, requestedStops, cursor, usedPlaceIds, usedPlaceNames);
            }
            if (places.size() < requestedStops) {
                places = selectFallbackPlaces(pool, requestedStops, cursor, usedPlaceIds, usedPlaceNames);
            }
            if (places.isEmpty()) {
                pool = expandCityPoolFromLive(city, pool, dayPlaceCount + 1);
                cityPools.put(cityKey, pool);
                minimumStops = computeGuaranteedMinimumStops(pool, dayPlaceCount);
                requestedStops = Math.max(dayPlaceCount, minimumStops);
                places = selectPlacesForTheme(pool, normalizedTheme, requestedStops, cursor + dayNumber, usedPlaceIds, usedPlaceNames);
            }
            if (places.size() < minimumStops) {
                List<PlaceDto> relaxed = selectPlacesForTheme(
                        pool,
                        normalizedTheme,
                        minimumStops,
                        cursor + dayNumber,
                        Set.of(),
                        Set.of()
                );
                if (relaxed.size() > places.size()) {
                    places = relaxed;
                }
            }
            if (places.size() < minimumStops) {
                places = topUpDayWithNearbyReuse(places, pool, minimumStops, cursor + dayNumber);
            }
            places = orderByNearestNeighbor(places);
            places.forEach(place -> {
                usedPlaceIds.add(place.id());
                usedPlaceNames.add(normalizeNameKey(place.name()));
            });
            int poolSize = Math.max(pool.size(), 1);
            int advance = Math.max(1, places.size());
            if (advance % poolSize == 0) {
                advance = Math.max(1, advance - 1);
            }
            cityCursor.put(cityKey, cursor + advance);
            createDayPlan(tripId, dayNumber, city, normalizedTheme, places, request.preferredTravelMode(), dailyBudget);
        }

        return tripId;
    }

    @Transactional(readOnly = true)
    public TripDetailDto getTrip(long tripId) {
        return itineraryRepository.findTripById(tripId)
                .orElseThrow(() -> new NoSuchElementException("Trip not found for id " + tripId));
    }

    @Transactional(readOnly = true)
    public List<PlaceDto> searchPlaces(String city, String query, int limit) {
        if (query == null || query.isBlank()) {
            return itineraryRepository.findPlacesByCity(city, limit);
        }
        return itineraryRepository.findPlaces(city, List.of(query), limit);
    }

    @Transactional
    public void regenerateDay(long tripId, int dayNumber, RegenerateDayRequest request) {
        TripDetailDto existing = getTrip(tripId);
        String city = existing.days().stream()
                .filter(d -> d.dayNumber() == dayNumber)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Day " + dayNumber + " does not exist"))
                .city();

        itineraryRepository.deleteTripDayPlan(tripId, dayNumber);
        int placeCount = getPlaceCountByPace(existing.pace());
        int requiredUnique = Math.max(existing.days().size(), 1) * placeCount + 2;
        Set<Long> usedByOtherDays = existing.days().stream()
                .filter(d -> d.dayNumber() != dayNumber)
                .flatMap(d -> d.places().stream())
                .map(p -> p.placeId())
                .collect(Collectors.toSet());
        Set<String> usedNamesByOtherDays = existing.days().stream()
                .filter(d -> d.dayNumber() != dayNumber)
                .flatMap(d -> d.places().stream())
                .map(p -> normalizeNameKey(p.name()))
                .collect(Collectors.toSet());

        List<PlaceDto> pool = buildCityPool(city, List.of(), requiredUnique);
        String normalizedTheme = "tour";
        int minimumStops = computeGuaranteedMinimumStops(pool, placeCount);
        int requestedStops = Math.max(placeCount, minimumStops);
        pool = ensureThemePool(city, pool, normalizedTheme, placeCount + 4, false);
        List<PlaceDto> places = selectPlacesForTheme(pool, normalizedTheme, requestedStops, Math.max(0, dayNumber - 1) * placeCount, usedByOtherDays, usedNamesByOtherDays);
        if (places.size() < requestedStops) {
            pool = ensureThemePool(city, pool, normalizedTheme, placeCount + 8, false);
            pool = expandCityPoolFromLive(city, pool, placeCount + 2);
            minimumStops = computeGuaranteedMinimumStops(pool, placeCount);
            requestedStops = Math.max(placeCount, minimumStops);
            places = selectPlacesForTheme(pool, normalizedTheme, requestedStops, Math.max(0, dayNumber - 1) * placeCount, usedByOtherDays, usedNamesByOtherDays);
        }
        if (places.size() < requestedStops) {
            places = selectFallbackPlaces(pool, requestedStops, Math.max(0, dayNumber - 1) * placeCount, usedByOtherDays, usedNamesByOtherDays);
        }
        if (places.isEmpty()) {
            pool = expandCityPoolFromLive(city, pool, placeCount + 1);
            minimumStops = computeGuaranteedMinimumStops(pool, placeCount);
            requestedStops = Math.max(placeCount, minimumStops);
            places = selectPlacesForTheme(pool, normalizedTheme, requestedStops, Math.max(0, dayNumber) * placeCount, usedByOtherDays, usedNamesByOtherDays);
        }
        if (places.size() < minimumStops) {
            List<PlaceDto> relaxed = selectPlacesForTheme(
                    pool,
                    normalizedTheme,
                    minimumStops,
                    Math.max(0, dayNumber) * placeCount,
                    Set.of(),
                    Set.of()
            );
            if (relaxed.size() > places.size()) {
                places = relaxed;
            }
        }
        if (places.size() < minimumStops) {
            places = topUpDayWithNearbyReuse(places, pool, minimumStops, Math.max(0, dayNumber) * placeCount);
        }
        places = orderByNearestNeighbor(places);
        int daysCount = Math.max(existing.days().size(), 1);
        int dailyBudget = existing.budgetInr() / daysCount;
        String mode = request.preferredTravelMode() == null || request.preferredTravelMode().isBlank()
                ? existing.preferredTravelMode() : request.preferredTravelMode();

        createDayPlan(tripId, dayNumber, city, normalizedTheme, places, mode, dailyBudget);
    }

    private String pickBestTheme(
            List<String> interests,
            List<PlaceDto> pool,
            Set<Long> blockedIds,
            Set<String> blockedNameKeys,
            Map<String, Integer> themeUsage
    ) {
        List<String> normalized = normalizeInterests(interests);
        if (normalized.isEmpty()) {
            normalized = deriveInterestsFromPool(pool);
        }
        if (normalized.isEmpty()) {
            return "city";
        }

        String bestTheme = normalized.getFirst();
        int bestScore = Integer.MIN_VALUE;

        for (String theme : normalized) {
            int available = (int) pool.stream()
                    .filter(place -> !blockedIds.contains(place.id()))
                    .filter(place -> !blockedNameKeys.contains(normalizeNameKey(place.name())))
                    .filter(place -> !isSyntheticPlace(place))
                    .filter(place -> isPlaceSuitableForTheme(place, theme))
                    .count();
            int usagePenalty = themeUsage.getOrDefault(theme, 0) * 3;
            int score = available * 10 - usagePenalty;
            if (score > bestScore) {
                bestScore = score;
                bestTheme = theme;
            }
        }
        return bestTheme;
    }

    private String inferThemeFromSelectedPlaces(List<String> interests, List<PlaceDto> places, Map<String, Integer> themeUsage) {
        List<String> normalized = normalizeInterests(interests);
        if (normalized.isEmpty()) {
            normalized = deriveInterestsFromPool(places);
        }
        if (places.isEmpty()) {
            return normalized.isEmpty() ? "city" : normalized.getFirst();
        }
        if (normalized.isEmpty()) {
            return "city";
        }

        String bestTheme = normalized.getFirst();
        int bestScore = Integer.MIN_VALUE;
        for (String theme : normalized) {
            int matches = (int) places.stream()
                    .filter(place -> !isSyntheticPlace(place))
                    .filter(place -> isPlaceSuitableForTheme(place, theme))
                    .count();
            int usagePenalty = themeUsage.getOrDefault(theme, 0) * 2;
            int score = matches * 10 - usagePenalty;
            if (score > bestScore) {
                bestScore = score;
                bestTheme = theme;
            }
        }
        return bestTheme;
    }

    private void createDayPlan(long tripId, int dayNumber, String city, String theme, List<PlaceDto> places, String travelMode, int dailyBudget) {
        int estimatedCost = places.stream().mapToInt(PlaceDto::averageCostInr).sum();
        int totalTravelMinutes = Math.max(30, (places.size() - 1) * 35);
        String notes = "City-aware plan with " + places.size() + " stops optimized for realistic local travel windows.";
        long dayId = itineraryRepository.createTripDay(tripId, dayNumber, city, theme, notes, Math.min(estimatedCost, dailyBudget), totalTravelMinutes);

        LocalTime cursor = LocalTime.of(9, 0);
        for (int idx = 0; idx < places.size(); idx++) {
            PlaceDto place = places.get(idx);
            int travelMinutes = idx == 0 ? 0 : 35;
            LocalTime arrival = cursor.plusMinutes(travelMinutes);
            LocalTime departure = arrival.plusMinutes(place.visitMinutes());
            itineraryRepository.addPlaceToTripDay(dayId, place.id(), idx + 1, arrival, departure, normalizeMode(travelMode), travelMinutes);
            cursor = departure;
        }
    }

    private int getPlaceCountByPace(String pace) {
        return switch (pace.toLowerCase(Locale.ROOT)) {
            case "relaxed" -> 3;
            case "fast" -> 5;
            default -> 4;
        };
    }

    private int computeCityDayTarget(int preferredPerDay, int cityDayCount, List<PlaceDto> pool) {
        int days = Math.max(1, cityDayCount);
        int usableUnique = countUsableUniquePlaces(pool);
        if (usableUnique <= 0) {
            return 1;
        }
        if (usableUnique >= preferredPerDay * days) {
            return preferredPerDay;
        }
        int evenlyDistributed = Math.max(1, (usableUnique + days - 1) / days);
        int minimumTourStops = preferredPerDay >= 3 ? 2 : 1;
        if (usableUnique >= days + 1) {
            minimumTourStops = Math.max(minimumTourStops, 2);
        }
        evenlyDistributed = Math.max(minimumTourStops, evenlyDistributed);
        return Math.min(preferredPerDay, evenlyDistributed);
    }

    private int countUsableUniquePlaces(List<PlaceDto> pool) {
        if (pool == null || pool.isEmpty()) {
            return 0;
        }
        Set<String> names = new LinkedHashSet<>();
        for (PlaceDto place : pool) {
            if (place == null) {
                continue;
            }
            String name = place.name();
            if (isGenericPlaceName(name)
                    || isGeneratedTemplateName(name)
                    || isLikelyNonAttractionName(name)
                    || isSyntheticPlace(place)) {
                continue;
            }
            names.add(normalizeNameKey(name));
        }
        return names.size();
    }

    private int computeGuaranteedMinimumStops(List<PlaceDto> pool, int desired) {
        if (desired <= 1 || pool == null || pool.isEmpty()) {
            return 1;
        }
        int distinctCandidates = countDistinctCandidatePlaces(pool);
        if (distinctCandidates >= 2 && desired >= 3) {
            return 2;
        }
        return distinctCandidates >= 1 ? 1 : 1;
    }

    private int countDistinctCandidatePlaces(List<PlaceDto> pool) {
        if (pool == null || pool.isEmpty()) {
            return 0;
        }
        Set<String> names = new LinkedHashSet<>();
        for (PlaceDto place : pool) {
            if (place == null) {
                continue;
            }
            String name = place.name();
            if (isGenericPlaceName(name)
                    || isLikelyNonAttractionName(name)) {
                continue;
            }
            names.add(normalizeNameKey(name));
        }
        return names.size();
    }

    private List<String> optimizeCitySequence(List<String> inputCities) {
        List<String> cleaned = inputCities.stream()
                .filter(city -> city != null && !city.isBlank())
                .map(String::trim)
                .toList();
        if (cleaned.size() < 2) {
            return cleaned;
        }

        Map<String, Integer> cityCounts = new LinkedHashMap<>();
        Map<String, String> cityOriginalName = new LinkedHashMap<>();
        for (String city : cleaned) {
            String cityKey = normalizeCityKey(city);
            cityCounts.merge(cityKey, 1, Integer::sum);
            cityOriginalName.putIfAbsent(cityKey, city);
        }

        List<String> cityKeys = new ArrayList<>(cityCounts.keySet());
        if (cityKeys.size() < 2) {
            return cleaned;
        }

        Map<String, GeoPoint> cityPoints = new LinkedHashMap<>();
        for (String cityKey : cityKeys) {
            resolveCityPoint(cityOriginalName.get(cityKey)).ifPresent(point -> cityPoints.put(cityKey, point));
        }
        if (cityPoints.size() < 2) {
            return cleaned;
        }

        List<String> orderedCityKeys = new ArrayList<>();
        String current = cityPoints.containsKey(cityKeys.get(0))
                ? cityKeys.get(0)
                : cityPoints.keySet().iterator().next();

        Set<String> unvisited = new LinkedHashSet<>(cityPoints.keySet());
        unvisited.remove(current);
        orderedCityKeys.add(current);

        while (!unvisited.isEmpty()) {
            String nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (String candidate : unvisited) {
                double distance = haversineKm(cityPoints.get(current), cityPoints.get(candidate));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
            current = nearest;
            orderedCityKeys.add(nearest);
            unvisited.remove(nearest);
        }

        for (String cityKey : cityKeys) {
            if (!cityPoints.containsKey(cityKey)) {
                orderedCityKeys.add(cityKey);
            }
        }

        List<String> optimized = new ArrayList<>();
        for (String cityKey : orderedCityKeys) {
            int count = cityCounts.getOrDefault(cityKey, 1);
            for (int i = 0; i < count; i++) {
                optimized.add(cityOriginalName.get(cityKey));
            }
        }
        return optimized;
    }

    private List<String> buildDayCitySequence(
            Map<String, Integer> cityDayCounts,
            Map<String, String> cityOriginalName,
            List<String> optimizedCities
    ) {
        List<String> sequence = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();

        for (String city : optimizedCities) {
            String cityKey = normalizeCityKey(city);
            if (!added.add(cityKey)) {
                continue;
            }
            int count = cityDayCounts.getOrDefault(cityKey, 0);
            String name = cityOriginalName.getOrDefault(cityKey, city);
            for (int i = 0; i < count; i++) {
                sequence.add(name);
            }
        }

        for (String cityKey : cityDayCounts.keySet()) {
            if (!added.add(cityKey)) {
                continue;
            }
            int count = cityDayCounts.getOrDefault(cityKey, 0);
            String name = cityOriginalName.getOrDefault(cityKey, cityKey);
            for (int i = 0; i < count; i++) {
                sequence.add(name);
            }
        }

        return sequence;
    }

    private Optional<GeoPoint> resolveCityPoint(String city) {
        List<PlaceDto> localMatches = itineraryRepository.findPlacesByCityWide(city, 1);
        if (!localMatches.isEmpty()) {
            PlaceDto place = localMatches.get(0);
            return Optional.of(new GeoPoint(place.latitude(), place.longitude()));
        }
        try {
            List<ExternalPlaceDto> externalMatches = openMapService.searchPlace("city center", city, 1);
            if (!externalMatches.isEmpty()) {
                ExternalPlaceDto place = externalMatches.get(0);
                return Optional.of(new GeoPoint(place.latitude(), place.longitude()));
            }
        } catch (Exception ignored) {
            // If city lookup fails, planner falls back to original city order.
        }
        return Optional.empty();
    }

    private static double haversineKm(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lon1 = Math.toRadians(from.longitude());
        double lat2 = Math.toRadians(to.latitude());
        double lon2 = Math.toRadians(to.longitude());
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }

    private static String normalizeCityKey(String city) {
        return city.toLowerCase(Locale.ROOT).trim();
    }

    private List<PlaceDto> buildCityPool(String city, List<String> interests, int targetSize) {
        Map<Long, PlaceDto> byId = new LinkedHashMap<>();
        List<String> normalizedInterests = normalizeInterests(interests);

        if (enableExternalEnrichment) {
            int liveNeeded = normalizedInterests.isEmpty()
                    ? Math.max(12, targetSize + 4)
                    : Math.max(10, targetSize + 3);
            fetchAndPersistExternal(city, normalizedInterests, liveNeeded, normalizedInterests.isEmpty())
                    .forEach(place -> byId.putIfAbsent(place.id(), place));
            if (byId.size() < targetSize) {
                fetchAndPersistExternal(city, List.of(), Math.max(8, targetSize / 2 + 2), false)
                        .forEach(place -> byId.putIfAbsent(place.id(), place));
            }
            if (byId.size() >= Math.max(targetSize, 10)) {
                return new ArrayList<>(byId.values());
            }
        }

        if (!normalizedInterests.isEmpty()) {
            itineraryRepository.findPlaces(city, normalizedInterests, Math.max(targetSize + 16, 24))
                    .stream()
                    .filter(p -> isRelevantForAnyInterest(p, normalizedInterests, city))
                    .filter(p -> !isGeneratedTemplateName(p.name()))
                    .filter(p -> !isLowQualityTourName(p.name()))
                    .forEach(p -> byId.putIfAbsent(p.id(), p));

            if (byId.size() < targetSize) {
                if (enableExternalEnrichment) {
                    fetchAndPersistExternal(city, normalizedInterests, Math.min(targetSize - byId.size() + 4, 6), false);
                }
                itineraryRepository.findPlaces(city, normalizedInterests, Math.max(targetSize + 24, 32))
                        .stream()
                        .filter(p -> isRelevantForAnyInterest(p, normalizedInterests, city))
                        .filter(p -> !isGeneratedTemplateName(p.name()))
                        .filter(p -> !isLowQualityTourName(p.name()))
                        .forEach(p -> byId.putIfAbsent(p.id(), p));
            }

            if (enableExternalEnrichment && byId.size() < Math.min(targetSize, 4)) {
                fetchAndPersistExternal(city, normalizedInterests, Math.max(targetSize - byId.size() + 6, 10), true);
                itineraryRepository.findPlaces(city, normalizedInterests, Math.max(targetSize + 40, 60))
                        .stream()
                        .filter(p -> isRelevantForAnyInterest(p, normalizedInterests, city))
                        .filter(p -> !isGeneratedTemplateName(p.name()))
                        .filter(p -> !isLowQualityTourName(p.name()))
                        .forEach(p -> byId.putIfAbsent(p.id(), p));
            }

            if (byId.size() < targetSize) {
                itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 24, 36))
                        .stream()
                        .filter(p -> isThemeCategoryMatchForAnyInterest(p, normalizedInterests, city))
                        .filter(p -> !isGeneratedTemplateName(p.name()))
                        .filter(p -> !isLowQualityTourName(p.name()))
                        .forEach(p -> byId.putIfAbsent(p.id(), p));
            }
            if (byId.isEmpty()) {
                itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 24, 36))
                        .stream()
                        .filter(p -> !isGenericPlaceName(p.name()))
                        .filter(p -> !isGeneratedTemplateName(p.name()))
                        .filter(p -> !isLowQualityTourName(p.name()))
                        .forEach(p -> byId.putIfAbsent(p.id(), p));
            }
            return new ArrayList<>(byId.values());
        }

        itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 12, 20)).stream()
                .filter(p -> !isGenericPlaceName(p.name()))
                .filter(p -> !isGeneratedTemplateName(p.name()))
                .filter(p -> !isLowQualityTourName(p.name()))
                .forEach(p -> byId.putIfAbsent(p.id(), p));
        if (byId.size() < targetSize || byId.size() < 10) {
            if (enableExternalEnrichment) {
                fetchAndPersistExternal(city, List.of(), targetSize - byId.size() + 20, byId.size() < 6)
                        .forEach(place -> byId.putIfAbsent(place.id(), place));
            }
            itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 28, 36)).stream()
                    .filter(p -> !isGenericPlaceName(p.name()))
                    .filter(p -> !isGeneratedTemplateName(p.name()))
                    .filter(p -> !isLowQualityTourName(p.name()))
                    .forEach(p -> byId.putIfAbsent(p.id(), p));
        }
        return new ArrayList<>(byId.values());
    }

    private List<PlaceDto> expandCityPoolFromLive(String city, List<PlaceDto> existingPool, int neededMore) {
        Map<Long, PlaceDto> byId = new LinkedHashMap<>();
        existingPool.forEach(place -> byId.putIfAbsent(place.id(), place));
        if (!enableExternalEnrichment) {
            return new ArrayList<>(byId.values());
        }

        int need = Math.max(neededMore, 10);
        boolean aggressive = need > 12;
        fetchAndPersistExternal(city, List.of(), need, aggressive)
                .forEach(place -> byId.putIfAbsent(place.id(), place));
        itineraryRepository.findPlacesByCityWide(city, Math.max(existingPool.size() + need + 20, 50)).stream()
                .filter(p -> !isGenericPlaceName(p.name()))
                .filter(p -> !isGeneratedTemplateName(p.name()))
                .filter(p -> !isLikelyNonAttractionName(p.name()))
                .filter(p -> !isLowQualityTourName(p.name()))
                .forEach(p -> byId.putIfAbsent(p.id(), p));
        return new ArrayList<>(byId.values());
    }

    private List<PlaceDto> fetchAndPersistExternal(String city, List<String> interests, int needed, boolean aggressive) {
        Map<Long, PlaceDto> discovered = new LinkedHashMap<>();
        Set<String> queries = new LinkedHashSet<>(buildDynamicQueries(interests));
        if (aggressive) {
            queries.add("tourist attraction");
            queries.add("top places to visit");
            queries.add("landmark");
            queries.add("famous places");
            queries.add("historical place");
        }
        int maxQueries = aggressive
                ? (interests == null || interests.isEmpty() ? 7 : Math.min(9, Math.max(5, interests.size() * 2)))
                : (interests == null || interests.isEmpty() ? 4 : Math.min(6, Math.max(3, interests.size() + 1)));
        int resultLimit = aggressive ? 9 : 6;
        long budgetNanos = TimeUnit.MILLISECONDS.toNanos(aggressive ? 5000 : 2800);
        long deadline = System.nanoTime() + budgetNanos;

        int added = 0;
        Set<String> seenNames = new HashSet<>();
        int queryCount = 0;
        for (String q : queries) {
            if (System.nanoTime() > deadline) {
                break;
            }
            if (queryCount >= maxQueries) {
                break;
            }
            queryCount++;
            if (added >= needed) {
                break;
            }
            try {
                List<ExternalPlaceDto> ext = openMapService.searchPlace(q, city, resultLimit);
                for (ExternalPlaceDto e : ext) {
                    if (added >= needed) {
                        break;
                    }
                    String cleanName = cleanName(e.displayName());
                    if (cleanName.length() < 3
                            || isGenericPlaceName(cleanName)
                            || isGeneratedTemplateName(cleanName)
                            || isLowQualityTourName(cleanName)
                            || !isRelevantExternalPlace(e, cleanName, q, city)
                            || !seenNames.add(cleanName.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    PlaceDto persisted = itineraryRepository.upsertPlace(
                            cleanName,
                            city,
                            "India",
                            normalizeCategory(e.type(), q),
                            e.latitude(),
                            e.longitude(),
                            estimateCostByCategory(q, e.type()),
                            90,
                            4.2,
                            "Discovered from map index for " + city + "."
                    );
                    discovered.putIfAbsent(persisted.id(), persisted);
                    added++;
                }
            } catch (Exception ignored) {
                // Map APIs can rate-limit or fail; the planner still proceeds with local DB places.
            }
        }
        return new ArrayList<>(discovered.values());
    }

    private Set<String> buildDynamicQueries(List<String> interests) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add("tourist attraction");
        queries.add("things to do");
        queries.add("landmark");
        queries.add("must visit places");
        queries.add("historical places");
        queries.add("temples");
        queries.add("nature spots");
        queries.add("local food");
        queries.add("points of interest");
        queries.add("sightseeing");
        queries.add("city attractions");
        queries.add("culture");
        queries.add("famous places");
        queries.add("weekend spots");

        if (interests != null) {
            for (String interest : interests) {
                if (interest == null || interest.isBlank()) {
                    continue;
                }
                String normalized = canonicalInterest(interest);
                queries.add(normalized);
                queries.add(normalized + " places");
                queries.add(normalized + " attractions");
                if ("temple".equals(normalized)) {
                    queries.add("hindu temple");
                    queries.add("famous temple");
                    queries.add("ancient temple");
                    queries.add("place of worship");
                } else if ("heritage".equals(normalized)) {
                    queries.add("heritage site");
                    queries.add("historical monument");
                    queries.add("museum");
                    queries.add("fort");
                    queries.add("palace");
                }
            }
        }
        queries.add("popular places");
        return queries;
    }

    private List<String> normalizeInterests(List<String> interests) {
        if (interests == null) {
            return List.of();
        }
        return interests.stream()
                .filter(i -> i != null && !i.isBlank())
                .map(this::canonicalInterest)
                .distinct()
                .toList();
    }

    private List<String> deriveInterestsFromPool(List<PlaceDto> pool) {
        if (pool == null || pool.isEmpty()) {
            return List.of("heritage", "temple", "nature", "food");
        }

        Map<String, Integer> byTheme = new LinkedHashMap<>();
        for (PlaceDto place : pool) {
            if (place == null || isGenericPlaceName(place.name()) || isLikelyNonAttractionName(place.name())) {
                continue;
            }
            String category = canonicalInterest(place.category());
            String theme = switch (category) {
                case "temple", "heritage", "food", "nature" -> category;
                case "beach" -> "nature";
                default -> "";
            };
            if (!theme.isBlank()) {
                byTheme.merge(theme, 1, Integer::sum);
            }
        }

        List<String> ordered = byTheme.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (ordered.isEmpty()) {
            return List.of("heritage", "temple", "nature", "food");
        }
        return ordered;
    }

    private List<PlaceDto> selectPlacesForTheme(
            List<PlaceDto> pool,
            String theme,
            int count,
            int startCursor,
            Set<Long> blockedIds,
            Set<String> blockedNameKeys
    ) {
        if (theme == null || theme.isBlank()) {
            return selectSequentialPlaces(pool, count, startCursor, blockedIds, blockedNameKeys);
        }
        if ("tour".equals(canonicalInterest(theme))) {
            return selectTourPlacesForDay(pool, count, startCursor, blockedIds, blockedNameKeys);
        }

        List<PlaceDto> themedPool = pool.stream()
                .filter(place -> isPlaceSuitableForTheme(place, theme))
                .sorted((a, b) -> Integer.compare(scorePlaceForTheme(b, theme), scorePlaceForTheme(a, theme)))
                .toList();
        List<PlaceDto> nonSyntheticThemedPool = themedPool.stream()
                .filter(place -> !isSyntheticPlace(place))
                .toList();
        if (!nonSyntheticThemedPool.isEmpty()) {
            themedPool = nonSyntheticThemedPool;
        }
        if (themedPool.isEmpty()) {
            themedPool = pool.stream()
                    .filter(place -> isThemeMatch(place.category(), theme) && !isLikelyNonAttractionName(place.name()))
                    .sorted((a, b) -> Integer.compare(scorePlaceForTheme(b, theme), scorePlaceForTheme(a, theme)))
                    .toList();
        }

        List<PlaceDto> selected = selectSequentialPlaces(themedPool, count, startCursor, blockedIds, blockedNameKeys);
        if (selected.size() < count && !themedPool.isEmpty()) {
            selected = topUpFromSameTheme(selected, themedPool, count, blockedIds, blockedNameKeys);
        }
        if (selected.size() < count) {
            selected = topUpFromGeneralPool(selected, pool, theme, count, blockedIds, blockedNameKeys);
        }
        return selected;
    }

    private List<PlaceDto> selectTourPlacesForDay(
            List<PlaceDto> pool,
            int count,
            int startCursor,
            Set<Long> blockedIds,
            Set<String> blockedNameKeys
    ) {
        List<PlaceDto> candidates = pool.stream()
                .filter(place -> !isLikelyNonAttractionName(place.name()))
                .filter(place -> !isSyntheticPlace(place))
                .filter(place -> !isLowQualityTourName(place.name()))
                .filter(place -> !blockedIds.contains(place.id()))
                .filter(place -> !blockedNameKeys.contains(normalizeNameKey(place.name())))
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<PlaceDto> ranked = candidates.stream()
                .sorted((a, b) -> Double.compare(b.rating(), a.rating()))
                .toList();
        PlaceDto anchor = ranked.get(Math.floorMod(startCursor, ranked.size()));

        List<PlaceDto> byDistance = candidates.stream()
                .sorted((a, b) -> {
                    double distanceA = haversineKm(new GeoPoint(anchor.latitude(), anchor.longitude()), new GeoPoint(a.latitude(), a.longitude()));
                    double distanceB = haversineKm(new GeoPoint(anchor.latitude(), anchor.longitude()), new GeoPoint(b.latitude(), b.longitude()));
                    int cmp = Double.compare(distanceA, distanceB);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Double.compare(b.rating(), a.rating());
                })
                .toList();

        List<PlaceDto> selected = selectSequentialPlaces(byDistance, count, 0, Set.of(), Set.of());
        if (selected.size() < count) {
            List<PlaceDto> relaxedRanked = pool.stream()
                    .filter(place -> !isLikelyNonAttractionName(place.name()))
                    .filter(place -> !isSyntheticPlace(place))
                    .sorted((a, b) -> Double.compare(b.rating(), a.rating()))
                    .toList();
            selected = topUpFromGeneralPool(selected, relaxedRanked, "tour", count, Set.of(), Set.of());
        }
        if (selected.size() < count) {
            selected = topUpDayWithNearbyReuse(selected, pool, count, startCursor + 1);
        }
        return orderByNearestNeighbor(selected);
    }

    private boolean isPlaceSuitableForTheme(PlaceDto place, String theme) {
        String normalizedTheme = canonicalInterest(theme);
        String normalizedCategory = canonicalInterest(place.category());
        String normalizedName = normalizeNameKey(place.name());

        if (isLikelyNonAttractionName(normalizedName)) {
            return false;
        }

        return switch (normalizedTheme) {
            case "temple" -> normalizedCategory.equals("temple")
                    || isNameConsistentWithInterest(place.name(), "temple");
            case "heritage" -> {
                boolean looksHeritage = normalizedCategory.equals("heritage")
                        || isNameConsistentWithInterest(place.name(), "heritage");
                boolean conflictingCategory = Set.of("food", "nature", "beach", "temple").contains(normalizedCategory);
                yield looksHeritage && !conflictingCategory && !isLowQualityHeritageName(normalizedName);
            }
            case "food" -> normalizedCategory.equals("food")
                    || isNameConsistentWithInterest(place.name(), "food");
            case "nature" -> normalizedCategory.equals("nature")
                    || normalizedCategory.equals("beach")
                    || isNameConsistentWithInterest(place.name(), "nature");
            default -> isStrongThemeCandidate(place, theme);
        };
    }

    private int scorePlaceForTheme(PlaceDto place, String theme) {
        String normalizedTheme = canonicalInterest(theme);
        String normalizedCategory = canonicalInterest(place.category());
        String normalizedName = normalizeNameKey(place.name());

        int score = 0;
        if (isSyntheticPlace(place)) {
            score -= 30;
        }
        if (normalizedCategory.equals(normalizedTheme)) {
            score += 10;
        }
        if (isNameConsistentWithInterest(place.name(), normalizedTheme)) {
            score += 8;
        }
        score += Math.max(0, (int) Math.round(place.rating() * 2));

        if (normalizedTheme.equals("heritage")) {
            if (containsAny(normalizedName, "fort", "museum", "monument", "palace", "memorial", "archaeological")) {
                score += 6;
            }
            if (isLowQualityHeritageName(normalizedName)) {
                score -= 15;
            }
        }
        if (normalizedTheme.equals("temple") && containsAny(normalizedName, "temple", "mandir", "kovil", "amman", "swamy", "perumal")) {
            score += 5;
        }
        return score;
    }

    private List<PlaceDto> topUpFromSameTheme(
            List<PlaceDto> current,
            List<PlaceDto> themePool,
            int targetCount,
            Set<Long> blockedIds,
            Set<String> blockedNameKeys
    ) {
        Map<Long, PlaceDto> byId = new LinkedHashMap<>();
        Set<String> seenNames = new LinkedHashSet<>();
        current.forEach(place -> {
            byId.putIfAbsent(place.id(), place);
            seenNames.add(normalizeNameKey(place.name()));
        });

        for (PlaceDto place : themePool) {
            if (byId.size() >= targetCount) {
                break;
            }
            if (blockedIds.contains(place.id())) {
                continue;
            }
            String nameKey = normalizeNameKey(place.name());
            if (blockedNameKeys.contains(nameKey)) {
                continue;
            }
            if (seenNames.add(nameKey)) {
                byId.putIfAbsent(place.id(), place);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<PlaceDto> topUpFromGeneralPool(
            List<PlaceDto> current,
            List<PlaceDto> pool,
            String theme,
            int targetCount,
            Set<Long> blockedIds,
            Set<String> blockedNameKeys
    ) {
        Map<Long, PlaceDto> byId = new LinkedHashMap<>();
        Set<String> seenNames = new LinkedHashSet<>();
        current.forEach(place -> {
            byId.putIfAbsent(place.id(), place);
            seenNames.add(normalizeNameKey(place.name()));
        });

        List<PlaceDto> ranked = pool.stream()
                .filter(place -> !isLikelyNonAttractionName(place.name()))
                .filter(place -> !isSyntheticPlace(place))
                .filter(place -> !isLowQualityTourName(place.name()))
                .sorted((a, b) -> {
                    int themeScoreA = isPlaceSuitableForTheme(a, theme) ? 1 : 0;
                    int themeScoreB = isPlaceSuitableForTheme(b, theme) ? 1 : 0;
                    if (themeScoreA != themeScoreB) {
                        return Integer.compare(themeScoreB, themeScoreA);
                    }
                    return Double.compare(b.rating(), a.rating());
                })
                .toList();

        for (PlaceDto place : ranked) {
            if (byId.size() >= targetCount) {
                break;
            }
            if (blockedIds.contains(place.id())) {
                continue;
            }
            String nameKey = normalizeNameKey(place.name());
            if (blockedNameKeys.contains(nameKey)) {
                continue;
            }
            if (seenNames.add(nameKey)) {
                byId.putIfAbsent(place.id(), place);
            }
        }

        return new ArrayList<>(byId.values());
    }

    private List<PlaceDto> orderByNearestNeighbor(List<PlaceDto> places) {
        if (places == null || places.size() < 3) {
            return places == null ? List.of() : places;
        }
        List<PlaceDto> remaining = new ArrayList<>(places);
        remaining.sort((a, b) -> Double.compare(b.rating(), a.rating()));

        List<PlaceDto> ordered = new ArrayList<>();
        PlaceDto current = remaining.removeFirst();
        ordered.add(current);

        while (!remaining.isEmpty()) {
            PlaceDto next = null;
            double bestDistance = Double.MAX_VALUE;
            for (PlaceDto candidate : remaining) {
                double distance = haversineKm(
                        new GeoPoint(current.latitude(), current.longitude()),
                        new GeoPoint(candidate.latitude(), candidate.longitude()));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    next = candidate;
                }
            }
            ordered.add(next);
            remaining.remove(next);
            current = next;
        }
        return ordered;
    }

    private List<PlaceDto> topUpDayWithNearbyReuse(
            List<PlaceDto> current,
            List<PlaceDto> pool,
            int minimumCount,
            int startCursor
    ) {
        if (minimumCount <= 0) {
            return current == null ? List.of() : current;
        }
        Map<Long, PlaceDto> byId = new LinkedHashMap<>();
        Set<String> seenNames = new LinkedHashSet<>();
        if (current != null) {
            current.forEach(place -> {
                byId.putIfAbsent(place.id(), place);
                seenNames.add(normalizeNameKey(place.name()));
            });
        }

        List<PlaceDto> ranked = pool.stream()
                .filter(place -> !isLikelyNonAttractionName(place.name()))
                .filter(place -> !isSyntheticPlace(place))
                .filter(place -> !isLowQualityTourName(place.name()))
                .sorted((a, b) -> Double.compare(b.rating(), a.rating()))
                .toList();
        if (ranked.isEmpty()) {
            return new ArrayList<>(byId.values());
        }

        PlaceDto anchor = ranked.get(Math.floorMod(startCursor, ranked.size()));
        List<PlaceDto> nearby = ranked.stream()
                .sorted((a, b) -> {
                    double distanceA = haversineKm(
                            new GeoPoint(anchor.latitude(), anchor.longitude()),
                            new GeoPoint(a.latitude(), a.longitude()));
                    double distanceB = haversineKm(
                            new GeoPoint(anchor.latitude(), anchor.longitude()),
                            new GeoPoint(b.latitude(), b.longitude()));
                    int cmp = Double.compare(distanceA, distanceB);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return Double.compare(b.rating(), a.rating());
                })
                .toList();

        for (PlaceDto place : nearby) {
            if (byId.size() >= minimumCount) {
                break;
            }
            String nameKey = normalizeNameKey(place.name());
            if (seenNames.add(nameKey)) {
                byId.putIfAbsent(place.id(), place);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private boolean isThemeMatch(String category, String theme) {
        String normalizedCategory = canonicalInterest(category);
        String normalizedTheme = canonicalInterest(theme);
        return normalizedCategory.equals(normalizedTheme)
                || normalizedCategory.contains(normalizedTheme)
                || normalizedTheme.contains(normalizedCategory);
    }

    private List<PlaceDto> selectSequentialPlaces(List<PlaceDto> pool, int count, int startCursor, Set<Long> blockedIds, Set<String> blockedNameKeys) {
        if (pool.isEmpty()) {
            return List.of();
        }
        List<PlaceDto> selected = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        Set<String> seenNames = new LinkedHashSet<>();
        int start = Math.max(0, startCursor);
        for (int i = 0; i < pool.size() * 2 && selected.size() < count; i++) {
            PlaceDto candidate = pool.get((start + i) % pool.size());
            if (blockedIds.contains(candidate.id())) {
                continue;
            }
            String nameKey = normalizeNameKey(candidate.name());
            if (blockedNameKeys.contains(nameKey)) {
                continue;
            }
            if (seen.add(candidate.id()) && seenNames.add(nameKey)) {
                selected.add(candidate);
            }
        }
        if (selected.size() < count) {
            for (PlaceDto place : pool) {
                if (selected.size() >= count) {
                    break;
                }
                if (blockedIds.contains(place.id())) {
                    continue;
                }
                String nameKey = normalizeNameKey(place.name());
                if (blockedNameKeys.contains(nameKey)) {
                    continue;
                }
                if (seen.add(place.id()) && seenNames.add(nameKey)) {
                    selected.add(place);
                }
            }
        }
        return selected;
    }

    private List<PlaceDto> selectFallbackPlaces(
            List<PlaceDto> pool,
            int count,
            int startCursor,
            Set<Long> blockedIds,
            Set<String> blockedNameKeys
    ) {
        List<PlaceDto> safePool = pool.stream()
                .filter(place -> !isLikelyNonAttractionName(place.name()))
                .filter(place -> !isSyntheticPlace(place))
                .filter(place -> !isLowQualityTourName(place.name()))
                .toList();
        List<PlaceDto> selected = selectSequentialPlaces(safePool, count, startCursor, blockedIds, blockedNameKeys);
        if (selected.size() >= count) {
            return selected;
        }
        selected = topUpFromGeneralPool(selected, safePool, "", count, blockedIds, blockedNameKeys);
        if (selected.size() >= count) {
            return selected;
        }
        selected = topUpFromGeneralPool(selected, pool, "", count, blockedIds, blockedNameKeys);
        if (selected.size() >= count) {
            return selected;
        }
        return selected;
    }

    private String normalizeCategory(String type, String query) {
        String base = (type == null ? "" : type.toLowerCase(Locale.ROOT));
        if (base.contains("museum")
                || base.contains("monument")
                || base.contains("fort")
                || base.contains("palace")
                || base.contains("castle")
                || base.contains("memorial")
                || base.contains("ruins")
                || base.contains("archaeological")) {
            return "heritage";
        }
        if (base.contains("temple") || base.contains("mosque") || base.contains("church")) {
            return "temple";
        }
        if (base.contains("beach")) {
            return "beach";
        }
        if (base.contains("park") || base.contains("garden") || base.contains("waterfall")) {
            return "nature";
        }
        if (query != null && !query.isBlank()) {
            return canonicalInterest(query);
        }
        return "city";
    }

    private int estimateCostByCategory(String query, String type) {
        String cat = normalizeCategory(type, query);
        return switch (cat) {
            case "beach", "nature" -> 600;
            case "heritage", "temple" -> 400;
            default -> 500;
        };
    }

    private String cleanName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        List<String> parts = Arrays.stream(displayName.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (parts.isEmpty()) {
            return "";
        }
        String first = parts.get(0);
        if (!isGenericPlaceName(first)) {
            return first;
        }
        for (String part : parts) {
            if (!isGenericPlaceName(part) && part.length() >= 3) {
                return part;
            }
        }
        return "";
    }

    private boolean isGenericPlaceName(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Set<String> exactGeneric = Set.of(
                "temple", "temples", "museum", "museums", "park", "parks", "fort", "forts",
                "lake", "lakes", "beach", "beaches", "market", "markets", "church", "mosque",
                "heritage", "food", "food mall", "pond"
        );
        if (exactGeneric.contains(normalized)) {
            return true;
        }
        if (normalized.startsWith("temple (")
                || normalized.startsWith("temples (")
                || normalized.startsWith("museum (")
                || normalized.startsWith("heritage (")) {
            return true;
        }
        if (normalized.matches("^(temple|temples|museum|heritage) (west|east|north|south|road|street|junction|district|city|town|village).*$")) {
            return true;
        }
        if (normalized.matches("^(nh|sh)\\s*\\d+[a-z]?$")) {
            return true;
        }
        if (normalized.matches("^(road|street|lane|cross|junction|ward|sector)\\s*\\d+[a-z]?$")) {
            return true;
        }
        return normalized.endsWith(" road")
                || normalized.endsWith(" district")
                || normalized.endsWith(" ward")
                || normalized.endsWith(" nagar")
                || normalized.endsWith(" marg");
    }

    private boolean isRelevantExternalPlace(ExternalPlaceDto place, String cleanName, String query, String city) {
        String interest = canonicalInterest(query);
        String type = place.type() == null ? "" : place.type().toLowerCase(Locale.ROOT);
        String name = cleanName.toLowerCase(Locale.ROOT);
        String cityKey = normalizeNameKey(city);
        String nameKey = normalizeNameKey(cleanName);

        Set<String> blockedTypes = Set.of(
                "city", "town", "village", "suburb", "neighbourhood", "neighborhood",
                "administrative", "county", "state", "district", "residential", "locality", "road"
        );
        if (blockedTypes.contains(type)) {
            return false;
        }
        if (nameKey.equals(cityKey)) {
            return false;
        }

        return switch (interest) {
            case "temple" -> containsAny(name, "temple", "mandir", "mosque", "church", "dargah", "shrine", "cathedral")
                    || containsAny(type, "temple", "place_of_worship", "mosque", "church");
            case "heritage" -> (containsAny(name, "fort", "palace", "museum", "monument", "memorial", "charminar", "citadel")
                    || containsAny(type, "museum", "monument", "fort", "castle", "palace", "memorial", "ruins", "archaeological_site"))
                    && !containsAny(name, "hotel", "resort", "apartment", "bungalow", "villa", "lodge", "mall");
            case "food" -> containsAny(name, "restaurant", "cafe", "eatery", "dhaba", "mess", "food")
                    || containsAny(type, "restaurant", "cafe", "food_court");
            case "nature" -> containsAny(name, "park", "garden", "lake", "beach", "waterfall", "sanctuary", "hill")
                    || containsAny(type, "park", "garden", "beach", "waterfall", "nature_reserve");
            default -> true;
        };
    }

    private boolean isRelevantForAnyInterest(PlaceDto place, List<String> interests, String city) {
        if (isGenericPlaceName(place.name())) {
            return false;
        }
        if (normalizeNameKey(place.name()).equals(normalizeNameKey(city))) {
            return false;
        }
        for (String interest : interests) {
            if (isThemeMatch(place.category(), interest)) {
                return true;
            }
        }
        return false;
    }

    private boolean isThemeCategoryMatchForAnyInterest(PlaceDto place, List<String> interests, String city) {
        if (isGenericPlaceName(place.name())) {
            return false;
        }
        if (normalizeNameKey(place.name()).equals(normalizeNameKey(city))) {
            return false;
        }
        for (String interest : interests) {
            if (isThemeMatch(place.category(), interest)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNameConsistentWithInterest(String name, String interest) {
        String normalizedName = normalizeNameKey(name);
        String normalizedInterest = canonicalInterest(interest);
        return switch (normalizedInterest) {
            case "temple" -> containsAny(normalizedName, "temple", "mandir", "mosque", "church", "dargah", "shrine", "cathedral",
                    "devi", "amman", "mahadev", "swamy", "swami", "kovil", "bhagavathy", "perumal");
            case "heritage" -> (containsAny(normalizedName, "fort", "palace", "museum", "monument", "memorial", "charminar", "citadel")
                    && !containsAny(normalizedName, "hotel", "resort", "apartment", "bungalow", "villa", "lodge", "mall"));
            case "food" -> containsAny(normalizedName, "restaurant", "cafe", "food", "eatery", "dhaba", "mess", "kitchen");
            case "nature" -> containsAny(normalizedName, "park", "garden", "lake", "beach", "waterfall", "sanctuary", "hill", "forest");
            default -> true;
        };
    }

    private boolean isStrongThemeCandidate(PlaceDto place, String theme) {
        String normalizedTheme = canonicalInterest(theme);
        String name = normalizeNameKey(place.name());
        if (isLikelyNonAttractionName(name)) {
            return false;
        }
        boolean categoryMatches = isThemeMatch(place.category(), normalizedTheme);
        boolean nameMatches = isNameConsistentWithInterest(place.name(), normalizedTheme);
        return switch (normalizedTheme) {
            case "temple" -> nameMatches && (categoryMatches || containsAny(name, "temple", "mandir", "kovil", "shrine"));
            case "heritage" -> (categoryMatches || nameMatches) && !containsAny(name, "hotel", "restaurant", "street", "road", "market");
            case "food" -> nameMatches && (categoryMatches || containsAny(name, "restaurant", "cafe", "eatery", "dhaba", "mess"));
            case "nature" -> categoryMatches || nameMatches;
            default -> categoryMatches || nameMatches;
        };
    }

    private List<PlaceDto> ensureThemePool(String city, List<PlaceDto> existingPool, String theme, int desiredCount, boolean allowExternal) {
        if (theme == null || theme.isBlank()) {
            return existingPool;
        }
        long matching = existingPool.stream().filter(place -> isPlaceSuitableForTheme(place, theme)).count();
        if (matching >= desiredCount) {
            return existingPool;
        }

        if (allowExternal && enableExternalEnrichment) {
            fetchAndPersistExternal(city, List.of(theme), Math.max(desiredCount, 8), false);
        }
        Map<Long, PlaceDto> byId = new LinkedHashMap<>();
        existingPool.forEach(place -> byId.putIfAbsent(place.id(), place));
        itineraryRepository.findPlaces(city, List.of(canonicalInterest(theme)), Math.max(desiredCount * 5, 30))
                .stream()
                .filter(place -> isPlaceSuitableForTheme(place, theme))
                .forEach(place -> byId.putIfAbsent(place.id(), place));
        itineraryRepository.findPlacesByCityWide(city, Math.max(desiredCount * 6, 40))
                .stream()
                .filter(place -> isPlaceSuitableForTheme(place, theme))
                .forEach(place -> byId.putIfAbsent(place.id(), place));

        return new ArrayList<>(byId.values());
    }

    private boolean containsAny(String text, String... words) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String word : words) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelyNonAttractionName(String name) {
        String normalized = normalizeNameKey(name);
        return containsAny(normalized,
                "police station", "railway station", "bus stand", "bus station", "post office",
                "street", "main street", "road", "junction", "cross", "market road",
                "hospital", "clinic", "school", "college", "office", "department",
                "nagar", "ward", "district office", "collectorate",
                "entry ticket", "entry tickets", "ticket counter", "ticket office",
                "booking office", "service road", "mobile palace")
                || normalized.matches("^(nh|sh)\\s*\\d+[a-z]?$")
                || normalized.matches("^[a-z]{0,3}\\d{2,}$");
    }

    private boolean isLowQualityTourName(String name) {
        String normalized = normalizeNameKey(name);
        if (normalized.isBlank()) {
            return true;
        }
        return containsAny(normalized,
                "best local", "cheap local", "very local vibe", "local vibe", "lots of",
                "view point", "viewpoint", "city view", "palace view", "tourist point",
                "main market", "local market", "food point", "food street",
                "marg", "cross road", "service lane")
                || normalized.matches(".*\\b(local|best|cheap)\\b.*\\b(food|cuisine|vibe|spot|point|place)\\b.*")
                || normalized.matches("^(best|cheap|local)\\b.*")
                || normalized.matches(".*\\b(point|view)\\s*\\d+\\b.*");
    }

    private boolean isGeneratedTemplateName(String name) {
        String normalized = normalizeNameKey(name);
        return containsAny(normalized,
                "local cuisine trail",
                "heritage landmark",
                "sacred temple circuit",
                "nature viewpoint");
    }

    private boolean isLowQualityHeritageName(String normalizedName) {
        return containsAny(normalizedName,
                "dream house", "new mobile", "residency", "apartment", "villa", "bungalow")
                || normalizedName.matches(".*\\b(19|20)\\d{2}\\b.*");
    }

    private boolean isSyntheticPlace(PlaceDto place) {
        if (place == null || place.description() == null) {
            return false;
        }
        return place.description().startsWith("Curated fallback place generated for ");
    }

    private String normalizeNameKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String canonicalInterest(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.contains("temple")) {
            return "temple";
        }
        if (normalized.contains("heritage") || normalized.contains("history") || normalized.contains("historical")) {
            return "heritage";
        }
        if (normalized.contains("food") || normalized.contains("cuisine")) {
            return "food";
        }
        if (normalized.contains("nature") || normalized.contains("park") || normalized.contains("garden") || normalized.contains("wildlife")) {
            return "nature";
        }
        if (normalized.contains("beach") || normalized.contains("coast")) {
            return "beach";
        }
        return normalized;
    }

    private static String pickTheme(List<String> interests, int dayNumber) {
        if (interests == null || interests.isEmpty()) {
            return "culture";
        }
        return interests.get((dayNumber - 1) % interests.size());
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "cab";
        }
        return mode.toLowerCase(Locale.ROOT);
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be after or equal to startDate");
        }
    }

    private record GeoPoint(double latitude, double longitude) {
    }
}
