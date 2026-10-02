package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.receivable.Eligibility;

/** Data available to resolve template placeholders for one campaign recipient. */
public record RecipientContext(Customer customer, Eligibility eligibility) {
}
