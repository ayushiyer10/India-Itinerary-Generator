package com.indiatravel.itinerary.dto;

public record PlaceDto(
        long id,
        String name,
        String city,
        String state,
        String category,
        double latitude,
        double longitude,
        int averageCostInr,
        int visitMinutes,
        double rating,
        String description
) {
}
