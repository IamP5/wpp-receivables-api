package com.tubadev.receivables.domain.customer;

import com.tubadev.receivables.domain.ValueObject;
import com.tubadev.receivables.domain.person.Document;
import com.tubadev.receivables.domain.person.PhoneNumber;

/**
 * Read model of a customer owned by the Customer service. This service never changes it directly;
 * every change goes through {@link CustomerGateway}.
 */
public record Customer(CustomerId id, String name, Document document, PhoneNumber phoneNumber, boolean optedIn)
        implements ValueObject {

    public Customer {
        this.assertArgumentNotNull(id, "'customer.id' should not be null");
        this.assertArgumentNotEmpty(name, "'customer.name' should not be empty");
        this.assertArgumentNotNull(phoneNumber, "'customer.phoneNumber' should not be null");
    }

    public String firstName() {
        return name.strip().split("\\s+")[0];
    }

    /** Meta requires an explicit opt-in before sending business-initiated (marketing) templates. */
    public boolean canReceiveCampaigns() {
        return optedIn;
    }
}
