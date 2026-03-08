package com.foodapp.orderservice.application;

import com.foodapp.orderservice.application.cart.CheckoutUseCase;
import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.domain.enums.OrderType;
import com.foodapp.orderservice.domain.enums.PaymentMethod;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.AddressRequest;
import com.foodapp.orderservice.dto.request.CheckoutRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.gateway.PaymentGateway;
import com.foodapp.orderservice.gateway.RestaurantGateway;
import com.foodapp.orderservice.repository.CartRepository;
import com.foodapp.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock PaymentGateway paymentGateway;
    @Mock OrderStateMachine stateMachine;
    @Mock OrderEventPublisher eventPublisher;
    @InjectMocks CheckoutUseCase checkoutUseCase;

    private UUID userId;

    @BeforeEach void setUp() { userId = UUID.randomUUID(); }

    @Test
    void shouldThrowWhenNoActiveCart() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.empty());
        var req = new CheckoutRequest(new AddressRequest("S","D","C","34000",41.0,29.0),
                PaymentMethod.CREDIT_CARD, OrderType.DELIVERY, null);
        assertThatThrownBy(() -> checkoutUseCase.execute(userId, UUID.randomUUID().toString(), null, req))
                .isInstanceOf(CartNotFoundException.class);
    }
}
