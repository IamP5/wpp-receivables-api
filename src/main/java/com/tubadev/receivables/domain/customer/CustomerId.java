package com.tubadev.receivables.domain.customer;

import com.tubadev.receivables.domain.Identifier;

public record CustomerId(String value) implements Identifier<String> {

    public CustomerId {
        this.assertArgumentNotEmpty(value, "'customerId' should not be empty");
    }
}
