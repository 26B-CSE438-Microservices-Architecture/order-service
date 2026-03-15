package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.dto.request.ConfirmOrderRequest;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID orderId, UUID restaurantId, ConfirmOrderRequest request) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getRestaurantId().equals(restaurantId))
            throw new OrderNotBelongToUserException("Order does not belong to this restaurant");

        // PAYMENT_HELD → CONFIRMED_BY_RESTAURANT → PAYMENT_CAPTURE_PENDING
        order.transitionTo(OrderStatus.CONFIRMED_BY_RESTAURANT, stateMachine, restaurantId.toString(), "Restaurant confirmed");

        if (request.estimatedPreparationMinutes() != null) {
            order.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(request.estimatedPreparationMinutes() + 30));
        }

        order.transitionTo(OrderStatus.PAYMENT_CAPTURE_PENDING, stateMachine, "SYSTEM", "Initiating payment capture after restaurant approval");
        orderRepository.save(order);

        // Payment service parayı çekecek; capture_completed gelince PAID → PREPARING
        eventPublisher.publishPaymentCaptureRequested(order);
        log.info("Restaurant confirmed order, payment capture requested. orderId={}", orderId);
    }
}
