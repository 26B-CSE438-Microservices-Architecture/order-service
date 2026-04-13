package com.foodapp.orderservice.application;

import com.foodapp.orderservice.application.cart.CheckoutUseCase;
import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.enums.*;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.AddressRequest;
import com.foodapp.orderservice.dto.request.CheckoutRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.gateway.RestaurantGateway;
import com.foodapp.orderservice.repository.CartRepository;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutUseCaseTest {

    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock RestaurantGateway restaurantGateway;
    @Mock OrderStateMachine stateMachine;
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks CheckoutUseCase checkoutUseCase;

    private UUID userId;
    private UUID restaurantId;
    private static final CheckoutRequest VALID_REQUEST = new CheckoutRequest(
            new AddressRequest("Sokak", "Beşiktaş", "İstanbul", "34000", 41.0, 29.0),
            PaymentMethod.CREDIT_CARD, OrderType.DELIVERY, null);

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        ReflectionTestUtils.setField(checkoutUseCase, "paymentTimeoutMinutes", 10);
        ReflectionTestUtils.setField(checkoutUseCase, "defaultDeliveryFee", new BigDecimal("15.00"));
    }

    @Test
    void shouldThrowWhenNoActiveCart() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutUseCase.execute(userId, UUID.randomUUID().toString(), null, VALID_REQUEST))
                .isInstanceOf(CartNotFoundException.class);
    }

    @Test
    void shouldThrowWhenCartIsEmpty() {
        Cart emptyCart = TestFixtures.buildCart(userId, restaurantId);
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(emptyCart));

        assertThatThrownBy(() -> checkoutUseCase.execute(userId, UUID.randomUUID().toString(), null, VALID_REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenRestaurantIsClosed() {
        Cart cart = TestFixtures.buildCart(userId, restaurantId);
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));

        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(restaurantGateway.isRestaurantOpen(restaurantId)).thenReturn(false);

        assertThatThrownBy(() -> checkoutUseCase.execute(userId, UUID.randomUUID().toString(), null, VALID_REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void shouldThrowWhenItemValidationFails() {
        Cart cart = TestFixtures.buildCart(userId, restaurantId);
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));

        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(restaurantGateway.isRestaurantOpen(restaurantId)).thenReturn(true);
        when(restaurantGateway.validateOrderItems(any(), any()))
                .thenReturn(new RestaurantGateway.MenuValidationResult(false, List.of(), "Menü güncel değil"));

        assertThatThrownBy(() -> checkoutUseCase.execute(userId, UUID.randomUUID().toString(), null, VALID_REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation failed");
    }

    @Test
    void shouldReturnExistingOrderOnIdempotentRequest() {
        Order existingOrder = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        String idempotencyKey = UUID.randomUUID().toString();
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        var response = checkoutUseCase.execute(userId, idempotencyKey, null, VALID_REQUEST);

        assertThat(response.orderId()).isEqualTo(existingOrder.getId());
        verify(cartRepository, never()).findByUserIdAndStatus(any(), any());
    }

    @Test
    void shouldCreateOrderAndPublishEventOnHappyPath() {
        UUID menuItemId = UUID.randomUUID();
        Cart cart = TestFixtures.buildCart(userId, restaurantId);
        cart.addItem(TestFixtures.buildCartItem(menuItemId));

        Order savedOrder = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);

        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(restaurantGateway.isRestaurantOpen(restaurantId)).thenReturn(true);
        when(restaurantGateway.validateOrderItems(any(), any()))
                .thenReturn(new RestaurantGateway.MenuValidationResult(true,
                        List.of(new RestaurantGateway.ValidatedItem(menuItemId, "Burger", new BigDecimal("50.00"), true)),
                        null));
        when(cartRepository.save(any())).thenReturn(cart);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        var response = checkoutUseCase.execute(userId, UUID.randomUUID().toString(), null, VALID_REQUEST);

        assertThat(response).isNotNull();
        verify(orderRepository).save(any());
        verify(eventPublisher).publishPaymentHoldRequested(any());
    }
}
