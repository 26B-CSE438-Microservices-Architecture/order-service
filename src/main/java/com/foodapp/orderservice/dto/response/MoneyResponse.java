package com.foodapp.orderservice.dto.response;

import com.foodapp.orderservice.domain.valueobject.Money;
import java.math.BigDecimal;

public record MoneyResponse(BigDecimal amount, String currency) {
    public static MoneyResponse from(Money money) {
        if (money == null) return null;
        return new MoneyResponse(money.getAmount(), money.getCurrency());
    }
}
