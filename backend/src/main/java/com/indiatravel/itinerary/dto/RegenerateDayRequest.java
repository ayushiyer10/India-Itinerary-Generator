package com.indiatravel.itinerary.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RegenerateDayRequest(
        @NotEmpty List<String> interests,
        String preferredTravelMode
) {
}
