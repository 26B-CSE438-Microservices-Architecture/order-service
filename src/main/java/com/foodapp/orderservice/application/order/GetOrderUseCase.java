package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.UserRole;
import com.foodapp.orderservice.dto.response.OrderResponse;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetOrderUseCase {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderResponse execute(UUID orderId, UUID requestingUserId, UserRole role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        authorize(order, requestingUserId, role);
        return OrderResponse.from(order);
    }

    private void authorize(Order order, UUID userId, UserRole role) {
        if (role == UserRole.ADMIN) return;
        if (role == UserRole.CUSTOMER && !order.getUserId().equals(userId))
            throw new OrderNotBelongToUserException("Access denied");
        if (role == UserRole.RESTAURANT_OWNER && !order.getRestaurantId().equals(userId))
            throw new OrderNotBelongToUserException("Access denied");
    }
}
