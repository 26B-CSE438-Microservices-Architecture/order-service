package com.foodapp.orderservice.statemachine;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach
    void setUp() { stateMachine = new OrderStateMachine(); }

    @ParameterizedTest(name = "{0} -> {1} gecerli olmali")
    @CsvSource({
            "CREATED, PAYMENT_PENDING",
            "CREATED, CANCELLED",
            "PAYMENT_PENDING, PAYMENT_HELD",
            "PAYMENT_PENDING, PAYMENT_FAILED",
            "PAYMENT_PENDING, EXPIRED",
            "PAYMENT_HELD, CONFIRMED_BY_RESTAURANT",
            "PAYMENT_HELD, REJECTED_BY_RESTAURANT",
            "PAYMENT_HELD, RESTAURANT_TIMEOUT",
            "PAYMENT_HELD, CANCELLED",
            "CONFIRMED_BY_RESTAURANT, PAYMENT_CAPTURE_PENDING",
            "CONFIRMED_BY_RESTAURANT, CANCELLED",
            "PAYMENT_CAPTURE_PENDING, PAID",
            "PAYMENT_CAPTURE_PENDING, PAYMENT_FAILED",
            "PAYMENT_FAILED, CANCELLED",
            "REJECTED_BY_RESTAURANT, CANCELLED",
            "RESTAURANT_TIMEOUT, CANCELLED",
            "PAID, PREPARING",
            "PAID, CANCELLED",
            "PREPARING, READY_FOR_PICKUP",
            "PREPARING, CANCELLED",
            "READY_FOR_PICKUP, ON_THE_WAY",
            "ON_THE_WAY, DELIVERED",
            "CANCELLED, REFUND_REQUESTED",
            "REFUND_REQUESTED, REFUNDED"
    })
    void shouldAllowValidTransitions(OrderStatus from, OrderStatus to) {
        assertThatNoException().isThrownBy(() -> stateMachine.validate(from, to));
    }

    @Test
    void shouldThrowForSkippingPaymentStep() {
        assertThatThrownBy(() -> stateMachine.validate(OrderStatus.CREATED, OrderStatus.PAID))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot transition from CREATED to PAID");
    }

    @Test
    void shouldThrowForGoingBackwards() {
        assertThatThrownBy(() -> stateMachine.validate(OrderStatus.PREPARING, OrderStatus.PAYMENT_HELD))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void shouldThrowFromDeliveredTerminalState() {
        assertThatThrownBy(() -> stateMachine.validate(OrderStatus.DELIVERED, OrderStatus.PREPARING))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void shouldThrowFromExpiredTerminalState() {
        assertThatThrownBy(() -> stateMachine.validate(OrderStatus.EXPIRED, OrderStatus.PAYMENT_PENDING))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void shouldThrowFromRefundedTerminalState() {
        assertThatThrownBy(() -> stateMachine.validate(OrderStatus.REFUNDED, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void shouldReturnAllowedTransitionsForCreated() {
        var allowed = stateMachine.getAllowedTransitions(OrderStatus.CREATED);
        assertThat(allowed).containsExactlyInAnyOrder(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void shouldReturnEmptyTransitionsForTerminalStates() {
        assertThat(stateMachine.getAllowedTransitions(OrderStatus.DELIVERED)).isEmpty();
        assertThat(stateMachine.getAllowedTransitions(OrderStatus.EXPIRED)).isEmpty();
        assertThat(stateMachine.getAllowedTransitions(OrderStatus.REFUNDED)).isEmpty();
    }
}
