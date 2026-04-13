package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.RejectOrderRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RejectOrderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks RejectOrderUseCase useCase;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void shouldCancelOrderWhenRestaurantRejects() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(orderId, restaurantId, new RejectOrderRequest("Malzeme yok"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldPublishHoldReleaseAndCancelledEvents() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(orderId, restaurantId, new RejectOrderRequest("Kapalıyız"));

        verify(eventPublisher).publishPaymentHoldReleaseRequested(order);
        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId, restaurantId, new RejectOrderRequest("x")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldThrowWhenOrderBelongsToDifferentRestaurant() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, UUID.randomUUID(), OrderStatus.PAYMENT_HELD);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(orderId, restaurantId, new RejectOrderRequest("x")))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }

    @Test
    void shouldSetCancellationReasonToRestaurantRejected() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(orderId, restaurantId, new RejectOrderRequest("Stok bitti"));

        assertThat(order.getCancellationReason())
                .isEqualTo(com.foodapp.orderservice.domain.enums.OrderCancellationReason.RESTAURANT_REJECTED);
    }
}
