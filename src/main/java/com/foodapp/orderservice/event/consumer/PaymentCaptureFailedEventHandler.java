package com.foodapp.orderservice.event.consumer;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderCancellationReason;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Restoran onayından sonra ödeme çekimi (capture) başarısız oldu.
 * Nadir bir durum; siparişi iptal et. Payment service kendi içinde çekimi geri alır.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCaptureFailedEventHandler {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @KafkaListener(topics = "payment.capture_failed", groupId = "order-service")
    @Transactional
    public void handle(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> payload = (Map<String, Object>) record.value().get("payload");
        UUID orderId = UUID.fromString((String) payload.get("orderId"));
        String failureReason = (String) payload.get("failureReason");

        log.error("Payment capture failed for orderId={}, reason={}", orderId, failureReason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // PAYMENT_CAPTURE_PENDING → PAYMENT_FAILED → CANCELLED
        order.markPaymentFailed();
        order.transitionTo(OrderStatus.PAYMENT_FAILED, stateMachine, "PAYMENT_SERVICE", failureReason);
        order.cancel(stateMachine, OrderCancellationReason.PAYMENT_CAPTURE_FAILED, failureReason, "PAYMENT_SERVICE");
        orderRepository.save(order);

        eventPublisher.publishOrderCancelled(order);
    }
}
