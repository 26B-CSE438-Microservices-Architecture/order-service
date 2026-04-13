package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestRefundUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks RequestRefundUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void shouldThrowWhenOrderBelongsToDifferentUser() {
        var order = TestFixtures.buildOrder(UUID.randomUUID(), restaurantId, OrderStatus.CANCELLED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(order.getId(), userId))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }

    @Test
    void shouldThrowWhenOrderIsNotCancelled() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(order.getId(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    void shouldThrowWhenPaymentWasNotCaptured() {
        var order = TestFixtures.buildOrderWithPaymentStatus(userId, restaurantId,
                OrderStatus.CANCELLED, PaymentStatus.HELD); // para tutuldu ama çekilmedi
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(order.getId(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("captured");
    }

    @Test
    void shouldRequestRefundAndPublishEventOnHappyPath() {
        var order = TestFixtures.buildOrderWithPaymentStatus(userId, restaurantId,
                OrderStatus.CANCELLED, PaymentStatus.PAID);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(order.getId(), userId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
        verify(eventPublisher).publishRefundRequested(order);
    }
}
