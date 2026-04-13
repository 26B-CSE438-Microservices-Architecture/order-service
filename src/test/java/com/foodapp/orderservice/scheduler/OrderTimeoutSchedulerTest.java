package com.foodapp.orderservice.scheduler;

import com.foodapp.orderservice.config.scheduler.OrderTimeoutScheduler;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTimeoutSchedulerTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks OrderTimeoutScheduler scheduler;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void shouldExpirePaymentPendingOrder() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        order.setPaymentTimeoutAt(LocalDateTime.now().minusMinutes(1));

        when(orderRepository.findByStatusAndPaymentTimeoutAtBefore(eq(OrderStatus.PAYMENT_PENDING), any()))
                .thenReturn(List.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        scheduler.expirePaymentTimeouts();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldContinueWhenOneOrderFailsExpiry() {
        var badOrder = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        // No paymentTimeoutAt set — transition will still work from PAYMENT_PENDING → EXPIRED
        var goodOrder = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        goodOrder.setPaymentTimeoutAt(LocalDateTime.now().minusMinutes(1));

        // Simulate first order save throwing exception
        when(orderRepository.findByStatusAndPaymentTimeoutAtBefore(eq(OrderStatus.PAYMENT_PENDING), any()))
                .thenReturn(List.of(badOrder, goodOrder));
        when(orderRepository.save(badOrder)).thenThrow(new RuntimeException("DB error"));
        when(orderRepository.save(goodOrder)).thenReturn(goodOrder);

        // Should not throw — errors are caught per-order
        assertThatNoException().isThrownBy(() -> scheduler.expirePaymentTimeouts());

        assertThat(goodOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void shouldCancelOrderOnRestaurantTimeout() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        order.setRestaurantTimeoutAt(LocalDateTime.now().minusMinutes(1));

        when(orderRepository.findByStatusAndRestaurantTimeoutAtBefore(eq(OrderStatus.PAYMENT_HELD), any()))
                .thenReturn(List.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        scheduler.expireRestaurantTimeouts();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason())
                .isEqualTo(com.foodapp.orderservice.domain.enums.OrderCancellationReason.RESTAURANT_TIMEOUT);
    }

    @Test
    void shouldPublishHoldReleaseAndCancelledOnRestaurantTimeout() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        order.setRestaurantTimeoutAt(LocalDateTime.now().minusMinutes(1));

        when(orderRepository.findByStatusAndRestaurantTimeoutAtBefore(eq(OrderStatus.PAYMENT_HELD), any()))
                .thenReturn(List.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        scheduler.expireRestaurantTimeouts();

        verify(eventPublisher).publishPaymentHoldReleaseRequested(order);
        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldDoNothingWhenNoTimedOutOrders() {
        when(orderRepository.findByStatusAndPaymentTimeoutAtBefore(any(), any()))
                .thenReturn(List.of());

        scheduler.expirePaymentTimeouts();

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }
}
