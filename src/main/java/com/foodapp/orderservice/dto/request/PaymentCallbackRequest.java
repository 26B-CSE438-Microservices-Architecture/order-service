package com.foodapp.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentCallbackRequest(
        @NotNull UUID paymentId,
        @NotBlank String status,
        String failureReason
) {}
