package com.tubadev.receivables.domain.customer;

import com.tubadev.receivables.domain.person.PhoneNumber;

import java.util.Optional;

/**
 * Port to the Customer service (registration data and WhatsApp consent).
 */
public interface CustomerGateway {

    Optional<Customer> customerOfId(CustomerId anId);

    Optional<Customer> customerOfPhoneNumber(PhoneNumber aPhoneNumber);

    void optOut(CustomerId anId);
}
