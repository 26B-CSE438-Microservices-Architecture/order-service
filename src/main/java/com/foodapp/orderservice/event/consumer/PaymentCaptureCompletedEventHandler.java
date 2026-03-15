package com.foodapp.orderservice.event.consumer;

import com.foodapp.orderservice.domain.aggregate.Order;
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

import java.util.Map;
import java.util.UUID;

/**
 * Restoran onayından sonra payment service ödemeyi başarıyla çekti (capture).
 * Order'ı PAID durumuna geçir; restoran hazırlamaya başlayabilir.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCaptureCompletedEventHandler {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @KafkaListener(topics = "payment.capture_completed", groupId = "order-service")
    public void handle(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> payload = (Map<String, Object>) record.value().get("payload");
        UUID orderId = UUID.fromString((String) payload.get("orderId"));
        UUID paymentId = UUID.fromString((String) payload.get("paymentId"));

        log.info("Payment capture completed for orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // PAYMENT_CAPTURE_PENDING → PAID
        order.markPaymentCaptured(paymentId);
        order.transitionTo(OrderStatus.PAID, stateMachine, "PAYMENT_SERVICE", "Payment captured successfully");
        orderRepository.save(order);

        // Restorana "hazırlamaya başlayabilirsiniz" sinyali
        eventPublisher.publishOrderConfirmed(order);
        log.info("Payment captured, order confirmed for preparation. orderId={}", orderId);
    }
}
