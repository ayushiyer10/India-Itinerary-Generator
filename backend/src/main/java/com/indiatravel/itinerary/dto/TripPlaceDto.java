package com.indiatravel.itinerary.dto;

import java.time.LocalTime;

public record TripPlaceDto(
        long placeId,
        String name,
        String category,
        double latitude,
        double longitude,
        LocalTime arrivalTime,
        LocalTime departureTime,
        String transportMode,
        int travelMinutes,
        int averageCostInr
) {
}
