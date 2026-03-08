package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.entity.OrderItem;
import java.util.UUID;

public record OrderItemResponse(UUID id, UUID menuItemId, String menuItemName,
                                 int quantity, MoneyResponse unitPrice, MoneyResponse totalPrice,
                                 String specialInstructions) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getMenuItemId(), item.getMenuItemName(),
                item.getQuantity(), MoneyResponse.from(item.getUnitPrice()),
                MoneyResponse.from(item.getTotalPrice()), item.getSpecialInstructions());
    }
}
