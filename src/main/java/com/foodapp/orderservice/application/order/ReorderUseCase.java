package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.entity.CartItem;
import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.dto.response.CartResponse;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.CartRepository;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReorderUseCase {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;

    @Transactional
    public CartResponse execute(UUID orderId, UUID userId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(userId))
            throw new OrderNotBelongToUserException("Access denied");

        // Build new cart from previous order items (with original snapshots)
        var items = order.getItems().stream().map(oi -> CartItem.builder()
                .menuItemId(oi.getMenuItemId())
                .menuItemName(oi.getMenuItemName())
                .unitPrice(oi.getUnitPrice())
                .quantity(oi.getQuantity())
                .totalPrice(oi.getTotalPrice())
                .specialInstructions(oi.getSpecialInstructions())
                .build()).collect(Collectors.toList());

        Cart cart = Cart.builder()
                .userId(userId)
                .restaurantId(order.getRestaurantId())
                .status(CartStatus.ACTIVE)
                .items(items)
                .build();

        return CartResponse.from(cartRepository.save(cart));
    }
}
