package com.foodapp.orderservice.event.consumer;

import com.foodapp.orderservice.domain.aggregate.Order;
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
 * Payment service hold'u serbest bıraktı.
 * Kullanıcının parası hiç çekilmedi. Order zaten CANCELLED durumunda,
 * sadece paymentStatus'u RELEASED olarak güncelle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentHoldReleasedEventHandler {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "payment.hold_released", groupId = "order-service")
    public void handle(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> payload = (Map<String, Object>) record.value().get("payload");
        UUID orderId = UUID.fromString((String) payload.get("orderId"));

        log.info("Payment hold released for orderId={} — user was not charged", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.markHoldReleased();
        orderRepository.save(order);
    }
}
