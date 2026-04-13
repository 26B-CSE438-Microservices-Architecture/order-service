package com.foodapp.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.PaymentCallbackRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.GlobalExceptionHandler;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalOrderController.class)
@Import(GlobalExceptionHandler.class)
class InternalOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OrderRepository orderRepository;
    @SpyBean OrderStateMachine stateMachine;
    @MockBean OrderEventPublisher eventPublisher;

    // Required by SecurityFilterChain — mocked to be no-ops so tests focus on controller
    @MockBean com.foodapp.orderservice.config.jwt.JwtAuthenticationFilter jwtFilter;
    @MockBean com.foodapp.orderservice.config.InternalSecretFilter internalSecretFilter;

    private UUID userId;
    private UUID restaurantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    void shouldTransitionToPaymentHeldOnHoldConfirmed() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        var request = new PaymentCallbackRequest(UUID.randomUUID(), "HOLD_CONFIRMED", null);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(eventPublisher).publishRestaurantApprovalRequested(order);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldTransitionToCancelledOnHoldFailed() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        var request = new PaymentCallbackRequest(null, "HOLD_FAILED", "Yetersiz bakiye");

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldTransitionToPaidOnCaptureCompleted() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_CAPTURE_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        var request = new PaymentCallbackRequest(UUID.randomUUID(), "CAPTURE_COMPLETED", null);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(eventPublisher).publishOrderConfirmed(order);
    }

    @Test
    void shouldCancelOrderOnCaptureFailed() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_CAPTURE_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        var request = new PaymentCallbackRequest(null, "CAPTURE_FAILED", "Ödeme sistemi hatası");

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldHandleHoldReleasedWithoutPublishing() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.CANCELLED);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        var request = new PaymentCallbackRequest(null, "HOLD_RELEASED", null);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(orderRepository).save(order);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        var request = new PaymentCallbackRequest(UUID.randomUUID(), "HOLD_CONFIRMED", null);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenStatusIsMissing() throws Exception {
        // @NotBlank on status field — empty string triggers validation
        String invalidJson = "{\"status\":\"\"}";

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
