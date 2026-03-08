package com.foodapp.orderservice.application.cart;

import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.dto.response.CartResponse;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCartUseCase {

    private final CartRepository cartRepository;

    @Transactional(readOnly = true)
    public CartResponse execute(UUID userId) {
        return cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .map(CartResponse::from)
                .orElseThrow(() -> new CartNotFoundException("Active cart not found"));
    }
}
