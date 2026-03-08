package com.foodapp.orderservice.controller;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.PaymentCallbackRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
@Slf4j
public class InternalOrderController {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @PostMapping("/{orderId}/payment-callback")
    public ResponseEntity<Void> paymentCallback(@PathVariable UUID orderId,
                                                 @Valid @RequestBody PaymentCallbackRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        switch (request.status()) {
            case "COMPLETED" -> {
                order.markPaymentCompleted(request.paymentId());
                order.transitionTo(OrderStatus.PAID, stateMachine, "PAYMENT_SERVICE", "Payment completed");
                orderRepository.save(order);
                eventPublisher.publishOrderConfirmed(order);
            }
            case "FAILED" -> {
                order.markPaymentFailed();
                order.transitionTo(OrderStatus.PAYMENT_FAILED, stateMachine, "PAYMENT_SERVICE", request.failureReason());
                orderRepository.save(order);
            }
            default -> log.warn("Unknown payment status: {}", request.status());
        }
        return ResponseEntity.ok().build();
    }
}
