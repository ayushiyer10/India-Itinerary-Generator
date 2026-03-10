package com.indiatravel.itinerary.dto;

import jakarta.validation.constraints.NotNull;

public record RouteEstimateRequest(
        @NotNull double fromLat,
        @NotNull double fromLng,
        @NotNull double toLat,
        @NotNull double toLng
) {
}
