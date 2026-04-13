package com.foodapp.orderservice.event;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.consumer.PaymentCaptureFailedEventHandler;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
class PaymentCaptureFailedEventHandlerTest {

    @Mock OrderRepository orderRepository;
    @Spy  OrderStateMachine stateMachine = new OrderStateMachine();
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks PaymentCaptureFailedEventHandler handler;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void shouldCancelOrderWhenCaptureFails() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_CAPTURE_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        handler.handle(buildRecord(orderId, "Banka hatası"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void shouldPublishOrderCancelledEvent() {
        UUID orderId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_CAPTURE_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        handler.handle(buildRecord(orderId, "Limit aşıldı"));

        verify(eventPublisher).publishOrderCancelled(order);
        verify(orderRepository).save(order);
    }

    private ConsumerRecord<String, Map<String, Object>> buildRecord(UUID orderId, String reason) {
        return new ConsumerRecord<>("payment.capture_failed", 0, 0L, "key",
                Map.of("payload", Map.of("orderId", orderId.toString(), "failureReason", reason)));
    }
}
