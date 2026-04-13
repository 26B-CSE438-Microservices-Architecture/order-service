package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.CartRepository;
import com.foodapp.orderservice.repository.OrderRepository;
import com.foodapp.orderservice.support.TestFixtures;
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
class ReorderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @Mock CartRepository cartRepository;
    @InjectMocks ReorderUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), userId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldThrowWhenOrderBelongsToDifferentUser() {
        var order = TestFixtures.buildOrder(UUID.randomUUID(), restaurantId, OrderStatus.DELIVERED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> useCase.execute(order.getId(), userId))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }

    @Test
    void shouldCreateNewCartFromPreviousOrderItems() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.DELIVERED);
        var newCart = TestFixtures.buildCart(userId, restaurantId);

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(cartRepository.save(any())).thenReturn(newCart);

        var response = useCase.execute(order.getId(), userId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepository).save(any());
    }
}
