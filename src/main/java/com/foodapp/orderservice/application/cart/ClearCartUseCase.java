package com.foodapp.orderservice.application.cart;

import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClearCartUseCase {

    private final CartRepository cartRepository;

    @Transactional
    public void execute(UUID userId) {
        var cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Active cart not found"));
        cart.clear();
        cartRepository.save(cart);
    }
}
