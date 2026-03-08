package com.foodapp.orderservice.statemachine;

import com.foodapp.orderservice.domain.enums.OrderStatus;
import com.foodapp.orderservice.domain.statemachine.OrderStateMachine;
import com.foodapp.orderservice.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderStateMachineTest {

    private OrderStateMachine stateMachine;

    @BeforeEach void setUp() { stateMachine = new OrderStateMachine(); }

    @Test void allowValidTransition() {
        assertThatCode(() -> stateMachine.validate(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING))
                .doesNotThrowAnyException();
    }

    @Test void rejectIllegal_DeliveredToCancelled() {
        assertThatThrownBy(() -> stateMachine.validate(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test void allowCancellationFromPaid() {
        assertThatCode(() -> stateMachine.validate(OrderStatus.PAID, OrderStatus.CANCELLED))
                .doesNotThrowAnyException();
    }
}
