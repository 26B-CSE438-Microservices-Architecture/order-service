package com.foodapp.orderservice.dto.request;

import com.foodapp.orderservice.domain.enums.OrderType;
import com.foodapp.orderservice.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
        @NotNull AddressRequest deliveryAddress,
        @NotNull PaymentMethod paymentMethod,
        @NotNull OrderType orderType,
        String notes
) {}
