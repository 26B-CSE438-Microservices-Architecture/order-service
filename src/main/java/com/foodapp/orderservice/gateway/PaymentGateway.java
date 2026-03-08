package com.foodapp.orderservice.gateway;

import com.foodapp.orderservice.domain.enums.PaymentMethod;
import com.foodapp.orderservice.domain.valueobject.Money;
import java.util.UUID;

public interface PaymentGateway {
    PaymentInitiationResult initiatePayment(UUID orderId, UUID userId, Money amount, PaymentMethod paymentMethod);

    record PaymentInitiationResult(UUID paymentId, String status) {}
}
