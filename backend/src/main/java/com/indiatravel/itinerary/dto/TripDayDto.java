package com.indiatravel.itinerary.dto;

import java.util.List;

public record TripDayDto(
        int dayNumber,
        String city,
        String theme,
        String notes,
        int estimatedCostInr,
        int totalTravelMinutes,
        List<TripPlaceDto> places
) {
}
