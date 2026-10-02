package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.customer.CustomerId;

/**
 * Port to the credit/eligibility policy of the Receivables service.
 */
public interface EligibilityGateway {

    Eligibility eligibilityOf(CustomerId aCustomerId);
}
