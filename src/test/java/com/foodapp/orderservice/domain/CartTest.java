package com.foodapp.orderservice.domain;

import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.entity.CartItem;
import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CartTest {

    private Cart cart;
    private final UUID userId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cart = TestFixtures.buildCart(userId, restaurantId);
    }

    @Test
    void shouldAddNewItem() {
        CartItem item = TestFixtures.buildCartItem(UUID.randomUUID());
        cart.addItem(item);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void shouldAccumulateQuantityWhenSameMenuItemAdded() {
        UUID menuItemId = UUID.randomUUID();
        CartItem item1 = TestFixtures.buildCartItem(menuItemId); // quantity=2
        CartItem item2 = TestFixtures.buildCartItem(menuItemId); // quantity=2
        cart.addItem(item1);
        cart.addItem(item2);

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(4);
    }

    @Test
    void shouldKeepSeparateItemsForDifferentMenuIds() {
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        assertThat(cart.getItems()).hasSize(2);
    }

    @Test
    void shouldRemoveItemById() {
        CartItem item = TestFixtures.buildCartItem(UUID.randomUUID());
        cart.addItem(item);
        cart.removeItem(item.getMenuItemId());
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void shouldClearAllItems() {
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        cart.clear();
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void shouldTransitionToCheckedOutStatus() {
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        cart.checkout();
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }

    @Test
    void shouldThrowWhenCheckingOutEmptyCart() {
        assertThatThrownBy(() -> cart.checkout())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenModifyingCheckedOutCart() {
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        cart.checkout();

        assertThatThrownBy(() -> cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void shouldCalculateTotalAmountCorrectly() {
        // Her item: unitPrice=50, quantity=2 → totalPrice=100
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        cart.addItem(TestFixtures.buildCartItem(UUID.randomUUID()));
        assertThat(cart.getTotalAmount().getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void shouldReturnZeroForEmptyCart() {
        assertThat(cart.getTotalAmount().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
