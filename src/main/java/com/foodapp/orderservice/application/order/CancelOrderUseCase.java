package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderCancellationReason;
import com.foodapp.orderservice.domain.enums.UserRole;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.CancelOrderRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID orderId, UUID requestingUserId, UserRole role, CancelOrderRequest request) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (role == UserRole.CUSTOMER && !order.getUserId().equals(requestingUserId))
            throw new OrderNotBelongToUserException("Access denied");

        if (!order.isCancellable())
            throw new IllegalStateException("Order cannot be cancelled in current state: " + order.getStatus());

        var reason = role == UserRole.ADMIN ? OrderCancellationReason.ADMIN_CANCEL : OrderCancellationReason.CUSTOMER_REQUEST;
        order.cancel(stateMachine, reason, request.cancelReason(), requestingUserId.toString());
        orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order);
    }
}
