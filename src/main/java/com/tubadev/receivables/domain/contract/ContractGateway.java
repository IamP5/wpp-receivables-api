package com.tubadev.receivables.domain.contract;

import java.util.Optional;

public interface ContractGateway {

    ContractId nextId();

    Optional<Contract> contractOfId(ContractId anId);

    /** Persists the contract and its pending events, and returns the stored aggregate. */
    Contract save(Contract aContract);

    void saveDocument(ContractId anId, ContractDocument aDocument);

    Optional<ContractDocument> documentOf(ContractId anId);
}
