package com.foodapp.orderservice.support;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.entity.CartItem;
import com.foodapp.orderservice.domain.entity.OrderItem;
import com.foodapp.orderservice.domain.enums.*;
import com.foodapp.orderservice.domain.valueobject.Address;
import com.foodapp.orderservice.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TestFixtures {

    public static final Money UNIT_PRICE = Money.of(new BigDecimal("50.00"), "TRY");
    public static final Money DELIVERY_FEE = Money.of(new BigDecimal("15.00"), "TRY");
    public static final Money TOTAL_AMOUNT = Money.of(new BigDecimal("115.00"), "TRY");
    public static final Address ADDRESS = new Address("Test Sokak", "Beşiktaş", "İstanbul", "34000", 41.0, 29.0);

    public static Order buildOrder(UUID userId, UUID restaurantId, OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .restaurantId(restaurantId)
                .status(status)
                .orderType(OrderType.DELIVERY)
                .totalAmount(TOTAL_AMOUNT)
                .deliveryFee(DELIVERY_FEE)
                .deliveryAddress(ADDRESS)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .idempotencyKey(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(buildOrderItem())))
                .build();
    }

    public static Order buildOrderWithPaymentStatus(UUID userId, UUID restaurantId,
                                                     OrderStatus status, PaymentStatus paymentStatus) {
        Order order = buildOrder(userId, restaurantId, status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    public static OrderItem buildOrderItem() {
        return OrderItem.builder()
                .id(UUID.randomUUID())
                .menuItemId(UUID.randomUUID())
                .menuItemName("Test Burger")
                .unitPrice(UNIT_PRICE)
                .quantity(2)
                .totalPrice(Money.of(new BigDecimal("100.00"), "TRY"))
                .build();
    }

    public static Cart buildCart(UUID userId, UUID restaurantId) {
        return Cart.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .restaurantId(restaurantId)
                .status(CartStatus.ACTIVE)
                .items(new ArrayList<>())
                .build();
    }

    public static CartItem buildCartItem(UUID menuItemId) {
        CartItem item = CartItem.builder()
                .id(UUID.randomUUID())
                .menuItemId(menuItemId)
                .menuItemName("Test Ürün")
                .unitPrice(UNIT_PRICE)
                .quantity(2)
                .totalPrice(Money.of(new BigDecimal("100.00"), "TRY"))
                .build();
        return item;
    }
}
