package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.OrderType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID orderId, OrderStatus status, OrderType orderType,
                             UUID restaurantId, MoneyResponse totalAmount, MoneyResponse deliveryFee,
                             AddressResponse deliveryAddress, String notes,
                             LocalDateTime estimatedDeliveryTime, LocalDateTime createdAt,
                             List<OrderItemResponse> items,
                             List<OrderStatusHistoryResponse> statusHistory) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(), order.getStatus(), order.getOrderType(),
                order.getRestaurantId(),
                MoneyResponse.from(order.getTotalAmount()),
                MoneyResponse.from(order.getDeliveryFee()),
                AddressResponse.from(order.getDeliveryAddress()),
                order.getNotes(), order.getEstimatedDeliveryTime(), order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getStatusHistory().stream().map(OrderStatusHistoryResponse::from).toList()
        );
    }
}
