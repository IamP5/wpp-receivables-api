package com.tubadev.receivables.domain.receivable;

public sealed interface OfferResult {

    record Offered(AnticipationOffer offer) implements OfferResult {}

    /** The requested amount is above what the eligible boletos can cover. */
    record ExceedsAvailable(Money requestedAmount, Money availableAmount) implements OfferResult {}

    /** Below the minimum ticket accepted by the credit policy. */
    record BelowMinimum(Money requestedAmount, Money minimumAmount) implements OfferResult {}

    record NotEligible(String reason) implements OfferResult {}
}
