package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.ValueObject;
import com.tubadev.receivables.domain.customer.Customer;

/**
 * Snapshot of the assignor (the customer selling the boletos) as qualified in the contract. It is copied, not
 * referenced, because the contract must keep the data valid at signature time.
 */
public record Party(String name, String documentNumber, String documentType, String phoneNumber) implements ValueObject {

    public Party {
        this.assertArgumentNotEmpty(name, "'party.name' should not be empty");
        this.assertArgumentNotEmpty(phoneNumber, "'party.phoneNumber' should not be empty");
    }

    public static Party of(final Customer aCustomer) {
        final var document = aCustomer.document();
        return new Party(
                aCustomer.name(),
                document == null ? null : document.value(),
                document == null ? null : document.type(),
                aCustomer.phoneNumber().value()
        );
    }
}
