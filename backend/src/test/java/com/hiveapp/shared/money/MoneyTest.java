package com.hiveapp.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesIsoCurrencyAndSupportsSameCurrencyArithmetic() {
        Money total = Money.of(new BigDecimal("29.9"), "usd")
                .add(Money.of(new BigDecimal("0.10"), "USD"));

        assertThat(total.amount()).isEqualByComparingTo("30.00");
        assertThat(total.currencyCode()).isEqualTo("USD");
    }

    @Test
    void rejectsUnknownCurrencyAndExcessMinorUnits() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "NOT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported ISO 4217 currency");
        assertThatThrownBy(() -> Money.of(new BigDecimal("1.001"), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minor-unit precision");
    }

    @Test
    void refusesImplicitCurrencyConversion() {
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "USD")
                .add(Money.of(BigDecimal.ONE, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void supportsNegativeValuesForFutureCreditsWithoutAllowingUnsafeRounding() {
        Money credit = Money.of(new BigDecimal("-2.50"), "USD");

        assertThat(credit.isNegative()).isTrue();
        assertThat(credit.multiply(2).amount()).isEqualByComparingTo("-5.00");
    }
}
