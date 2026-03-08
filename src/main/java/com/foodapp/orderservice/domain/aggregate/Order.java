package com.foodapp.orderservice.domain.aggregate;

import com.foodapp.orderservice.domain.entity.OrderItem;
import com.foodapp.orderservice.domain.entity.OrderStatusHistory;
import com.foodapp.orderservice.domain.enums.*;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.domain.valueobject.Address;
import com.foodapp.orderservice.domain.valueobject.Money;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID restaurantId;

    private UUID cartId;
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    })
    private Money totalAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "delivery_fee_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "delivery_fee_currency"))
    })
    private Money deliveryFee;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "delivery_street")),
            @AttributeOverride(name = "district", column = @Column(name = "delivery_district")),
            @AttributeOverride(name = "city", column = @Column(name = "delivery_city")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "delivery_postal_code")),
            @AttributeOverride(name = "lat", column = @Column(name = "delivery_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "delivery_lng"))
    })
    private Address deliveryAddress;

    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    private String cancelReason;

    @Enumerated(EnumType.STRING)
    private OrderCancellationReason cancellationReason;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String notes;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime paymentTimeoutAt;
    private LocalDateTime restaurantTimeoutAt;

    @Column(unique = true)
    private String idempotencyKey;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = OrderStatus.CREATED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void transitionTo(OrderStatus newStatus, OrderStateMachine stateMachine, String changedBy, String reason) {
        stateMachine.validate(this.status, newStatus);
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(this.id)
                .fromStatus(this.status)
                .toStatus(newStatus)
                .changedAt(LocalDateTime.now())
                .changedBy(changedBy)
                .reason(reason)
                .build();
        this.statusHistory.add(history);
        this.status = newStatus;
    }

    public void cancel(OrderStateMachine stateMachine, OrderCancellationReason reason, String cancelReason, String changedBy) {
        transitionTo(OrderStatus.CANCELLED, stateMachine, changedBy, cancelReason);
        this.cancellationReason = reason;
        this.cancelReason = cancelReason;
    }

    public boolean isRefundRequired() {
        return paymentStatus == PaymentStatus.PAID;
    }

    public void markPaymentInitiated(UUID paymentId, LocalDateTime timeoutAt) {
        this.paymentId = paymentId;
        this.paymentTimeoutAt = timeoutAt;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    public void markPaymentCompleted(UUID paymentId) {
        this.paymentId = paymentId;
        this.paymentStatus = PaymentStatus.PAID;
    }

    public void markPaymentFailed() {
        this.paymentStatus = PaymentStatus.FAILED;
    }

    public void markRefunded() {
        this.paymentStatus = PaymentStatus.REFUNDED;
    }

    public boolean isCancellable() {
        return Set.of(
                OrderStatus.CREATED,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAID,
                OrderStatus.CONFIRMED_BY_RESTAURANT
        ).contains(this.status);
    }
}
