package com.foodapp.orderservice.support;

import com.foodapp.orderservice.domain.enums.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class JwtTestHelper {

    public static final String TEST_SECRET = "test-secret-key-for-testing-purposes-only-256-bits-long";

    public static String bearerToken(UUID userId, UserRole role) {
        String token = Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role.name())
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }
}
