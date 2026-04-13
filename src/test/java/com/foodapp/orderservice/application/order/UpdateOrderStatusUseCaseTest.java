package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.UpdateOrderStatusRequest;
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
class UpdateOrderStatusUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks UpdateOrderStatusUseCase useCase;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void shouldTransitionToPreparing() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAID);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(orderId, restaurantId, new UpdateOrderStatusRequest(OrderStatus.PREPARING));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldPublishReadyEventWhenStatusIsReadyForPickup() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PREPARING);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(orderId, restaurantId, new UpdateOrderStatusRequest(OrderStatus.READY_FOR_PICKUP));

        verify(eventPublisher).publishOrderReady(order);
    }

    @Test
    void shouldNotPublishEventForNonReadyStatus() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAID);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(orderId, restaurantId, new UpdateOrderStatusRequest(OrderStatus.PREPARING));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(orderId, restaurantId,
                new UpdateOrderStatusRequest(OrderStatus.PREPARING)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldThrowWhenOrderBelongsToDifferentRestaurant() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, UUID.randomUUID(), OrderStatus.PAID);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(orderId, restaurantId,
                new UpdateOrderStatusRequest(OrderStatus.PREPARING)))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }
}
