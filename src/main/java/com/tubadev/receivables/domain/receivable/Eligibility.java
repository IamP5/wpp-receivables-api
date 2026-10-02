package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.ValueObject;

/**
 * How much the customer can anticipate right now, given the eligible boletos and the credit policy.
 */
public record Eligibility(boolean eligible, Money availableAmount, int eligibleReceivables, String reason)
        implements ValueObject {

    public Eligibility {
        this.assertArgumentNotNull(availableAmount, "'availableAmount' should not be null");
    }

    public static Eligibility of(final Money availableAmount, final int eligibleReceivables) {
        final var eligible = eligibleReceivables > 0 && availableAmount.amount().signum() > 0;
        return new Eligibility(eligible, availableAmount, eligibleReceivables, eligible ? null : "no_eligible_receivables");
    }

    public static Eligibility notEligible(final String reason) {
        return new Eligibility(false, Money.zero(), 0, reason);
    }
}
