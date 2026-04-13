package com.foodapp.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record PaymentCallbackRequest(
        UUID paymentId,      // HOLD_RELEASED gibi durumlarda null olabilir
        @NotBlank String status,
        String failureReason
) {}
