package com.foodapp.orderservice.event.consumer;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundedEventHandler {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;

    @RabbitListener(queues = "payment.capture_completed.queue")
    @Transactional
    public void handle(@Payload Map<String, Object> event) {
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        UUID orderId = UUID.fromString((String) payload.get("orderId"));

        log.info("Payment refunded for orderId={}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        order.markRefunded();
        order.transitionTo(OrderStatus.REFUNDED, stateMachine, "PAYMENT_SERVICE", "Payment refunded");
        orderRepository.save(order);
    }
}
