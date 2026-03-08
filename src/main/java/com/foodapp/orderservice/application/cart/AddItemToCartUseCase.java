package com.foodapp.orderservice.application.cart;

import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.entity.CartItem;
import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.domain.valueobject.Money;
import com.foodapp.orderservice.dto.request.AddCartItemRequest;
import com.foodapp.orderservice.dto.response.CartResponse;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.gateway.RestaurantGateway;
import com.foodapp.orderservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddItemToCartUseCase {

    private final CartRepository cartRepository;
    private final RestaurantGateway restaurantGateway;

    @Transactional
    public CartResponse execute(UUID userId, AddCartItemRequest request) {
        // Validate item with restaurant service
        var validation = restaurantGateway.validateOrderItems(
                request.restaurantId(),
                List.of(new RestaurantGateway.OrderItemRequest(request.menuItemId(), request.quantity()))
        );

        if (!validation.valid()) {
            throw new IllegalArgumentException("Item validation failed: " + validation.errorMessage());
        }

        var validatedItem = validation.items().get(0);
        if (!validatedItem.available()) {
            throw new IllegalArgumentException("Item is not available: " + validatedItem.name());
        }

        // Get or create active cart
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseGet(() -> Cart.builder()
                        .userId(userId)
                        .restaurantId(request.restaurantId())
                        .status(CartStatus.ACTIVE)
                        .build());

        // Validate same restaurant
        if (cart.getRestaurantId() != null && !cart.getRestaurantId().equals(request.restaurantId())) {
            throw new IllegalArgumentException("Cannot add items from different restaurants");
        }

        Money unitPrice = Money.of(validatedItem.price(), "TRY");
        CartItem item = CartItem.builder()
                .cartId(cart.getId())
                .menuItemId(request.menuItemId())
                .menuItemName(validatedItem.name())
                .unitPrice(unitPrice)
                .quantity(request.quantity())
                .totalPrice(unitPrice.add(Money.zero("TRY")))
                .specialInstructions(request.specialInstructions())
                .build();
        item.recalculateTotal();

        cart.addItem(item);
        return CartResponse.from(cartRepository.save(cart));
    }
}
