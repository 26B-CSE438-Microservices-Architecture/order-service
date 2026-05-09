package com.foodapp.orderservice.event.consumer;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payment service parayı başarıyla tuttu (hold).
 * Order'ı PAYMENT_HELD durumuna geçir, restoran timeout saatini şimdi başlat,
 * ve restorana onay bildirimi gönder.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentHoldConfirmedEventHandler {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Value("${order.restaurant-timeout-minutes:5}")
    private int restaurantTimeoutMinutes;

    @RabbitListener(queues = "payment.capture_completed.queue")
    @Transactional
    public void handle(@Payload Map<String, Object> event) {
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        UUID orderId = UUID.fromString((String) payload.get("orderId"));
        UUID paymentId = UUID.fromString((String) payload.get("paymentId"));

        log.info("Payment hold confirmed for orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.markPaymentHeld(paymentId);
        order.transitionTo(OrderStatus.PAYMENT_HELD, stateMachine, "PAYMENT_SERVICE", "Payment hold confirmed");

        // Restoran timeout süreci hold onayından itibaren başlar (checkout anından değil)
        order.setRestaurantTimeoutAt(LocalDateTime.now().plusMinutes(restaurantTimeoutMinutes));

        orderRepository.save(order);

        // Para tutuldu, şimdi restorana onay isteği gönder
        eventPublisher.publishRestaurantApprovalRequested(order);
        log.info("Restaurant approval requested for orderId={}", orderId);
    }
}
