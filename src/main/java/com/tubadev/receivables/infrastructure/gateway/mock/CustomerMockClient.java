package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.person.PhoneNumber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "receivables.adapter", havingValue = "mock", matchIfMissing = true)
public class CustomerMockClient implements CustomerGateway {

    private final ReceivablesMockStore store;

    public CustomerMockClient(final ReceivablesMockStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public Optional<Customer> customerOfId(final CustomerId anId) {
        return store.customer(anId);
    }

    @Override
    public Optional<Customer> customerOfPhoneNumber(final PhoneNumber aPhoneNumber) {
        return store.customerByPhone(aPhoneNumber);
    }

    @Override
    public void optOut(final CustomerId anId) {
        store.optOut(anId);
    }
}
