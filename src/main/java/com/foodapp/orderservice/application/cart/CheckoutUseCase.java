package com.foodapp.orderservice.application.cart;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.entity.Cart;
import com.foodapp.orderservice.domain.entity.OrderItem;
import com.foodapp.orderservice.domain.enums.CartStatus;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.domain.valueobject.Address;
import com.foodapp.orderservice.domain.valueobject.Money;
import com.foodapp.orderservice.dto.request.CheckoutRequest;
import com.foodapp.orderservice.dto.response.OrderResponse;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.CartNotFoundException;
import com.foodapp.orderservice.gateway.PaymentGateway;
import com.foodapp.orderservice.gateway.RestaurantGateway;
import com.foodapp.orderservice.repository.CartRepository;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutUseCase {

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final RestaurantGateway restaurantGateway;
    private final PaymentGateway paymentGateway;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Value("${order.payment-timeout-minutes:10}")
    private int paymentTimeoutMinutes;

    @Value("${order.restaurant-timeout-minutes:5}")
    private int restaurantTimeoutMinutes;

    @Value("${order.delivery-fee:15.00}")
    private BigDecimal defaultDeliveryFee;

    @Transactional
    public OrderResponse execute(UUID userId, String idempotencyKey, String correlationId, CheckoutRequest request) {
        // Idempotency check
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(OrderResponse::from)
                .orElseGet(() -> doCheckout(userId, idempotencyKey, correlationId, request));
    }

    private OrderResponse doCheckout(UUID userId, String idempotencyKey, String correlationId, CheckoutRequest request) {
        // 1. Get active cart
        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new CartNotFoundException("No active cart found"));
        if (cart.getItems().isEmpty()) throw new IllegalStateException("Cart is empty");

        // 2. Validate restaurant is open
        if (!restaurantGateway.isRestaurantOpen(cart.getRestaurantId())) {
            throw new IllegalStateException("Restaurant is currently closed");
        }

        // 3. Validate items and get fresh prices
        var itemRequests = cart.getItems().stream()
                .map(i -> new RestaurantGateway.OrderItemRequest(i.getMenuItemId(), i.getQuantity()))
                .toList();
        var validation = restaurantGateway.validateOrderItems(cart.getRestaurantId(), itemRequests);
        if (!validation.valid())
            throw new IllegalArgumentException("Order validation failed: " + validation.errorMessage());

        // 4. Build order items with validated prices (snapshot)
        Money deliveryFee = Money.of(defaultDeliveryFee, "TRY"); // 1. Maddede eklediğimiz BigDecimal kullanılıyor

        var orderItems = cart.getItems().stream().map(cartItem -> {
            var validated = validation.items().stream()
                    .filter(v -> v.menuItemId().equals(cartItem.getMenuItemId()))
                    .findFirst().orElseThrow();
            Money unitPrice = Money.of(validated.price(), "TRY");
            Money totalPrice = Money.of(validated.price().multiply(BigDecimal.valueOf(cartItem.getQuantity())), "TRY");

            return OrderItem.builder()
                    .menuItemId(cartItem.getMenuItemId())
                    .menuItemName(validated.name())
                    .unitPrice(unitPrice)
                    .quantity(cartItem.getQuantity())
                    .totalPrice(totalPrice)
                    .specialInstructions(cartItem.getSpecialInstructions())
                    .build();
        }).collect(Collectors.toList());

        Money totalAmount = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(Money::add)
                .orElseThrow().add(deliveryFee);

        Address deliveryAddress = new Address(
                request.deliveryAddress().street(), request.deliveryAddress().district(),
                request.deliveryAddress().city(), request.deliveryAddress().postalCode(),
                request.deliveryAddress().lat(), request.deliveryAddress().lng()
        );

        // 5. Create Order aggregate
        Order order = Order.builder()
                .userId(userId)
                .restaurantId(cart.getRestaurantId())
                .cartId(cart.getId())
                .correlationId(correlationId)
                .status(OrderStatus.CREATED)
                .orderType(request.orderType())
                .totalAmount(totalAmount)
                .deliveryFee(deliveryFee)
                .deliveryAddress(deliveryAddress)
                .paymentMethod(request.paymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .notes(request.notes())
                .idempotencyKey(idempotencyKey)
                .items(orderItems)
                .build();

        // 6. Transition to PAYMENT_PENDING
        order.transitionTo(OrderStatus.PAYMENT_PENDING, stateMachine, userId.toString(), "Checkout initiated");
        order.setRestaurantTimeoutAt(LocalDateTime.now().plusMinutes(restaurantTimeoutMinutes));

        // DEĞİŞİKLİK BURADA: Artık initiatePayment senkron çağrısını KULLANMIYORUZ.
        order.setPaymentTimeoutAt(LocalDateTime.now().plusMinutes(paymentTimeoutMinutes));

        // 7. Lock cart
        cart.checkout();
        cartRepository.save(cart);

        // 8. Save order and publish event (Outbox üzerinden asenkron gidecek)
        Order saved = orderRepository.save(order);

        // Bu kod Outbox tablosuna yazacak, OutboxRelayScheduler 2 saniye içinde Kafka'ya fırlatacak.
        // Payment Service bu eventi duyup ödemeyi kendi içinde başlatacak. (Saga Pattern)
        eventPublisher.publishOrderCreated(saved);

        log.info("Order created asynchronously. orderId={} userId={}", saved.getId(), userId);
        return OrderResponse.from(saved);
    }
}