package com.foodapp.orderservice.event;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.consumer.PaymentHoldConfirmedEventHandler;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentHoldConfirmedEventHandlerTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks PaymentHoldConfirmedEventHandler handler;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "restaurantTimeoutMinutes", 5);
    }

    @Test
    void shouldTransitionToPaymentHeldAndSetRestaurantTimeout() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        handler.handle(buildRecord(orderId, paymentId));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_HELD);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.HELD);
        assertThat(order.getRestaurantTimeoutAt()).isNotNull();
        verify(eventPublisher).publishRestaurantApprovalRequested(order);
    }

    @Test
    void shouldThrowWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(buildRecord(orderId, UUID.randomUUID())))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldSaveOrderAfterTransition() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        handler.handle(buildRecord(orderId, UUID.randomUUID()));

        verify(orderRepository).save(order);
    }

    private ConsumerRecord<String, Map<String, Object>> buildRecord(UUID orderId, UUID paymentId) {
        return new ConsumerRecord<>("payment.hold_confirmed", 0, 0L, "key",
                Map.of("payload", Map.of(
                        "orderId", orderId.toString(),
                        "paymentId", paymentId.toString()
                )));
    }
}
