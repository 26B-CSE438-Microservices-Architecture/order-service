package com.foodapp.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodapp.orderservice.config.InternalSecretFilter;
import com.foodapp.orderservice.config.SecurityConfig;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.PaymentCallbackRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.GlobalExceptionHandler;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalOrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, OrderStateMachine.class})
@TestPropertySource(properties = {
        "internal.secret=test-internal-secret",
        "jwt.secret=test-secret-key-for-testing-purposes-only-256-bits-long"
})
class InternalOrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired InternalSecretFilter internalSecretFilter;

    @MockBean OrderRepository orderRepository;
    @MockBean OrderEventPublisher eventPublisher;

    private UUID userId;
    private UUID restaurantId;
    private UUID orderId;
    private String secret;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        // Force the exact value into the filter regardless of property loading order
        ReflectionTestUtils.setField(internalSecretFilter, "internalSecret", "test-internal-secret");
        secret = "test-internal-secret";
    }

    @Test
    void shouldTransitionToPaymentHeldOnHoldConfirmed() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(UUID.randomUUID(), "HOLD_CONFIRMED", null))))
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

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(null, "HOLD_FAILED", "Yetersiz bakiye"))))
                .andExpect(status().isOk());

        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldTransitionToPaidOnCaptureCompleted() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_CAPTURE_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(UUID.randomUUID(), "CAPTURE_COMPLETED", null))))
                .andExpect(status().isOk());

        verify(eventPublisher).publishOrderConfirmed(order);
    }

    @Test
    void shouldCancelOrderOnCaptureFailed() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_CAPTURE_PENDING);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(null, "CAPTURE_FAILED", "Sistem hatasi"))))
                .andExpect(status().isOk());

        verify(eventPublisher).publishOrderCancelled(order);
    }

    @Test
    void shouldHandleHoldReleasedWithoutPublishing() throws Exception {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.CANCELLED);
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(null, "HOLD_RELEASED", null))))
                .andExpect(status().isOk());

        verify(orderRepository).save(order);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(UUID.randomUUID(), "HOLD_CONFIRMED", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenSecretIsMissing() throws Exception {
        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentCallbackRequest(UUID.randomUUID(), "HOLD_CONFIRMED", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn400WhenStatusIsBlank() throws Exception {
        mockMvc.perform(post("/internal/orders/{id}/payment-callback", orderId)
                        .header("X-Internal-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
