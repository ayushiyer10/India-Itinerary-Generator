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

@Service
public class OpenMapService {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String nominatimUrl;
    private final String osrmUrl;

    public OpenMapService(
            ObjectMapper objectMapper,
            @Value("${maps.nominatim-url:https://nominatim.openstreetmap.org}") String nominatimUrl,
            @Value("${maps.osrm-url:https://router.project-osrm.org}") String osrmUrl
    ) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.objectMapper = objectMapper;
        this.nominatimUrl = nominatimUrl;
        this.osrmUrl = osrmUrl;
    }

    public List<ExternalPlaceDto> searchPlace(String query, String city, int limit) {
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
