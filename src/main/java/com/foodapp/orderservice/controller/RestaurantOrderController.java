package com.foodapp.orderservice.controller;

import com.foodapp.orderservice.application.order.*;
import com.foodapp.orderservice.config.jwt.AuthenticatedUser;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.dto.request.ConfirmOrderRequest;
import com.foodapp.orderservice.dto.request.RejectOrderRequest;
import com.foodapp.orderservice.dto.request.UpdateOrderStatusRequest;
import com.foodapp.orderservice.dto.response.OrderResponse;
import com.foodapp.orderservice.dto.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders/restaurant")
@RequiredArgsConstructor
public class RestaurantOrderController {

    private final ListOrdersUseCase listOrdersUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final RejectOrderUseCase rejectOrderUseCase;
    private final UpdateOrderStatusUseCase updateStatusUseCase;

    @GetMapping
    public PageResponse<OrderResponse> getRestaurantOrders(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @RequestParam(required = false) OrderStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return listOrdersUseCase.forRestaurant(user.userId(), status, page, size);
    }

    @PatchMapping("/{orderId}/confirm")
    public void confirm(@PathVariable UUID orderId,
                        @AuthenticationPrincipal AuthenticatedUser user,
                        @RequestBody(required = false) ConfirmOrderRequest request) {
        confirmOrderUseCase.execute(orderId, user.userId(),
                request != null ? request : new ConfirmOrderRequest(null));
    }

    @PatchMapping("/{orderId}/reject")
    public void reject(@PathVariable UUID orderId,
                       @AuthenticationPrincipal AuthenticatedUser user,
                       @Valid @RequestBody RejectOrderRequest request) {
        rejectOrderUseCase.execute(orderId, user.userId(), request);
    }

    @PatchMapping("/{orderId}/status")
    public void updateStatus(@PathVariable UUID orderId,
                              @AuthenticationPrincipal AuthenticatedUser user,
                              @Valid @RequestBody UpdateOrderStatusRequest request) {
        updateStatusUseCase.execute(orderId, user.userId(), request);
    }
}
