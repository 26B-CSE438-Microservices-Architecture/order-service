package com.foodapp.orderservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCartItemRequest(
        @NotNull UUID menuItemId,
        @NotNull UUID restaurantId,
        @Min(1) int quantity,
        String specialInstructions
) {}
