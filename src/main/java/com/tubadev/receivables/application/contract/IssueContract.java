package com.tubadev.receivables.application.contract;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.contract.ElectronicSignature;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;

import java.time.LocalDate;

/**
 * Issues the assignment contract of a requested anticipation and renders its document.
 */
public abstract class IssueContract extends UseCase<IssueContract.Input, IssueContract.Output> {

    public interface Input {
        Customer customer();

        ConversationId conversationId();

        String protocol();

        AnticipationOffer offer();

        LocalDate expectedCreditDate();

        ElectronicSignature signature();
    }

    public interface Output {
        ContractId contractId();

        ContractDocument document();
    }
}
