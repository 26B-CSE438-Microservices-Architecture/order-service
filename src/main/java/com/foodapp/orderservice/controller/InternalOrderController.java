package com.foodapp.orderservice.controller;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderCancellationReason;
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
            case "HOLD_CONFIRMED" -> {
                order.markPaymentHeld(request.paymentId());
                order.transitionTo(OrderStatus.PAYMENT_HELD, stateMachine, "PAYMENT_SERVICE", "Payment hold confirmed");
                orderRepository.save(order);
                eventPublisher.publishRestaurantApprovalRequested(order);
            }
            case "HOLD_FAILED" -> {
                order.markPaymentFailed();
                order.transitionTo(OrderStatus.PAYMENT_FAILED, stateMachine, "PAYMENT_SERVICE", request.failureReason());
                order.cancel(stateMachine, OrderCancellationReason.PAYMENT_FAILED,
                        request.failureReason(), "PAYMENT_SERVICE");
                orderRepository.save(order);
                eventPublisher.publishOrderCancelled(order);
            }
            case "CAPTURE_COMPLETED" -> {
                order.markPaymentCaptured(request.paymentId());
                order.transitionTo(OrderStatus.PAID, stateMachine, "PAYMENT_SERVICE", "Payment captured");
                orderRepository.save(order);
                eventPublisher.publishOrderConfirmed(order);
            }
            case "CAPTURE_FAILED" -> {
                order.markPaymentFailed();
                order.transitionTo(OrderStatus.PAYMENT_FAILED, stateMachine, "PAYMENT_SERVICE", request.failureReason());
                order.cancel(stateMachine, OrderCancellationReason.PAYMENT_CAPTURE_FAILED,
                        request.failureReason(), "PAYMENT_SERVICE");
                orderRepository.save(order);
                eventPublisher.publishOrderCancelled(order);
            }
            case "HOLD_RELEASED" -> {
                order.markHoldReleased();
                orderRepository.save(order);
            }
            default -> log.warn("Unknown payment callback status: {}", request.status());
        }
        return ResponseEntity.ok().build();
    }
}
