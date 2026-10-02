package com.tubadev.receivables.application.contract.impl;

import com.tubadev.receivables.application.contract.IssueContract;
import com.tubadev.receivables.domain.contract.Contract;
import com.tubadev.receivables.domain.contract.ContractCommand.AttachDocument;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractDocumentGateway;
import com.tubadev.receivables.domain.contract.ContractGateway;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.contract.Party;

import java.util.Objects;

public class DefaultIssueContract extends IssueContract {

    private final ContractGateway contractGateway;
    private final ContractDocumentGateway contractDocumentGateway;

    public DefaultIssueContract(final ContractGateway contractGateway, final ContractDocumentGateway contractDocumentGateway) {
        this.contractGateway = Objects.requireNonNull(contractGateway);
        this.contractDocumentGateway = Objects.requireNonNull(contractDocumentGateway);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultIssueContract should not be null");
        }

        final var aContract = Contract.issue(
                this.contractGateway.nextId(),
                in.protocol(),
                in.customer().id(),
                in.conversationId(),
                Party.of(in.customer()),
                in.offer(),
                in.expectedCreditDate(),
                in.signature()
        );

        final var document = this.contractDocumentGateway.render(aContract);
        aContract.execute(new AttachDocument(document.filename(), document.sha256()));

        final var saved = this.contractGateway.save(aContract);
        this.contractGateway.saveDocument(saved.id(), document);

        return new StdOutput(saved.id(), document);
    }

    record StdOutput(ContractId contractId, ContractDocument document) implements Output {
    }
}
