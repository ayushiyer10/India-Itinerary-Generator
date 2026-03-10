package com.indiatravel.itinerary.service;

import com.indiatravel.itinerary.dto.AuthResponse;
import com.indiatravel.itinerary.dto.LoginRequest;
import com.indiatravel.itinerary.dto.SignupRequest;
import com.indiatravel.itinerary.repository.AppUser;
import com.indiatravel.itinerary.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String hash = passwordEncoder.encode(request.password());
        long userId = userRepository.createUser(request.name().trim(), request.email().trim().toLowerCase(), hash);
        return new AuthResponse(userId, request.name().trim(), request.email().trim().toLowerCase());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email().trim())
                .orElseThrow(() -> new NoSuchElementException("Account not found"));

        String hash = user.passwordHash();
        if (hash == null || hash.isBlank() || !passwordEncoder.matches(request.password(), hash)) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        return new AuthResponse(user.id(), user.name(), user.email());
    }
}
