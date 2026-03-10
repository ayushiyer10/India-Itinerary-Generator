package com.indiatravel.itinerary.dto;

import java.time.LocalDate;
import java.util.List;

public record TripDetailDto(
        long id,
        long userId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        int budgetInr,
        String pace,
        String preferredTravelMode,
        List<TripDayDto> days
) {
}
