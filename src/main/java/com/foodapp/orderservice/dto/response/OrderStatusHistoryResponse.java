package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.entity.OrderStatusHistory;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import java.time.LocalDateTime;

public record OrderStatusHistoryResponse(OrderStatus fromStatus, OrderStatus toStatus,
                                          LocalDateTime changedAt, String changedBy, String reason) {
    public static OrderStatusHistoryResponse from(OrderStatusHistory h) {
        return new OrderStatusHistoryResponse(h.getFromStatus(), h.getToStatus(),
                h.getChangedAt(), h.getChangedBy(), h.getReason());
    }
}
