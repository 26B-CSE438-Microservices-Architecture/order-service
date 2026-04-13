package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.ConfirmOrderRequest;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks ConfirmOrderUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), restaurantId, new ConfirmOrderRequest(null)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldThrowWhenOrderBelongsToDifferentRestaurant() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UUID differentRestaurant = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.execute(order.getId(), differentRestaurant, new ConfirmOrderRequest(null)))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }

    @Test
    void shouldConfirmOrderAndRequestCapture() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(order.getId(), restaurantId, new ConfirmOrderRequest(null));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_CAPTURE_PENDING);
        verify(eventPublisher).publishPaymentCaptureRequested(order);
    }

    @Test
    void shouldSetEstimatedDeliveryTimeWhenProvided() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(order.getId(), restaurantId, new ConfirmOrderRequest(20));

        assertThat(order.getEstimatedDeliveryTime()).isNotNull();
    }
}
