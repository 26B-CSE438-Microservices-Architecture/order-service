package com.foodapp.orderservice.domain.enums;

public enum OrderCancellationReason {
    CUSTOMER_REQUEST, PAYMENT_FAILED, PAYMENT_TIMEOUT,
    RESTAURANT_REJECTED, RESTAURANT_TIMEOUT, ADMIN_CANCEL
}
