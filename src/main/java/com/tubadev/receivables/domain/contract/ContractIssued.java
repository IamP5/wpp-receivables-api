package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.math.BigDecimal;
import java.time.Instant;

public record ContractIssued(String contractId, String protocol, String customerId, BigDecimal netAmount, Instant occurredOn)
        implements ContractEvent {

    public ContractIssued {
        this.assertArgumentNotEmpty(contractId, "'contractId' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public ContractIssued(final Contract aContract) {
        this(aContract.id().value(), aContract.protocol(), aContract.customerId().value(),
                aContract.offer().netAmount().amount(), InstantUtils.now());
    }
}
