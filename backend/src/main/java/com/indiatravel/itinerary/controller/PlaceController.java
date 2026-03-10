package com.indiatravel.itinerary.controller;

import com.indiatravel.itinerary.dto.PlaceDto;
import com.indiatravel.itinerary.service.ItineraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final ItineraryService itineraryService;

    public PlaceController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @GetMapping("/search")
    public List<PlaceDto> searchPlaces(
            @RequestParam String city,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return itineraryService.searchPlaces(city, query, limit);
    }
}
