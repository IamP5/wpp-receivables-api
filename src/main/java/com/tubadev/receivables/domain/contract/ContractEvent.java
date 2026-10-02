package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.DomainEvent;

public sealed interface ContractEvent extends DomainEvent permits ContractIssued {

    String TYPE = "Contract";

    String contractId();

    @Override
    default String aggregateId() {
        return contractId();
    }

    @Override
    default String aggregateType() {
        return TYPE;
    }
}
