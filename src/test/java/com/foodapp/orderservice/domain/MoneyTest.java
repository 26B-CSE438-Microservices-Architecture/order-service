package com.foodapp.orderservice.domain;

import com.foodapp.orderservice.domain.valueobject.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidValues() {
        Money money = Money.of(new BigDecimal("100.00"), "TRY");
        assertThat(money.getAmount()).isEqualByComparingTo("100.00");
        assertThat(money.getCurrency()).isEqualTo("TRY");
    }

    @Test
    void shouldCreateZeroMoney() {
        Money zero = Money.zero("TRY");
        assertThat(zero.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(zero.getCurrency()).isEqualTo("TRY");
    }

    @Test
    void shouldThrowWhenAmountIsNegative() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01"), "TRY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void shouldThrowWhenAmountIsNull() {
        assertThatThrownBy(() -> Money.of(null, "TRY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenCurrencyIsBlank() {
        assertThatThrownBy(() -> Money.of(BigDecimal.TEN, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAddSameCurrency() {
        Money a = Money.of(new BigDecimal("50.00"), "TRY");
        Money b = Money.of(new BigDecimal("30.00"), "TRY");
        Money result = a.add(b);
        assertThat(result.getAmount()).isEqualByComparingTo("80.00");
        assertThat(result.getCurrency()).isEqualTo("TRY");
    }

    @Test
    void shouldThrowWhenAddingDifferentCurrencies() {
        Money tryMoney = Money.of(new BigDecimal("50.00"), "TRY");
        Money usdMoney = Money.of(new BigDecimal("30.00"), "USD");
        assertThatThrownBy(() -> tryMoney.add(usdMoney))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currencies");
    }

    @Test
    void shouldSubtractSameCurrency() {
        Money a = Money.of(new BigDecimal("100.00"), "TRY");
        Money b = Money.of(new BigDecimal("40.00"), "TRY");
        assertThat(a.subtract(b).getAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void shouldBeEqualWhenSameAmountAndCurrency() {
        Money a = Money.of(new BigDecimal("100.00"), "TRY");
        Money b = Money.of(new BigDecimal("100.00"), "TRY");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldReturnTrueForPositiveAmount() {
        assertThat(Money.of(new BigDecimal("1.00"), "TRY").isPositive()).isTrue();
        assertThat(Money.zero("TRY").isPositive()).isFalse();
    }
}
