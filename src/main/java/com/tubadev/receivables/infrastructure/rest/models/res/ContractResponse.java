package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.domain.contract.Contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ContractResponse(
        String id,
        String protocol,
        String customerId,
        String conversationId,
        Assignor assignor,
        List<Receivable> receivables,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        BigDecimal monthlyRate,
        LocalDate expectedCreditDate,
        Signature signature,
        String authenticationCode,
        Document document,
        Instant issuedAt
) {

    public record Assignor(String name, String documentNumber, String documentType, String phoneNumber) {}

    public record Receivable(String id, String payerName, LocalDate dueDate, BigDecimal amount) {}

    public record Signature(String channel, String phoneNumber, String biometricVerificationId, BigDecimal biometricScore,
                            Instant signedAt) {}

    public record Document(String filename, String sha256, String url) {}

    public ContractResponse(final Contract c) {
        this(
                c.id().value(),
                c.protocol(),
                c.customerId().value(),
                c.conversationId().value(),
                new Assignor(c.assignor().name(), c.assignor().documentNumber(), c.assignor().documentType(), c.assignor().phoneNumber()),
                c.offer().receivables().stream()
                        .map(r -> new Receivable(r.id().value(), r.payerName(), r.dueDate(), r.amount().amount()))
                        .toList(),
                c.offer().grossAmount().amount(),
                c.offer().feeAmount().amount(),
                c.offer().netAmount().amount(),
                c.offer().monthlyRate(),
                c.expectedCreditDate(),
                new Signature(c.signature().channel(), c.signature().phoneNumber(), c.signature().biometricVerificationId(),
                        c.signature().biometricScore(), c.signature().signedAt()),
                c.authenticationCode(),
                c.hasDocument() ? new Document(c.documentFilename(), c.documentSha256(), "/contracts/%s/document".formatted(c.id().value())) : null,
                c.issuedAt()
        );
    }
}
