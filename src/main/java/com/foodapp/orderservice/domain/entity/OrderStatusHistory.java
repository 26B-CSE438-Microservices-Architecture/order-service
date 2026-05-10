package com.foodapp.orderservice.domain.entity;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus toStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    private String changedBy;
    private String reason;

    public UUID getOrderId() {
        return orderId != null ? orderId : order != null ? order.getId() : null;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}
