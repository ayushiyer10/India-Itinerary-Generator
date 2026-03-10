package com.indiatravel.itinerary.dto;

public record ExternalPlaceDto(
        String displayName,
        double latitude,
        double longitude,
        String type
) {
}
