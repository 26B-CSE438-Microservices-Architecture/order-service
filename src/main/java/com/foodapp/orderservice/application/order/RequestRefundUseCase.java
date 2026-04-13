package com.foodapp.orderservice.application.order;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.enums.PaymentStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.event.producer.OrderEventPublisher;
import com.foodapp.orderservice.exception.OrderNotFoundException;
import com.foodapp.orderservice.exception.OrderNotBelongToUserException;
import com.foodapp.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * İptal edilmiş ve ödemesi çekilmiş siparişler için geri ödeme talebini başlatır.
 * CANCELLED (paymentStatus=PAID) → REFUND_REQUESTED
 * Payment service bu eventi duyarak geri ödemeyi başlatır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestRefundUseCase {

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;
    private final OrderEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID orderId, UUID requestingUserId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (!order.getUserId().equals(requestingUserId))
            throw new OrderNotBelongToUserException("Access denied");

        if (order.getStatus() != OrderStatus.CANCELLED)
            throw new IllegalStateException("Refund can only be requested for cancelled orders, current status: " + order.getStatus());

        if (order.getPaymentStatus() != PaymentStatus.PAID)
            throw new IllegalStateException("Refund can only be requested when payment was captured, current payment status: " + order.getPaymentStatus());

        order.transitionTo(OrderStatus.REFUND_REQUESTED, stateMachine, requestingUserId.toString(), "Customer requested refund");
        orderRepository.save(order);

        eventPublisher.publishRefundRequested(order);
        log.info("Refund requested for orderId={} by userId={}", orderId, requestingUserId);
    }
}
