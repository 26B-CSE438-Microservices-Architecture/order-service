package com.foodapp.orderservice.repository;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);
    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);
    Page<Order> findByRestaurantId(UUID restaurantId, Pageable pageable);
    Page<Order> findByRestaurantIdAndStatus(UUID restaurantId, OrderStatus status, Pageable pageable);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    List<Order> findByStatusAndPaymentTimeoutAtBefore(OrderStatus status, LocalDateTime now);
    List<Order> findByStatusAndRestaurantTimeoutAtBefore(OrderStatus status, LocalDateTime now);
}
