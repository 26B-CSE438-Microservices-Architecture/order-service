package com.foodapp.orderservice.repository;

import com.foodapp.orderservice.domain.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {}
