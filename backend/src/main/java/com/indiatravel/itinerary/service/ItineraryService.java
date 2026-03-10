package com.indiatravel.itinerary.service;

import com.indiatravel.itinerary.dto.GenerateTripRequest;
import com.indiatravel.itinerary.dto.ExternalPlaceDto;
import com.indiatravel.itinerary.dto.PlaceDto;
import com.indiatravel.itinerary.dto.RegenerateDayRequest;
import com.indiatravel.itinerary.dto.TripDetailDto;
import com.indiatravel.itinerary.repository.ItineraryRepository;
import com.indiatravel.itinerary.repository.UserRepository;
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
import java.util.stream.Collectors;

@Service
public class ItineraryService {

    private final ItineraryRepository itineraryRepository;
    private final UserRepository userRepository;
    private final OpenMapService openMapService;

    public ItineraryService(ItineraryRepository itineraryRepository, UserRepository userRepository, OpenMapService openMapService) {
        this.itineraryRepository = itineraryRepository;
        this.userRepository = userRepository;
        this.openMapService = openMapService;
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
        Map<String, Integer> cityCursor = new LinkedHashMap<>();
        for (String cityKey : cityDayCounts.keySet()) {
            int requiredUnique = cityDayCounts.get(cityKey) * placeCount + 2;
            cityPools.put(cityKey, buildCityPool(cityOriginalName.get(cityKey), request.interests(), requiredUnique));
            cityCursor.put(cityKey, 0);
        }

        for (int i = 0; i < totalDays; i++) {
            int dayNumber = i + 1;
            String city = dayCitySequence.get(i);
            String theme = pickTheme(request.interests(), dayNumber);
            String cityKey = normalizeCityKey(city);
            List<PlaceDto> pool = cityPools.getOrDefault(cityKey, List.of());
            int cursor = cityCursor.getOrDefault(cityKey, 0);
            List<PlaceDto> places = selectPlacesForTheme(pool, theme, placeCount, cursor, Set.of());
            cityCursor.put(cityKey, cursor + places.size());
            createDayPlan(tripId, dayNumber, city, theme, places, request.preferredTravelMode(), dailyBudget);
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

        List<PlaceDto> pool = buildCityPool(city, request.interests(), requiredUnique);
        String theme = pickTheme(request.interests(), dayNumber);
        List<PlaceDto> places = selectPlacesForTheme(pool, theme, placeCount, Math.max(0, dayNumber - 1) * placeCount, usedByOtherDays);
        int daysCount = Math.max(existing.days().size(), 1);
        int dailyBudget = existing.budgetInr() / daysCount;
        String mode = request.preferredTravelMode() == null || request.preferredTravelMode().isBlank()
                ? existing.preferredTravelMode() : request.preferredTravelMode();

        createDayPlan(tripId, dayNumber, city, theme, places, mode, dailyBudget);
    }

    private void createDayPlan(long tripId, int dayNumber, String city, String theme, List<PlaceDto> places, String travelMode, int dailyBudget) {
        int estimatedCost = places.stream().mapToInt(PlaceDto::averageCostInr).sum();
        int totalTravelMinutes = Math.max(30, (places.size() - 1) * 35);
        String notes = "Balanced for India city traffic and realistic local travel windows.";
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

        if (!normalizedInterests.isEmpty()) {
            itineraryRepository.findPlaces(city, normalizedInterests, Math.max(targetSize + 16, 24))
                    .stream()
                    .filter(p -> isRelevantForAnyInterest(p, normalizedInterests, city))
                    .forEach(p -> byId.putIfAbsent(p.id(), p));

            if (byId.size() < targetSize) {
                fetchAndPersistExternal(city, normalizedInterests, Math.min(targetSize - byId.size() + 10, 16));
                itineraryRepository.findPlaces(city, normalizedInterests, Math.max(targetSize + 24, 32))
                        .stream()
                        .filter(p -> isRelevantForAnyInterest(p, normalizedInterests, city))
                        .forEach(p -> byId.putIfAbsent(p.id(), p));
            }

            if (byId.size() < targetSize) {
                itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 24, 36))
                        .stream()
                        .filter(p -> isThemeCategoryMatchForAnyInterest(p, normalizedInterests, city))
                        .forEach(p -> byId.putIfAbsent(p.id(), p));
            }
            return new ArrayList<>(byId.values());
        }

        itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 8, 14)).stream()
                .filter(p -> !isGenericPlaceName(p.name()))
                .forEach(p -> byId.putIfAbsent(p.id(), p));
        if (byId.size() < targetSize || byId.size() < 8) {
            fetchAndPersistExternal(city, List.of(), targetSize - byId.size() + 20);
            itineraryRepository.findPlacesByCityWide(city, Math.max(targetSize + 20, 24)).stream()
                    .filter(p -> !isGenericPlaceName(p.name()))
                    .forEach(p -> byId.putIfAbsent(p.id(), p));
        }
        return new ArrayList<>(byId.values());
    }

    private void fetchAndPersistExternal(String city, List<String> interests, int needed) {
        Set<String> queries = buildDynamicQueries(interests);
        int maxQueries = interests == null || interests.isEmpty() ? 6 : 6;

        int added = 0;
        Set<String> seenNames = new HashSet<>();
        int queryCount = 0;
        for (String q : queries) {
            if (queryCount >= maxQueries) {
                break;
            }
            queryCount++;
            if (added >= needed) {
                break;
            }
            try {
                List<ExternalPlaceDto> ext = openMapService.searchPlace(q, city, 10);
                for (ExternalPlaceDto e : ext) {
                    if (added >= needed) {
                        break;
                    }
                    String cleanName = cleanName(e.displayName());
                    if (cleanName.length() < 3
                            || isGenericPlaceName(cleanName)
                            || !isRelevantExternalPlace(e, cleanName, q, city)
                            || !seenNames.add(cleanName.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    itineraryRepository.upsertPlace(
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
                    added++;
                }
            } catch (Exception ignored) {
                // Map APIs can rate-limit or fail; the planner still proceeds with local DB places.
            }
        }
    }

    private Set<String> buildDynamicQueries(List<String> interests) {
        Set<String> queries = new LinkedHashSet<>();
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
        if (queries.isEmpty()) {
            queries.add("tourist attraction");
            queries.add("popular places");
            queries.add("things to do");
        }
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

    private List<PlaceDto> selectPlacesForTheme(List<PlaceDto> pool, String theme, int count, int startCursor, Set<Long> blockedIds) {
        if (theme == null || theme.isBlank()) {
            return selectSequentialPlaces(pool, count, startCursor, blockedIds);
        }

        List<PlaceDto> themedPool = pool.stream()
                .filter(place -> isThemeMatch(place.category(), theme))
                .toList();

        List<PlaceDto> selected = selectSequentialPlaces(themedPool, count, startCursor, blockedIds);
        if (selected.size() >= count) {
            return selected;
        }
        return selected;
    }

    private boolean isThemeMatch(String category, String theme) {
        String normalizedCategory = canonicalInterest(category);
        String normalizedTheme = canonicalInterest(theme);
        return normalizedCategory.equals(normalizedTheme)
                || normalizedCategory.contains(normalizedTheme)
                || normalizedTheme.contains(normalizedCategory);
    }

    private List<PlaceDto> selectSequentialPlaces(List<PlaceDto> pool, int count, int startCursor, Set<Long> blockedIds) {
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
            if (seen.add(candidate.id()) && seenNames.add(nameKey)) {
                selected.add(candidate);
            }
        }
        if (selected.size() < count) {
            for (PlaceDto place : pool) {
                if (selected.size() >= count) {
                    break;
                }
                String nameKey = normalizeNameKey(place.name());
                if (seen.add(place.id()) && seenNames.add(nameKey)) {
                    selected.add(place);
                }
            }
        }
        return selected;
    }

    private String normalizeCategory(String type, String query) {
        String base = (type == null ? "" : type.toLowerCase(Locale.ROOT));
        if (base.contains("museum") || base.contains("monument") || base.contains("fort")) {
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
        return normalized.endsWith(" road")
                || normalized.endsWith(" district")
                || normalized.endsWith(" ward")
                || normalized.endsWith(" nagar");
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
