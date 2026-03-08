package com.foodapp.orderservice.dto.request;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}
