package com.foodapp.orderservice.repository;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.*;
import com.foodapp.orderservice.domain.valueobject.Address;
import com.foodapp.orderservice.domain.valueobject.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired OrderRepository orderRepository;

    @Test
    void shouldFindByIdempotencyKey() {
        String key = UUID.randomUUID().toString();
        var order = buildOrder(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.PAYMENT_PENDING);
        order.setIdempotencyKey(key);
        orderRepository.save(order);

        var found = orderRepository.findByIdempotencyKey(key);
        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    void shouldReturnEmptyForUnknownIdempotencyKey() {
        assertThat(orderRepository.findByIdempotencyKey("bilinmeyen-key")).isEmpty();
    }

    @Test
    void shouldFindByUserIdWithPagination() {
        UUID userId = UUID.randomUUID();
        orderRepository.save(buildOrder(userId, UUID.randomUUID(), OrderStatus.DELIVERED));
        orderRepository.save(buildOrder(userId, UUID.randomUUID(), OrderStatus.CANCELLED));
        orderRepository.save(buildOrder(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.DELIVERED)); // farklı kullanıcı

        var page = orderRepository.findByUserId(userId, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void shouldFindByUserIdAndStatus() {
        UUID userId = UUID.randomUUID();
        orderRepository.save(buildOrder(userId, UUID.randomUUID(), OrderStatus.DELIVERED));
        orderRepository.save(buildOrder(userId, UUID.randomUUID(), OrderStatus.CANCELLED));

        var page = orderRepository.findByUserIdAndStatus(userId, OrderStatus.DELIVERED, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void shouldFindTimedOutPaymentOrders() {
        UUID userId = UUID.randomUUID();
        var timedOut = buildOrder(userId, UUID.randomUUID(), OrderStatus.PAYMENT_PENDING);
        timedOut.setPaymentTimeoutAt(LocalDateTime.now().minusMinutes(5)); // geçmişte
        orderRepository.save(timedOut);

        var notTimedOut = buildOrder(userId, UUID.randomUUID(), OrderStatus.PAYMENT_PENDING);
        notTimedOut.setPaymentTimeoutAt(LocalDateTime.now().plusMinutes(5)); // gelecekte
        orderRepository.save(notTimedOut);

        var results = orderRepository.findByStatusAndPaymentTimeoutAtBefore(
                OrderStatus.PAYMENT_PENDING, LocalDateTime.now());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(timedOut.getId());
    }

    @Test
    void shouldFindTimedOutRestaurantOrders() {
        UUID userId = UUID.randomUUID();
        var timedOut = buildOrder(userId, UUID.randomUUID(), OrderStatus.PAYMENT_HELD);
        timedOut.setRestaurantTimeoutAt(LocalDateTime.now().minusMinutes(1));
        orderRepository.save(timedOut);

        var results = orderRepository.findByStatusAndRestaurantTimeoutAtBefore(
                OrderStatus.PAYMENT_HELD, LocalDateTime.now());

        assertThat(results).hasSize(1);
    }

    @Test
    void shouldFindByRestaurantId() {
        UUID restaurantId = UUID.randomUUID();
        orderRepository.save(buildOrder(UUID.randomUUID(), restaurantId, OrderStatus.PAID));
        orderRepository.save(buildOrder(UUID.randomUUID(), restaurantId, OrderStatus.PREPARING));
        orderRepository.save(buildOrder(UUID.randomUUID(), UUID.randomUUID(), OrderStatus.PAID)); // farklı restoran

        var page = orderRepository.findByRestaurantId(restaurantId, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(2);
    }

    private Order buildOrder(UUID userId, UUID restaurantId, OrderStatus status) {
        return Order.builder()
                .userId(userId)
                .restaurantId(restaurantId)
                .status(status)
                .orderType(OrderType.DELIVERY)
                .totalAmount(Money.of(new BigDecimal("115.00"), "TRY"))
                .deliveryFee(Money.of(new BigDecimal("15.00"), "TRY"))
                .deliveryAddress(new Address("Sokak", "Beşiktaş", "İstanbul", "34000", 41.0, 29.0))
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .idempotencyKey(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }
}
