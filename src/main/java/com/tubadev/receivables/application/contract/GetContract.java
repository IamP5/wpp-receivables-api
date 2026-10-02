package com.tubadev.receivables.application.contract;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.contract.Contract;

public abstract class GetContract extends UseCase<GetContract.Input, Contract> {

    public interface Input {
        String contractId();
    }
}
