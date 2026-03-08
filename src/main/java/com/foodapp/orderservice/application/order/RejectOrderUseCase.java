package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderCancellationReason;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.RejectOrderRequest;
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
public class RejectOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID orderId, UUID restaurantId, RejectOrderRequest request) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getRestaurantId().equals(restaurantId))
            throw new OrderNotBelongToUserException("Order does not belong to this restaurant");

        order.transitionTo(OrderStatus.REJECTED_BY_RESTAURANT, stateMachine, restaurantId.toString(), request.rejectReason());
        order.cancel(stateMachine, OrderCancellationReason.RESTAURANT_REJECTED, request.rejectReason(), restaurantId.toString());
        orderRepository.save(order);
        eventPublisher.publishOrderCancelled(order);
    }
}
