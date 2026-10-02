package com.tubadev.receivables.application.contract.impl;

import com.tubadev.receivables.application.contract.GetContract;
import com.tubadev.receivables.domain.contract.Contract;
import com.tubadev.receivables.domain.contract.ContractGateway;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.exceptions.DomainException;

import java.util.Objects;

public class DefaultGetContract extends GetContract {

    private final ContractGateway contractGateway;

    public DefaultGetContract(final ContractGateway contractGateway) {
        this.contractGateway = Objects.requireNonNull(contractGateway);
    }

    @Override
    public Contract execute(final Input in) {
        final var contractId = new ContractId(in.contractId());
        return this.contractGateway.contractOfId(contractId)
                .orElseThrow(() -> DomainException.notFound(Contract.class, contractId));
    }
}
