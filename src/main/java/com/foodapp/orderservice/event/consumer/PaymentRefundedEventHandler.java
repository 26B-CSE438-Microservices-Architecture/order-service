package com.foodapp.orderservice.event.consumer;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundedEventHandler {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;

    @KafkaListener(topics = "payment.refunded", groupId = "order-service")
    public void handle(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> payload = (Map<String, Object>) record.value().get("payload");
        UUID orderId = UUID.fromString((String) payload.get("orderId"));

        log.info("Payment refunded for orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.markRefunded();
        order.transitionTo(OrderStatus.REFUNDED, stateMachine, "PAYMENT_SERVICE", "Payment refunded");
        orderRepository.save(order);
    }
}
