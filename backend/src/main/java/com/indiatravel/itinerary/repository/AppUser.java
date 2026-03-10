package com.indiatravel.itinerary.repository;

public record AppUser(
        long id,
        String name,
        String email,
        String passwordHash
) {
}
