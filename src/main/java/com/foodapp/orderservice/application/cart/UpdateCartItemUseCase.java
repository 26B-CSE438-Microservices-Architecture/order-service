package com.foodapp.orderservice.application.cart;

import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.dto.request.UpdateCartItemRequest;
import com.foodapp.orderservice.dto.response.CartResponse;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateCartItemUseCase {

    private final CartRepository cartRepository;

    @Transactional
    public CartResponse execute(UUID userId, UUID itemId, UpdateCartItemRequest request) {
        var cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("Active cart not found"));
        cart.updateItemQuantity(itemId, request.quantity());
        return CartResponse.from(cartRepository.save(cart));
    }
}
