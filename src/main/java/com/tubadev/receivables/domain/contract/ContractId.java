package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.Identifier;

public record ContractId(String value) implements Identifier<String> {

    public ContractId {
        this.assertArgumentNotEmpty(value, "'contractId' should not be empty");
    }
}
