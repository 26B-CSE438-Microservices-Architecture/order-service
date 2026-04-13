package com.foodapp.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodapp.orderservice.application.order.*;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean GetOrderUseCase getOrderUseCase;
    @MockBean ListOrdersUseCase listOrdersUseCase;
    @MockBean CancelOrderUseCase cancelOrderUseCase;
    @MockBean ReorderUseCase reorderUseCase;
    @MockBean RequestRefundUseCase requestRefundUseCase;

    // These beans are required by the security filter chain loaded in @WebMvcTest
    @MockBean com.foodapp.orderservice.config.jwt.JwtAuthenticationFilter jwtFilter;
    @MockBean com.foodapp.orderservice.config.InternalSecretFilter internalSecretFilter;

    private UUID userId;
    private UUID orderId;
    private Authentication customerAuth;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        var principal = new AuthenticatedUser(userId, UserRole.CUSTOMER);
        customerAuth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    @Test
    void shouldReturnOrderById() throws Exception {
        var response = buildOrderResponse(orderId, userId, OrderStatus.PAID);
        when(getOrderUseCase.execute(orderId, userId, UserRole.CUSTOMER)).thenReturn(response);

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(getOrderUseCase.execute(any(), any(), any()))
                .thenThrow(new OrderNotFoundException("Order not found"));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn403WhenOrderDoesNotBelongToUser() throws Exception {
        when(getOrderUseCase.execute(any(), any(), any()))
                .thenThrow(new OrderNotBelongToUserException("Access denied"));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnMyOrders() throws Exception {
        var page = new PageResponse<>(List.of(buildOrderResponse(orderId, userId, OrderStatus.DELIVERED)), 0, 10, 1, 1);
        when(listOrdersUseCase.forCustomer(userId, null, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/orders/my")
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));
    }

    @Test
    void shouldFilterMyOrdersByStatus() throws Exception {
        var page = new PageResponse<>(List.of(buildOrderResponse(orderId, userId, OrderStatus.DELIVERED)), 0, 10, 1, 1);
        when(listOrdersUseCase.forCustomer(userId, OrderStatus.DELIVERED, 0, 10)).thenReturn(page);

        mockMvc.perform(get("/orders/my")
                        .param("status", "DELIVERED")
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DELIVERED"));
    }

    @Test
    void shouldCancelOrder() throws Exception {
        var request = new CancelOrderRequest("Yanlış sipariş");
        doNothing().when(cancelOrderUseCase).execute(any(), any(), any(), any());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .with(authentication(customerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(cancelOrderUseCase).execute(eq(orderId), eq(userId), eq(UserRole.CUSTOMER), any());
    }

    @Test
    void shouldCancelOrderWithoutBody() throws Exception {
        doNothing().when(cancelOrderUseCase).execute(any(), any(), any(), any());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenCancellingUncancellableOrder() throws Exception {
        doThrow(new IllegalStateException("Order cannot be cancelled"))
                .when(cancelOrderUseCase).execute(any(), any(), any(), any());

        mockMvc.perform(post("/orders/{id}/cancel", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReorder() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        var cartResponse = new CartResponse(UUID.randomUUID(), restaurantId, null, List.of(), null);
        when(reorderUseCase.execute(orderId, userId)).thenReturn(cartResponse);

        mockMvc.perform(post("/orders/{id}/reorder", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()));
    }

    @Test
    void shouldRequestRefundAndReturn204() throws Exception {
        doNothing().when(requestRefundUseCase).execute(orderId, userId);

        mockMvc.perform(post("/orders/{id}/request-refund", orderId)
                        .with(authentication(customerAuth)))
                .andExpect(status().isNoContent());

        verify(requestRefundUseCase).execute(orderId, userId);
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/orders/{id}", orderId))
                .andExpect(status().isUnauthorized());
    }

    private OrderResponse buildOrderResponse(UUID orderId, UUID userId, OrderStatus status) {
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
