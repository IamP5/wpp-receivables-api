package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) implements ValueObject {

    public static final Currency BRL = Currency.getInstance("BRL");

    public Money {
        this.assertArgumentNotNull(amount, "'amount' should not be null");
        this.assertArgumentNotNull(currency, "'currency' should not be null");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money brl(final BigDecimal amount) {
        return new Money(amount, BRL);
    }

    public static Money brl(final String amount) {
        return brl(new BigDecimal(amount));
    }

    public static Money zero() {
        return brl(BigDecimal.ZERO);
    }

    public Money plus(final Money other) {
        this.assertConditionTrue(currency.equals(other.currency()), "Can´t sum different currencies");
        return new Money(amount.add(other.amount()), currency);
    }

    public Money minus(final Money other) {
        this.assertConditionTrue(currency.equals(other.currency()), "Can´t subtract different currencies");
        return new Money(amount.subtract(other.amount()), currency);
    }
}
