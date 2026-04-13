package com.foodapp.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodapp.orderservice.application.order.*;
import com.foodapp.orderservice.config.InternalSecretFilter;
import com.foodapp.orderservice.config.SecurityConfig;
import com.foodapp.orderservice.config.jwt.AuthenticatedUser;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.OrderType;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.enums.UserRole;
import com.foodapp.orderservice.dto.request.CancelOrderRequest;
import com.foodapp.orderservice.dto.response.*;
import com.foodapp.orderservice.exception.GlobalExceptionHandler;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.support.JwtTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-testing-purposes-only-256-bits-long",
        "internal.secret=test-internal-secret"
})
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired InternalSecretFilter internalSecretFilter;

    @MockBean GetOrderUseCase getOrderUseCase;
    @MockBean ListOrdersUseCase listOrdersUseCase;
    @MockBean CancelOrderUseCase cancelOrderUseCase;
    @MockBean ReorderUseCase reorderUseCase;
    @MockBean RequestRefundUseCase requestRefundUseCase;

    private UUID userId;
    private UUID orderId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerToken = JwtTestHelper.bearerToken(userId, UserRole.CUSTOMER);
    }

    @Test
    void shouldReturnOrderById() throws Exception {
        var response = buildOrderResponse(orderId, OrderStatus.PAID);
        when(getOrderUseCase.execute(orderId, userId, UserRole.CUSTOMER)).thenReturn(response);

        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(getOrderUseCase.execute(any(), any(), any()))
                .thenThrow(new OrderNotFoundException("Order not found"));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("Authorization", customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn403WhenOrderDoesNotBelongToUser() throws Exception {
        when(getOrderUseCase.execute(any(), any(), any()))
                .thenThrow(new OrderNotBelongToUserException("Access denied"));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .header("Authorization", customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnMyOrders() throws Exception {
        var page = new PageResponse<>(
                List.of(buildOrderResponse(orderId, OrderStatus.DELIVERED)), 1, 1, 0, 10);
        when(listOrdersUseCase.forCustomer(userId, null, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/orders/my")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));
    }

    @Test
    void shouldFilterMyOrdersByStatus() throws Exception {
        var page = new PageResponse<>(
                List.of(buildOrderResponse(orderId, OrderStatus.DELIVERED)), 1, 1, 0, 10);
        when(listOrdersUseCase.forCustomer(userId, OrderStatus.DELIVERED, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/orders/my")
                        .param("status", "DELIVERED")
                        .header("Authorization", customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));
    }

    @Test
    void shouldCancelOrder() throws Exception {
        doNothing().when(cancelOrderUseCase).execute(any(), any(), any(), any());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .header("Authorization", customerToken)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelOrderRequest("Yanlış sipariş"))))
                .andExpect(status().isOk());

        verify(cancelOrderUseCase).execute(eq(orderId), eq(userId), eq(UserRole.CUSTOMER), any());
    }

    @Test
    void shouldCancelOrderWithoutBody() throws Exception {
        doNothing().when(cancelOrderUseCase).execute(any(), any(), any(), any());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .header("Authorization", customerToken)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenCancellingUncancellableOrder() throws Exception {
        doThrow(new IllegalStateException("Order cannot be cancelled"))
                .when(cancelOrderUseCase).execute(any(), any(), any(), any());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .header("Authorization", customerToken)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReorder() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        var cartResponse = new CartResponse(UUID.randomUUID(), restaurantId, null, List.of(), null);
        when(reorderUseCase.execute(orderId, userId)).thenReturn(cartResponse);

        mockMvc.perform(post("/orders/{id}/reorder", orderId)
                        .header("Authorization", customerToken)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()));
    }

    @Test
    void shouldRequestRefundAndReturn204() throws Exception {
        doNothing().when(requestRefundUseCase).execute(orderId, userId);

        mockMvc.perform(post("/orders/{id}/request-refund", orderId)
                        .header("Authorization", customerToken)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(requestRefundUseCase).execute(orderId, userId);
    }

    @Test
    void shouldReturn4xxWhenNotAuthenticated() throws Exception {
        // Spring Security returns 403 (no auth entry point configured) when no JWT is present
        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().is4xxClientError());
    }

    private OrderResponse buildOrderResponse(UUID orderId, OrderStatus status) {
        return new OrderResponse(
                orderId, status, OrderType.DELIVERY,
                UUID.randomUUID(),
                new MoneyResponse(new BigDecimal("100.00"), "TRY"),
                new MoneyResponse(new BigDecimal("15.00"), "TRY"),
                new AddressResponse("Sokak", "Beşiktaş", "İstanbul", "34000", 41.0, 29.0),
                null, PaymentStatus.PENDING, null,
                null, LocalDateTime.now(),
                List.of(), List.of()
        );
    }
}
