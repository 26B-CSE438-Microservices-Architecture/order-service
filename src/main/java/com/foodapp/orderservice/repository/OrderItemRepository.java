package com.foodapp.orderservice.repository;

import com.foodapp.orderservice.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {}
