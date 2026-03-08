package com.foodapp.orderservice.config.jwt;

import com.foodapp.orderservice.domain.enums.UserRole;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, UserRole role) {}
