package com.indiatravel.itinerary.repository;

import com.indiatravel.itinerary.dto.PlaceDto;
import com.indiatravel.itinerary.dto.TripDayDto;
import com.indiatravel.itinerary.dto.TripDetailDto;
import com.indiatravel.itinerary.dto.TripPlaceDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ItineraryRepository {

    private final JdbcTemplate jdbcTemplate;

    public ItineraryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createTrip(long userId, String title, LocalDate startDate, LocalDate endDate, int budgetInr, String pace, String preferredTravelMode) {
        String sql = """
                INSERT INTO trips (user_id, title, start_date, end_date, budget_inr, pace, preferred_travel_mode)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(startDate));
            ps.setDate(4, Date.valueOf(endDate));
            ps.setInt(5, budgetInr);
            ps.setString(6, pace);
            ps.setString(7, preferredTravelMode);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public long createTripDay(long tripId, int dayNumber, String city, String theme, String notes, int estimatedCostInr, int totalTravelMinutes) {
        String sql = """
                INSERT INTO trip_days (trip_id, day_number, city, theme, notes, estimated_cost_inr, total_travel_minutes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, tripId);
            ps.setInt(2, dayNumber);
            ps.setString(3, city);
            ps.setString(4, theme);
            ps.setString(5, notes);
            ps.setInt(6, estimatedCostInr);
            ps.setInt(7, totalTravelMinutes);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void addPlaceToTripDay(long tripDayId, long placeId, int sortOrder, LocalTime arrivalTime, LocalTime departureTime, String transportMode, int travelMinutes) {
        jdbcTemplate.update("""
                        INSERT INTO trip_day_places (trip_day_id, place_id, sort_order, arrival_time, departure_time, transport_mode, travel_minutes)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                tripDayId, placeId, sortOrder, Time.valueOf(arrivalTime), Time.valueOf(departureTime), transportMode, travelMinutes);
    }

    public List<PlaceDto> findPlaces(String city, List<String> interests, int limit) {
        if (interests == null || interests.isEmpty()) {
            return findPlacesByCity(city, limit);
        }
        String inClause = interests.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT id, name, city, state, category, latitude, longitude, average_cost_inr, visit_minutes, rating, description
                FROM places
                WHERE LOWER(city) = LOWER(?)
                  AND LOWER(category) IN (""" + inClause + ") ORDER BY rating DESC LIMIT ?";

        List<Object> params = new ArrayList<>();
        params.add(city);
        params.addAll(interests.stream().map(String::toLowerCase).toList());
        params.add(limit);

        return jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> new PlaceDto(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("city"),
                rs.getString("state"),
                rs.getString("category"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getInt("average_cost_inr"),
                rs.getInt("visit_minutes"),
                rs.getDouble("rating"),
                rs.getString("description")
        ));
    }

    public List<PlaceDto> findPlacesByCity(String city, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, name, city, state, category, latitude, longitude, average_cost_inr, visit_minutes, rating, description
                        FROM places
                        WHERE LOWER(city) = LOWER(?)
                        ORDER BY rating DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> new PlaceDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("category"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("average_cost_inr"),
                        rs.getInt("visit_minutes"),
                        rs.getDouble("rating"),
                        rs.getString("description")
                ),
                city, limit);
    }

    public PlaceDto upsertPlace(String name, String city, String state, String category, double latitude, double longitude, int averageCostInr, int visitMinutes, double rating, String description) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO places (name, city, state, category, latitude, longitude, average_cost_inr, visit_minutes, rating, description)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (name, city) DO UPDATE SET
                            state = EXCLUDED.state,
                            category = EXCLUDED.category,
                            latitude = EXCLUDED.latitude,
                            longitude = EXCLUDED.longitude,
                            average_cost_inr = EXCLUDED.average_cost_inr,
                            visit_minutes = EXCLUDED.visit_minutes,
                            rating = EXCLUDED.rating,
                            description = EXCLUDED.description
                        RETURNING id, name, city, state, category, latitude, longitude, average_cost_inr, visit_minutes, rating, description
                        """,
                (rs, rowNum) -> new PlaceDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("category"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("average_cost_inr"),
                        rs.getInt("visit_minutes"),
                        rs.getDouble("rating"),
                        rs.getString("description")
                ),
                name, city, state, category, latitude, longitude, averageCostInr, visitMinutes, rating, description
        );
    }

    public List<PlaceDto> findPlacesByCityWide(String city, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, name, city, state, category, latitude, longitude, average_cost_inr, visit_minutes, rating, description
                        FROM places
                        WHERE LOWER(city) = LOWER(?)
                        ORDER BY rating DESC, id
                        LIMIT ?
                        """,
                (rs, rowNum) -> new PlaceDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("city"),
                        rs.getString("state"),
                        rs.getString("category"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("average_cost_inr"),
                        rs.getInt("visit_minutes"),
                        rs.getDouble("rating"),
                        rs.getString("description")
                ),
                city, limit);
    }

    public void deleteTripDayPlan(long tripId, int dayNumber) {
        Optional<Long> tripDayId = jdbcTemplate.query("""
                        SELECT id FROM trip_days WHERE trip_id = ? AND day_number = ?
                        """,
                rs -> rs.next() ? Optional.of(rs.getLong("id")) : Optional.empty(),
                tripId, dayNumber);
        tripDayId.ifPresent(id -> {
            jdbcTemplate.update("DELETE FROM trip_day_places WHERE trip_day_id = ?", id);
            jdbcTemplate.update("DELETE FROM trip_days WHERE id = ?", id);
        });
    }

    public Optional<TripDetailDto> findTripById(long tripId) {
        List<TripDetailDto> trips = jdbcTemplate.query("""
                        SELECT id, user_id, title, start_date, end_date, budget_inr, pace, preferred_travel_mode
                        FROM trips
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new TripDetailDto(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("title"),
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate(),
                        rs.getInt("budget_inr"),
                        rs.getString("pace"),
                        rs.getString("preferred_travel_mode"),
                        List.of()
                ), tripId);

        if (trips.isEmpty()) {
            return Optional.empty();
        }

        TripDetailDto base = trips.getFirst();
        List<TripDayDto> days = findTripDays(tripId);
        return Optional.of(new TripDetailDto(
                base.id(),
                base.userId(),
                base.title(),
                base.startDate(),
                base.endDate(),
                base.budgetInr(),
                base.pace(),
                base.preferredTravelMode(),
                days
        ));
    }

    private List<TripDayDto> findTripDays(long tripId) {
        List<Map<String, Object>> dayRows = jdbcTemplate.queryForList("""
                SELECT id, day_number, city, theme, notes, estimated_cost_inr, total_travel_minutes
                FROM trip_days
                WHERE trip_id = ?
                ORDER BY day_number
                """, tripId);

        if (dayRows.isEmpty()) {
            return List.of();
        }

        List<Long> dayIds = dayRows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        String inClause = dayIds.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = """
                SELECT tdp.trip_day_id, p.id as place_id, p.name, p.category, p.latitude, p.longitude, p.average_cost_inr,
                       tdp.arrival_time, tdp.departure_time, tdp.transport_mode, tdp.travel_minutes
                FROM trip_day_places tdp
                JOIN places p ON p.id = tdp.place_id
                WHERE tdp.trip_day_id IN (""" + inClause + ") ORDER BY tdp.trip_day_id, tdp.sort_order";

        List<TripPlaceRow> placeRows = jdbcTemplate.query(sql, dayIds.toArray(), (rs, rowNum) -> new TripPlaceRow(
                rs.getLong("trip_day_id"),
                new TripPlaceDto(
                        rs.getLong("place_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getTime("arrival_time").toLocalTime(),
                        rs.getTime("departure_time").toLocalTime(),
                        rs.getString("transport_mode"),
                        rs.getInt("travel_minutes"),
                        rs.getInt("average_cost_inr")
                )));

        Map<Long, List<TripPlaceDto>> placesByDay = new HashMap<>();
        placeRows.forEach(row -> placesByDay.computeIfAbsent(row.tripDayId(), ignored -> new ArrayList<>()).add(row.place()));

        List<TripDayDto> result = new ArrayList<>();
        for (Map<String, Object> row : dayRows) {
            long dayId = ((Number) row.get("id")).longValue();
            result.add(new TripDayDto(
                    ((Number) row.get("day_number")).intValue(),
                    (String) row.get("city"),
                    (String) row.get("theme"),
                    (String) row.get("notes"),
                    ((Number) row.get("estimated_cost_inr")).intValue(),
                    ((Number) row.get("total_travel_minutes")).intValue(),
                    placesByDay.getOrDefault(dayId, List.of())
            ));
        }
        return result;
    }

    private record TripPlaceRow(long tripDayId, TripPlaceDto place) {
    }
}
