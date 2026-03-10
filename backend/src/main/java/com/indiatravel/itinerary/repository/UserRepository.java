package com.indiatravel.itinerary.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUser> findByEmail(String email) {
        List<AppUser> users = jdbcTemplate.query("""
                        SELECT id, name, email, password_hash
                        FROM users
                        WHERE LOWER(email) = LOWER(?)
                        LIMIT 1
                        """,
                (rs, rowNum) -> new AppUser(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                ),
                email
        );
        if (users.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(users.getFirst());
    }

    public long createUser(String name, String email, String passwordHash) {
        String sql = """
                INSERT INTO users (name, email, password_hash)
                VALUES (?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, passwordHash);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("Email is already registered");
        }
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public boolean existsById(long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM users WHERE id = ?",
                Integer.class,
                userId
        );
        return count != null && count > 0;
    }
}
