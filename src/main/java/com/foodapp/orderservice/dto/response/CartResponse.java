package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.enums.CartStatus;
import java.util.List;
import java.util.UUID;

public record CartResponse(UUID cartId, UUID restaurantId, CartStatus status,
                            List<CartItemResponse> items, MoneyResponse total) {
    public static CartResponse from(Cart cart) {
        return new CartResponse(
                cart.getId(), cart.getRestaurantId(), cart.getStatus(),
                cart.getItems().stream().map(CartItemResponse::from).toList(),
                MoneyResponse.from(cart.getTotalAmount())
        );
    }
}
