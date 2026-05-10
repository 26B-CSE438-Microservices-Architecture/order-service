package com.foodapp.orderservice.event.consumer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class PaymentEventPayloads {

    private PaymentEventPayloads() {
    }

    static UUID paymentIdFrom(Object rawPaymentId) {
        String value = String.valueOf(rawPaymentId);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(("payment:" + value).getBytes(StandardCharsets.UTF_8));
        }
    }
}
