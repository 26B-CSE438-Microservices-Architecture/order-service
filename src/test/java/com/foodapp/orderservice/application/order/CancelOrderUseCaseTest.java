package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.enums.UserRole;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.CancelOrderRequest;
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
class CancelOrderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks CancelOrderUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final CancelOrderRequest cancelRequest = new CancelOrderRequest("Test iptal");

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), userId, UserRole.CUSTOMER, cancelRequest))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldThrowWhenCustomerTriesToCancelOthersOrder() {
        var order = TestFixtures.buildOrder(UUID.randomUUID(), restaurantId, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UUID differentUser = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.execute(order.getId(), differentUser, UserRole.CUSTOMER, cancelRequest))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }

    @Test
    void shouldThrowWhenOrderIsNotCancellable() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.DELIVERED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(order.getId(), userId, UserRole.CUSTOMER, cancelRequest))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldCancelOrderSuccessfully() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(order.getId(), userId, UserRole.CUSTOMER, cancelRequest);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldPublishHoldReleaseWhenPaymentWasHeld() {
        var order = TestFixtures.buildOrderWithPaymentStatus(userId, restaurantId,
                OrderStatus.PAYMENT_HELD, PaymentStatus.HELD);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        useCase.execute(order.getId(), userId, UserRole.CUSTOMER, cancelRequest);

        verify(eventPublisher).publishPaymentHoldReleaseRequested(order);
        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldAllowAdminToCancelAnyOrder() {
        UUID adminId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        assertThatNoException().isThrownBy(() ->
                useCase.execute(order.getId(), adminId, UserRole.ADMIN, cancelRequest));
    }
}
