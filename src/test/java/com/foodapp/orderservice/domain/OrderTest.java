package com.foodapp.orderservice.domain;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.*;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.exception.InvalidOrderStateException;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private OrderStateMachine stateMachine;
    private Order order;
    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stateMachine = new OrderStateMachine();
        order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
    }

    @Test
    void shouldRecordHistoryEntryOnTransition() {
        order.transitionTo(OrderStatus.CONFIRMED_BY_RESTAURANT, stateMachine, "RESTAURANT", "Onaylandı");

        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.PAYMENT_HELD);
        assertThat(order.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.CONFIRMED_BY_RESTAURANT);
        assertThat(order.getStatusHistory().get(0).getChangedBy()).isEqualTo("RESTAURANT");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED_BY_RESTAURANT);
    }

    @Test
    void shouldThrowOnInvalidTransition() {
        // PAYMENT_HELD → PAID doğrudan geçiş yok
        assertThatThrownBy(() -> order.transitionTo(OrderStatus.PAID, stateMachine, "SYSTEM", "test"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void shouldCancelAndSetCancellationReason() {
        order.cancel(stateMachine, OrderCancellationReason.CUSTOMER_REQUEST, "Müşteri iptal etti", "USER");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo(OrderCancellationReason.CUSTOMER_REQUEST);
        assertThat(order.getCancelReason()).isEqualTo("Müşteri iptal etti");
    }

    @Test
    void shouldReturnTrueForCancellableStates() {
        // PAYMENT_HELD cancellable
        assertThat(order.isCancellable()).isTrue();

        Order created = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.CREATED);
        assertThat(created.isCancellable()).isTrue();
    }

    @Test
    void shouldReturnFalseForNonCancellableStates() {
        Order delivered = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.DELIVERED);
        assertThat(delivered.isCancellable()).isFalse();

        Order expired = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.EXPIRED);
        assertThat(expired.isCancellable()).isFalse();
    }

    @Test
    void shouldRequireHoldReleaseWhenPaymentIsHeld() {
        order.markPaymentHeld(UUID.randomUUID());
        assertThat(order.isHoldReleaseRequired()).isTrue();
        assertThat(order.isRefundRequired()).isFalse();
    }

    @Test
    void shouldRequireRefundWhenPaymentIsPaid() {
        order.markPaymentCaptured(UUID.randomUUID());
        assertThat(order.isRefundRequired()).isTrue();
        assertThat(order.isHoldReleaseRequired()).isFalse();
    }

    @Test
    void shouldUpdatePaymentStatusOnMarkPaymentHeld() {
        UUID paymentId = UUID.randomUUID();
        order.markPaymentHeld(paymentId);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.HELD);
        assertThat(order.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void shouldUpdatePaymentStatusOnMarkPaymentFailed() {
        order.markPaymentFailed();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
