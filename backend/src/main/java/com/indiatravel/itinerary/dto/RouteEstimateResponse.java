package com.indiatravel.itinerary.dto;

public record RouteEstimateResponse(
        double distanceKm,
        int durationMinutes
) {
}
