package com.indiatravel.itinerary.controller;

import com.indiatravel.itinerary.dto.GenerateTripRequest;
import com.indiatravel.itinerary.dto.GenerateTripResponse;
import com.indiatravel.itinerary.dto.RegenerateDayRequest;
import com.indiatravel.itinerary.dto.TripDetailDto;
import com.indiatravel.itinerary.service.ItineraryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final ItineraryService itineraryService;

    public TripController(ItineraryService itineraryService) {
        this.itineraryService = itineraryService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public GenerateTripResponse generate(@Valid @RequestBody GenerateTripRequest request) {
        long tripId = itineraryService.generateTrip(request);
        return new GenerateTripResponse(tripId, "Trip generated successfully");
    }

    @GetMapping("/{tripId}")
    public TripDetailDto getTrip(@PathVariable long tripId) {
        return itineraryService.getTrip(tripId);
    }

    @PostMapping("/{tripId}/regenerate-day/{dayNumber}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void regenerateDay(@PathVariable long tripId, @PathVariable int dayNumber, @Valid @RequestBody RegenerateDayRequest request) {
        itineraryService.regenerateDay(tripId, dayNumber, request);
    }
}
