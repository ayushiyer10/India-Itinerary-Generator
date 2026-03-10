package com.indiatravel.itinerary.controller;

import com.indiatravel.itinerary.dto.ExternalPlaceDto;
import com.indiatravel.itinerary.dto.RouteEstimateRequest;
import com.indiatravel.itinerary.dto.RouteEstimateResponse;
import com.indiatravel.itinerary.service.OpenMapService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/maps")
public class MapController {

    private final OpenMapService openMapService;

    public MapController(OpenMapService openMapService) {
        this.openMapService = openMapService;
    }

    @GetMapping("/search")
    public List<ExternalPlaceDto> search(
            @RequestParam String city,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return openMapService.searchPlace(query, city, limit);
    }

    @PostMapping("/route-estimate")
    public RouteEstimateResponse estimate(@Valid @RequestBody RouteEstimateRequest request) {
        return openMapService.estimateRoute(request.fromLat(), request.fromLng(), request.toLat(), request.toLng());
    }
}
