package com.foodapp.orderservice.controller;

import com.foodapp.orderservice.application.cart.*;
import com.foodapp.orderservice.config.jwt.AuthenticatedUser;
import com.foodapp.orderservice.dto.request.*;
import com.foodapp.orderservice.dto.response.CartResponse;
import com.foodapp.orderservice.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final AddItemToCartUseCase addItemUseCase;
    private final RemoveItemFromCartUseCase removeItemUseCase;
    private final UpdateCartItemUseCase updateItemUseCase;
    private final GetCartUseCase getCartUseCase;
    private final ClearCartUseCase clearCartUseCase;
    private final CheckoutUseCase checkoutUseCase;

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return getCartUseCase.execute(user.userId());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody AddCartItemRequest request) {
        return addItemUseCase.execute(user.userId(), request);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable UUID itemId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return updateItemUseCase.execute(user.userId(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@AuthenticationPrincipal AuthenticatedUser user,
                           @PathVariable UUID itemId) {
        removeItemUseCase.execute(user.userId(), itemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@AuthenticationPrincipal AuthenticatedUser user) {
        clearCartUseCase.execute(user.userId());
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                  @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
                                  @Valid @RequestBody CheckoutRequest request) {
        return checkoutUseCase.execute(user.userId(), idempotencyKey, correlationId, request);
    }
}
