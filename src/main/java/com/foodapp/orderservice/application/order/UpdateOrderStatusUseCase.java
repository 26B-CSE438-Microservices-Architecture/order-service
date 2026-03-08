package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.UpdateOrderStatusRequest;
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
public class UpdateOrderStatusUseCase {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID orderId, UUID restaurantId, UpdateOrderStatusRequest request) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getRestaurantId().equals(restaurantId))
            throw new OrderNotBelongToUserException("Order does not belong to this restaurant");

        order.transitionTo(request.status(), stateMachine, restaurantId.toString(), "Status updated by restaurant");
        orderRepository.save(order);

        if (request.status() == OrderStatus.READY_FOR_PICKUP) {
            eventPublisher.publishOrderReady(order);
        }
    }
}
