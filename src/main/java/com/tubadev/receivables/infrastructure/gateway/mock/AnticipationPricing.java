package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.Receivable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Simple discount pricing: fee = amount × monthly rate × (days until due / 30).
 */
record AnticipationPricing(BigDecimal monthlyRate) {

    private static final BigDecimal DAYS_IN_MONTH = BigDecimal.valueOf(30);

    Money feeOf(final Receivable r, final LocalDate today) {
        final var days = BigDecimal.valueOf(Math.max(1, ChronoUnit.DAYS.between(today, r.dueDate())));
        final var fee = r.amount().amount()
                .multiply(monthlyRate)
                .multiply(days)
                .divide(DAYS_IN_MONTH, MathContext.DECIMAL64);
        return Money.brl(fee);
    }

    Money netOf(final Receivable r, final LocalDate today) {
        return r.amount().minus(feeOf(r, today));
    }
}
