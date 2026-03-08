package com.foodapp.orderservice.config.scheduler;

import com.foodapp.orderservice.domain.aggregate.Order;
import com.foodapp.orderservice.domain.enums.OrderCancellationReason;
import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 60000) // Her 1 dakikada bir
    @Transactional
    public void expirePaymentTimeouts() {
        List<Order> timedOut = orderRepository
                .findByStatusAndPaymentTimeoutAtBefore(OrderStatus.PAYMENT_PENDING, LocalDateTime.now());

        timedOut.forEach(order -> {
            log.info("Payment timeout for orderId={}", order.getId());
            try {
                order.transitionTo(OrderStatus.EXPIRED, stateMachine, "SYSTEM", "Payment timeout");
                orderRepository.save(order);
            } catch (Exception e) {
                log.error("Failed to expire order {}: {}", order.getId(), e.getMessage());
            }
        });
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireRestaurantTimeouts() {
        List<Order> timedOut = orderRepository
                .findByStatusAndRestaurantTimeoutAtBefore(OrderStatus.PAID, LocalDateTime.now());

        timedOut.forEach(order -> {
            log.info("Restaurant timeout for orderId={}", order.getId());
            try {
                order.transitionTo(OrderStatus.RESTAURANT_TIMEOUT, stateMachine, "SYSTEM", "Restaurant did not respond");
                order.cancel(stateMachine, OrderCancellationReason.RESTAURANT_TIMEOUT,
                        "Restaurant did not respond in time", "SYSTEM");
                orderRepository.save(order);
                eventPublisher.publishOrderCancelled(order);
            } catch (Exception e) {
                log.error("Failed to handle restaurant timeout for order {}: {}", order.getId(), e.getMessage());
            }
        });
    }
}
