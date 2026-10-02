package com.tubadev.receivables.application.contract;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.contract.ContractDocument;

public abstract class GetContractDocument extends UseCase<GetContractDocument.Input, ContractDocument> {

    public interface Input {
        String contractId();
    }
}
