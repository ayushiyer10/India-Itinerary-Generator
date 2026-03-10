package com.indiatravel.itinerary.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record GenerateTripRequest(
        @NotNull Long userId,
        @NotBlank String title,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotEmpty List<String> cities,
        @NotEmpty List<String> interests,
        @Min(1000) int budgetInr,
        @NotBlank String pace,
        @NotBlank String preferredTravelMode
) {
}
