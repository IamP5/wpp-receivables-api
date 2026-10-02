package com.tubadev.receivables.application.contract.impl;

import com.tubadev.receivables.application.contract.GetContractDocument;
import com.tubadev.receivables.domain.contract.Contract;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractGateway;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.exceptions.DomainException;

import java.util.Objects;

public class DefaultGetContractDocument extends GetContractDocument {

    private final ContractGateway contractGateway;

    public DefaultGetContractDocument(final ContractGateway contractGateway) {
        this.contractGateway = Objects.requireNonNull(contractGateway);
    }

    @Override
    public ContractDocument execute(final Input in) {
        final var contractId = new ContractId(in.contractId());
        return this.contractGateway.documentOf(contractId)
                .orElseThrow(() -> DomainException.notFound(Contract.class, contractId));
    }
}
