package com.indiatravel.itinerary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indiatravel.itinerary.dto.ExternalPlaceDto;
import com.indiatravel.itinerary.dto.RouteEstimateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OpenMapService {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String nominatimUrl;
    private final String osrmUrl;
    private final String geoapifyUrl;
    private final String geoapifyApiKey;

    public OpenMapService(
            ObjectMapper objectMapper,
            @Value("${maps.nominatim-url:https://nominatim.openstreetmap.org}") String nominatimUrl,
            @Value("${maps.osrm-url:https://router.project-osrm.org}") String osrmUrl,
            @Value("${maps.geoapify-url:https://api.geoapify.com}") String geoapifyUrl,
            @Value("${maps.geoapify-api-key:}") String geoapifyApiKey
    ) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.objectMapper = objectMapper;
        this.nominatimUrl = nominatimUrl;
        this.osrmUrl = osrmUrl;
        this.geoapifyUrl = geoapifyUrl;
        this.geoapifyApiKey = geoapifyApiKey == null ? "" : geoapifyApiKey.trim();
    }

    public List<ExternalPlaceDto> searchPlace(String query, String city, int limit) {
        if (!geoapifyApiKey.isBlank()) {
            try {
                List<ExternalPlaceDto> geoapify = searchWithGeoapify(query, city, limit);
                if (!geoapify.isEmpty()) {
                    return geoapify;
                }
            } catch (Exception ignored) {
                // Fallback to Nominatim when Geoapify is unavailable.
            }
        }

        String text = (query + ", " + city + ", India").trim();
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = nominatimUrl + "/search?q=" + encoded + "&format=json&limit=" + limit;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "india-itinerary-planner/1.0")
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode arr = objectMapper.readTree(response.body());
            List<ExternalPlaceDto> out = new ArrayList<>();
            for (JsonNode node : arr) {
                out.add(new ExternalPlaceDto(
                        node.path("display_name").asText(),
                        node.path("lat").asDouble(),
                        node.path("lon").asDouble(),
                        node.path("type").asText()
                ));
            }
            return out;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Map search failed", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Map search failed", ex);
        }
    }

    private List<ExternalPlaceDto> searchWithGeoapify(String query, String city, int limit) throws IOException, InterruptedException {
        String cityCenterText = URLEncoder.encode(city + ", India", StandardCharsets.UTF_8);
        String cityUrl = geoapifyUrl + "/v1/geocode/search?text=" + cityCenterText + "&limit=1&apiKey=" + geoapifyApiKey;
        HttpRequest cityReq = HttpRequest.newBuilder(URI.create(cityUrl))
                .header("User-Agent", "india-itinerary-planner/1.0")
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        HttpResponse<String> cityRes = client.send(cityReq, HttpResponse.BodyHandlers.ofString());
        JsonNode cityRoot = objectMapper.readTree(cityRes.body());
        JsonNode cityFeature = cityRoot.path("features").path(0);
        if (cityFeature.isMissingNode()) {
            return List.of();
        }
        JsonNode cityCoords = cityFeature.path("geometry").path("coordinates");
        double lon = cityCoords.path(0).asDouble();
        double lat = cityCoords.path(1).asDouble();

        String categories = mapQueryToGeoapifyCategories(query);
        String placesUrl = geoapifyUrl + "/v2/places"
                + "?categories=" + URLEncoder.encode(categories, StandardCharsets.UTF_8)
                + "&filter=circle:" + lon + "," + lat + ",16000"
                + "&bias=proximity:" + lon + "," + lat
                + "&limit=" + Math.max(1, limit)
                + "&apiKey=" + geoapifyApiKey;
        HttpRequest placesReq = HttpRequest.newBuilder(URI.create(placesUrl))
                .header("User-Agent", "india-itinerary-planner/1.0")
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> placesRes = client.send(placesReq, HttpResponse.BodyHandlers.ofString());
        JsonNode placesRoot = objectMapper.readTree(placesRes.body());

        List<ExternalPlaceDto> out = new ArrayList<>();
        for (JsonNode feature : placesRoot.path("features")) {
            String name = feature.path("properties").path("name").asText("");
            if (name.isBlank()) {
                name = feature.path("properties").path("formatted").asText("");
            }
            JsonNode coords = feature.path("geometry").path("coordinates");
            double placeLon = coords.path(0).asDouble();
            double placeLat = coords.path(1).asDouble();
            String type = feature.path("properties").path("categories").isArray()
                    ? feature.path("properties").path("categories").path(0).asText("tourism.sights")
                    : feature.path("properties").path("category").asText("tourism.sights");
            if (!name.isBlank() && placeLat != 0 && placeLon != 0) {
                out.add(new ExternalPlaceDto(name, placeLat, placeLon, type));
            }
        }
        return out;
    }

    private String mapQueryToGeoapifyCategories(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (normalized.contains("temple") || normalized.contains("relig")) {
            return "religion";
        }
        if (normalized.contains("food") || normalized.contains("cuisine") || normalized.contains("restaurant")) {
            return "catering.restaurant,catering.cafe";
        }
        if (normalized.contains("nature") || normalized.contains("park") || normalized.contains("beach")) {
            return "natural,national_park,beach,leisure.park";
        }
        if (normalized.contains("heritage") || normalized.contains("museum") || normalized.contains("fort") || normalized.contains("palace")) {
            return "entertainment.museum,tourism.sights,tourism.attraction";
        }
        return "tourism.sights,tourism.attraction,entertainment,catering,leisure";
    }

    public RouteEstimateResponse estimateRoute(double fromLat, double fromLng, double toLat, double toLng) {
        String url = osrmUrl + "/route/v1/driving/" + fromLng + "," + fromLat + ";" + toLng + "," + toLat + "?overview=false";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "india-itinerary-planner/1.0")
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode route = root.path("routes").path(0);
            double distanceKm = route.path("distance").asDouble() / 1000.0;
            int durationMinutes = (int) Math.round(route.path("duration").asDouble() / 60.0);
            return new RouteEstimateResponse(Math.round(distanceKm * 10.0) / 10.0, durationMinutes);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Route estimation failed", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Route estimation failed", ex);
        }
    }
}
