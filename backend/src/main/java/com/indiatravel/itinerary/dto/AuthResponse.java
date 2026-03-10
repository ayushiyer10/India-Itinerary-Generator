package com.indiatravel.itinerary.dto;

public record AuthResponse(
        long userId,
        String name,
        String email
) {
}
