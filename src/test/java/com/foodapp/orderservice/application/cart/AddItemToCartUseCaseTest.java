package com.foodapp.orderservice.application.cart;

import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.dto.request.AddCartItemRequest;
import com.foodapp.orderservice.gateway.RestaurantGateway;
import com.foodapp.orderservice.repository.CartRepository;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddItemToCartUseCaseTest {

    @Mock CartRepository cartRepository;
    @Mock RestaurantGateway restaurantGateway;
    @InjectMocks AddItemToCartUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();

    private RestaurantGateway.MenuValidationResult successValidation() {
        return new RestaurantGateway.MenuValidationResult(true,
                List.of(new RestaurantGateway.ValidatedItem(menuItemId, "Test Burger", new BigDecimal("50.00"), true)),
                null);
    }

    @Test
    void shouldCreateNewCartWhenNoneExists() {
        var request = new AddCartItemRequest(menuItemId, restaurantId, 2, null);
        when(restaurantGateway.validateOrderItems(any(), any())).thenReturn(successValidation());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.empty());

        Cart savedCart = TestFixtures.buildCart(userId, restaurantId);
        when(cartRepository.save(any())).thenReturn(savedCart);

        var response = useCase.execute(userId, request);

        assertThat(response).isNotNull();
        verify(cartRepository).save(any());
    }

    @Test
    void shouldAddItemToExistingCart() {
        Cart existingCart = TestFixtures.buildCart(userId, restaurantId);
        var request = new AddCartItemRequest(menuItemId, restaurantId, 1, "Az acı");

        when(restaurantGateway.validateOrderItems(any(), any())).thenReturn(successValidation());
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any())).thenReturn(existingCart);

        useCase.execute(userId, request);

        verify(cartRepository).save(existingCart);
    }

    @Test
    void shouldThrowWhenRestaurantValidationFails() {
        var request = new AddCartItemRequest(menuItemId, restaurantId, 1, null);
        when(restaurantGateway.validateOrderItems(any(), any()))
                .thenReturn(new RestaurantGateway.MenuValidationResult(false, List.of(), "Ürün bulunamadı"));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item validation failed");
    }

    @Test
    void shouldThrowWhenItemIsNotAvailable() {
        var request = new AddCartItemRequest(menuItemId, restaurantId, 1, null);
        when(restaurantGateway.validateOrderItems(any(), any()))
                .thenReturn(new RestaurantGateway.MenuValidationResult(true,
                        List.of(new RestaurantGateway.ValidatedItem(menuItemId, "Burger", new BigDecimal("50.00"), false)),
                        null));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void shouldThrowWhenAddingItemFromDifferentRestaurant() {
        UUID existingRestaurantId = UUID.randomUUID();
        UUID differentRestaurantId = UUID.randomUUID();
        Cart existingCart = TestFixtures.buildCart(userId, existingRestaurantId);

        var request = new AddCartItemRequest(menuItemId, differentRestaurantId, 1, null);
        when(restaurantGateway.validateOrderItems(any(), any()))
                .thenReturn(new RestaurantGateway.MenuValidationResult(true,
                        List.of(new RestaurantGateway.ValidatedItem(menuItemId, "Pizza", new BigDecimal("80.00"), true)),
                        null));
        when(cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)).thenReturn(Optional.of(existingCart));

        assertThatThrownBy(() -> useCase.execute(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different restaurants");
    }
}
