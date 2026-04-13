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

    /** Sipariş oluşturuldu, payment service'ten hold (para tutma) isteği */
    public void publishPaymentHoldRequested(Order order) {
        publish("order.payment.hold.requested", buildEvent("ORDER_PAYMENT_HOLD_REQUESTED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "restaurantId", order.getRestaurantId(),
                "totalAmount", Map.of("amount", order.getTotalAmount().getAmount(), "currency", order.getTotalAmount().getCurrency()),
                "paymentMethod", order.getPaymentMethod()
        )));
    }

    /** Para tutuldu, restorana onay isteği gönder */
    public void publishRestaurantApprovalRequested(Order order) {
        publish("order.restaurant.approval.requested", buildEvent("ORDER_RESTAURANT_APPROVAL_REQUESTED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "restaurantId", order.getRestaurantId(),
                "userId", order.getUserId(),
                "totalAmount", Map.of("amount", order.getTotalAmount().getAmount(), "currency", order.getTotalAmount().getCurrency()),
                "items", order.getItems().stream().map(i -> Map.of(
                        "menuItemId", i.getMenuItemId(),
                        "menuItemName", i.getMenuItemName(),
                        "quantity", i.getQuantity(),
                        "unitPrice", i.getUnitPrice().getAmount()
                )).toList()
        )));
    }

    /** Restoran onayladı, payment service'ten capture (ödeme çekimi) isteği */
    public void publishPaymentCaptureRequested(Order order) {
        publish("order.payment.capture.requested", buildEvent("ORDER_PAYMENT_CAPTURE_REQUESTED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "paymentId", order.getPaymentId(),
                "userId", order.getUserId(),
                "totalAmount", Map.of("amount", order.getTotalAmount().getAmount(), "currency", order.getTotalAmount().getCurrency())
        )));
    }

    /** Restoran reddetti veya timeout, payment service'ten hold release isteği */
    public void publishPaymentHoldReleaseRequested(Order order) {
        publish("order.payment.hold.release.requested", buildEvent("ORDER_PAYMENT_HOLD_RELEASE_REQUESTED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "paymentId", order.getPaymentId() != null ? order.getPaymentId() : "",
                "userId", order.getUserId(),
                "reason", order.getCancellationReason() != null ? order.getCancellationReason().name() : "UNKNOWN"
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

    public void publishRefundRequested(Order order) {
        publish("order.refund.requested", buildEvent("ORDER_REFUND_REQUESTED", order.getCorrelationId(), Map.of(
                "orderId", order.getId(),
                "paymentId", order.getPaymentId(),
                "userId", order.getUserId(),
                "totalAmount", Map.of("amount", order.getTotalAmount().getAmount(), "currency", order.getTotalAmount().getCurrency())
        )));
    }

    private void publish(String topic, Map<String, Object> event) {
        try {
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId((String) event.get("correlationId"))
                    .eventType((String) event.get("eventType"))
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(event))
                    .createdAt(LocalDateTime.now())
                    .processed(false)
                    .retryCount(0)
                    .build();

            outboxRepository.save(outboxEvent);
            log.debug("Event saved to outbox: {}", outboxEvent.getEventType());
        } catch (Exception e) {
            log.error("Failed to serialize and save event to outbox", e);
            throw new RuntimeException("Outbox save failed", e);
        }
    }
}