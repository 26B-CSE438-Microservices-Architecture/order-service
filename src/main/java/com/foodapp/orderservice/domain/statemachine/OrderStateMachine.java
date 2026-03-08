package com.foodapp.orderservice.domain.statemachine;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.exception.InvalidOrderStateException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED,
                EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_PENDING,
                EnumSet.of(OrderStatus.PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.EXPIRED, OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_FAILED,
                EnumSet.of(OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.PAID,
                EnumSet.of(OrderStatus.CONFIRMED_BY_RESTAURANT, OrderStatus.REJECTED_BY_RESTAURANT,
                        OrderStatus.RESTAURANT_TIMEOUT, OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.REJECTED_BY_RESTAURANT,
                EnumSet.of(OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.RESTAURANT_TIMEOUT,
                EnumSet.of(OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED_BY_RESTAURANT,
                EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.PREPARING,
                EnumSet.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED));

        ALLOWED_TRANSITIONS.put(OrderStatus.READY_FOR_PICKUP,
                EnumSet.of(OrderStatus.ON_THE_WAY));

        ALLOWED_TRANSITIONS.put(OrderStatus.ON_THE_WAY,
                EnumSet.of(OrderStatus.DELIVERED));

        // Terminal states
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.EXPIRED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));

        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED,
                EnumSet.of(OrderStatus.REFUND_REQUESTED));

        ALLOWED_TRANSITIONS.put(OrderStatus.REFUND_REQUESTED,
                EnumSet.of(OrderStatus.REFUNDED));
    }

    public void validate(OrderStatus current, OrderStatus target) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(target)) {
            throw new InvalidOrderStateException(
                    String.format("Cannot transition from %s to %s", current, target));
        }
    }

    public Set<OrderStatus> getAllowedTransitions(OrderStatus current) {
        return ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class));
    }
}
