package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.customer.CustomerId;

/**
 * Port to the Receivables service: selects the boletos that cover an amount, prices the anticipation
 * and executes it once the customer confirms.
 */
public interface AnticipationGateway {

    OfferResult offerFor(CustomerId aCustomerId, Money requestedAmount);

    AnticipationResult request(CustomerId aCustomerId, String offerId);
}
