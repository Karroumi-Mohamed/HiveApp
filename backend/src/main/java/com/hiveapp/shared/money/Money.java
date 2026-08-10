package com.hiveapp.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Currency-safe monetary value. Arithmetic never performs implicit currency conversion and
 * amounts must fit the ISO currency's minor-unit precision.
 */
public final class Money {

    private final BigDecimal amount;
    private final String currencyCode;

    private Money(BigDecimal amount, String currencyCode) {
        Currency currency = currency(currencyCode);
        this.amount = normalize(amount, currency);
        this.currencyCode = currency.getCurrencyCode();
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currencyCode);
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, currencyCode);
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return of(amount.add(other.amount), currencyCode);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return of(amount.subtract(other.amount), currencyCode);
    }

    public Money multiply(long multiplier) {
        return of(amount.multiply(BigDecimal.valueOf(multiplier)), currencyCode);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currencyCode + " and " + other.currencyCode);
        }
    }

    public static String normalizeCurrencyCode(String currencyCode) {
        return currency(currencyCode).getCurrencyCode();
    }

    private static Currency currency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code is required");
        }
        Currency currency;
        try {
            currency = Currency.getInstance(currencyCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported ISO 4217 currency: " + currencyCode, exception);
        }
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Currency does not define minor units: " + currencyCode);
        }
        return currency;
    }

    private static BigDecimal normalize(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "amount");
        try {
            return amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Amount " + amount + " exceeds minor-unit precision for " + currency.getCurrencyCode(),
                    exception);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Money other)) {
            return false;
        }
        return currencyCode.equals(other.currencyCode) && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currencyCode);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currencyCode;
    }
}
