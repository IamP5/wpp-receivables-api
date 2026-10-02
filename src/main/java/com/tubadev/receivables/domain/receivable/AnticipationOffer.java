package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.ValueObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The boletos selected to cover the amount the customer asked for, and the pricing of anticipating them.
 * Boletos are indivisible, so {@code netAmount} (what is credited) is usually a bit above {@code requestedAmount}.
 */
public record AnticipationOffer(
        String offerId,
        Money requestedAmount,
        List<Receivable> receivables,
        Money grossAmount,
        Money feeAmount,
        Money netAmount,
        BigDecimal monthlyRate,
        Instant validUntil
) implements ValueObject {

    public AnticipationOffer {
        this.assertArgumentNotEmpty(offerId, "'offerId' should not be empty");
        this.assertArgumentNotNull(requestedAmount, "'requestedAmount' should not be null");
        this.assertArgumentNotEmpty(receivables, "'receivables' should not be empty");
        this.assertArgumentNotNull(grossAmount, "'grossAmount' should not be null");
        this.assertArgumentNotNull(feeAmount, "'feeAmount' should not be null");
        this.assertArgumentNotNull(netAmount, "'netAmount' should not be null");
        this.assertArgumentNotNull(monthlyRate, "'monthlyRate' should not be null");
        this.assertArgumentNotNull(validUntil, "'validUntil' should not be null");
        receivables = List.copyOf(receivables);
    }
}
