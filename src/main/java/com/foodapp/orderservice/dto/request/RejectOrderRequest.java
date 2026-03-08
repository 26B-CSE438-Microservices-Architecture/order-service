package com.foodapp.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectOrderRequest(@NotBlank String rejectReason) {}
