package com.foodapp.orderservice.controller;

import com.foodapp.orderservice.application.order.*;
import com.foodapp.orderservice.config.jwt.AuthenticatedUser;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.dto.request.CancelOrderRequest;
import com.foodapp.orderservice.dto.response.CartResponse;
import com.foodapp.orderservice.dto.response.OrderResponse;
import com.foodapp.orderservice.dto.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final GetOrderUseCase getOrderUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final ReorderUseCase reorderUseCase;

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return getOrderUseCase.execute(orderId, user.userId(), user.role());
    }

    @GetMapping("/my")
    public PageResponse<OrderResponse> myOrders(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam(required = false) OrderStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return listOrdersUseCase.forCustomer(user.userId(), status, page, size);
    }

    @PostMapping("/{orderId}/cancel")
    public void cancelOrder(@PathVariable UUID orderId,
                             @AuthenticationPrincipal AuthenticatedUser user,
                             @RequestBody(required = false) CancelOrderRequest request) {
        cancelOrderUseCase.execute(orderId, user.userId(), user.role(),
                request != null ? request : new CancelOrderRequest(null));
    }

    @PostMapping("/{orderId}/reorder")
    public CartResponse reorder(@PathVariable UUID orderId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return reorderUseCase.execute(orderId, user.userId());
    }
}
