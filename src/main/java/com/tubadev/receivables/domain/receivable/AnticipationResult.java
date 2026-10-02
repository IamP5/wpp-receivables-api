package com.tubadev.receivables.domain.receivable;

import java.time.LocalDate;

public sealed interface AnticipationResult {

    /** Accepted: {@code netAmount} will be credited at {@code expectedCreditDate}, under the terms of {@code offer}. */
    record Requested(String protocol, Money netAmount, LocalDate expectedCreditDate, AnticipationOffer offer)
            implements AnticipationResult {}

    /** Business refusal (expired offer, boleto no longer eligible, credit limit...). */
    record Refused(String reason) implements AnticipationResult {}
}
