package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.UserRole;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetOrderUseCaseTest {

    @Mock OrderRepository orderRepository;
    @InjectMocks GetOrderUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void shouldThrowWhenOrderNotFound() {
        when(orderRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), userId, UserRole.CUSTOMER))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void shouldReturnOrderForOwner() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        var response = useCase.execute(order.getId(), userId, UserRole.CUSTOMER);

        assertThat(response.orderId()).isEqualTo(order.getId());
    }

    @Test
    void shouldThrowWhenCustomerAccessesOtherOrder() {
        UUID ownerUserId = UUID.randomUUID();
        var order = TestFixtures.buildOrder(ownerUserId, restaurantId, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UUID anotherUserId = UUID.randomUUID();
        assertThatThrownBy(() -> useCase.execute(order.getId(), anotherUserId, UserRole.CUSTOMER))
                .isInstanceOf(OrderNotBelongToUserException.class);
    }

    @Test
    void shouldAllowAdminToAccessAnyOrder() {
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        UUID adminId = UUID.randomUUID();
        assertThatNoException().isThrownBy(() ->
                useCase.execute(order.getId(), adminId, UserRole.ADMIN));
    }

    @Test
    void shouldAllowRestaurantOwnerToSeeOwnRestaurantOrder() {
        // restaurantId == userId convention (JWT sub = restaurant UUID)
        var order = TestFixtures.buildOrder(userId, restaurantId, OrderStatus.PAYMENT_HELD);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // restaurantId'yi userId olarak geçiriyoruz (convention gereği)
        assertThatNoException().isThrownBy(() ->
                useCase.execute(order.getId(), restaurantId, UserRole.RESTAURANT_OWNER));
    }
}
