package com.foodapp.orderservice.gateway;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RestaurantGateway {
    MenuValidationResult validateOrderItems(UUID restaurantId, List<OrderItemRequest> items);
    boolean isRestaurantOpen(UUID restaurantId);

    record OrderItemRequest(UUID menuItemId, int quantity) {}
    record ValidatedItem(UUID menuItemId, String name, BigDecimal price, boolean available) {}
    record MenuValidationResult(boolean valid, List<ValidatedItem> items, String errorMessage) {}
}
