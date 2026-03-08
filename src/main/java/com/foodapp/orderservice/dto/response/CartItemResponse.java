package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.entity.CartItem;
import java.util.UUID;

public record CartItemResponse(UUID id, UUID menuItemId, String menuItemName,
                                int quantity, MoneyResponse unitPrice, MoneyResponse totalPrice,
                                String specialInstructions) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getId(), item.getMenuItemId(), item.getMenuItemName(),
                item.getQuantity(), MoneyResponse.from(item.getUnitPrice()),
                MoneyResponse.from(item.getTotalPrice()), item.getSpecialInstructions());
    }
}
