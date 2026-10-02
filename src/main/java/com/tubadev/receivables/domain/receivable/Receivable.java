package com.tubadev.receivables.domain.receivable;

import com.tubadev.receivables.domain.ValueObject;

import java.time.LocalDate;

/**
 * A boleto issued by the customer and eligible for anticipation (read model owned by the Receivables service).
 */
public record Receivable(ReceivableId id, String payerName, String documentNumber, LocalDate dueDate, Money amount)
        implements ValueObject {

    public Receivable {
        this.assertArgumentNotNull(id, "'receivable.id' should not be null");
        this.assertArgumentNotEmpty(payerName, "'receivable.payerName' should not be empty");
        this.assertArgumentNotNull(dueDate, "'receivable.dueDate' should not be null");
        this.assertArgumentNotNull(amount, "'receivable.amount' should not be null");
    }
}
