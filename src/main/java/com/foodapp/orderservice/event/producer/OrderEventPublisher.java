package com.foodapp.orderservice.event.producer;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.entity.OutboxEvent;
import com.foodapp.orderservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    // KAFKA TEMPLATE SİLİNDİ, YERİNE OUTBOX GELDİ
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishOrderCreated(Order order) {
        publish("order.created", buildEvent("ORDER_CREATED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "restaurantId", order.getRestaurantId(),
                "totalAmount", Map.of("amount", order.getTotalAmount().getAmount(), "currency", order.getTotalAmount().getCurrency()),
                "paymentMethod", order.getPaymentMethod()
        )));
    }

    public void publishOrderConfirmed(Order order) {
        publish("order.confirmed", buildEvent("ORDER_CONFIRMED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "restaurantId", order.getRestaurantId()
        )));
    }

    public void publishOrderCancelled(Order order) {
        publish("order.cancelled", buildEvent("ORDER_CANCELLED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "cancelledBy", order.getCancellationReason() != null ? order.getCancellationReason().name() : "UNKNOWN",
                "refundRequired", order.isRefundRequired(),
                "paymentId", order.getPaymentId() != null ? order.getPaymentId() : ""
        )));
    }

    public void publishOrderReady(Order order) {
        publish("order.ready", buildEvent("ORDER_READY", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "restaurantId", order.getRestaurantId()
        )));
    }

    public void publishOrderDelivered(Order order) {
        publish("order.delivered", buildEvent("ORDER_DELIVERED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "deliveredAt", LocalDateTime.now()
        )));
    }

    private Map<String, Object> buildEvent(String type, String correlationId, Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", type);
        event.put("correlationId", correlationId);
        event.put("occurredAt", LocalDateTime.now().toString());
        event.put("payload", payload);
        return event;
    }

    private void publish(String topic, Map<String, Object> event) {
        try {
            // Artık Kafka'ya değil veritabanına Outbox kaydı atıyoruz.
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId((String) event.get("correlationId"))
                    .eventType((String) event.get("eventType"))
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(LocalDateTime.now())
                    .processed(false)
                    .build();

            outboxRepository.save(outboxEvent);
            log.debug("Event saved to outbox: {}", outboxEvent.getEventType());
        } catch (Exception e) {
            log.error("Failed to serialize and save event to outbox", e);
            throw new RuntimeException("Outbox save failed", e);
        }
    }
}