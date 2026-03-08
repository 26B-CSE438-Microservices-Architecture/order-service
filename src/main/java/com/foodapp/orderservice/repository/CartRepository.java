package com.foodapp.orderservice.repository;

import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);
}
