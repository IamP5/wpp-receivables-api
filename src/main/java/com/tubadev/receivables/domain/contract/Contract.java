package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.AggregateRoot;
import com.tubadev.receivables.domain.contract.ContractCommand.AttachDocument;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.Receivable;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * The assignment contract ("cessão de crédito") of an anticipation: the customer sells the boletos of the offer
 * and receives the net amount. It is issued once the anticipation is requested and is immutable afterwards,
 * except for attaching the rendered document.
 */
public class Contract extends AggregateRoot<ContractId> {

    private int version;
    private String protocol;
    private CustomerId customerId;
    private ConversationId conversationId;
    private Party assignor;
    private AnticipationOffer offer;
    private LocalDate expectedCreditDate;
    private ElectronicSignature signature;
    private String authenticationCode;
    private String documentFilename;
    private String documentSha256;
    private Instant issuedAt;
    private Instant updatedAt;

    private Contract(
            final ContractId anId,
            final int version,
            final String protocol,
            final CustomerId aCustomerId,
            final ConversationId aConversationId,
            final Party assignor,
            final AnticipationOffer offer,
            final LocalDate expectedCreditDate,
            final ElectronicSignature signature,
            final String authenticationCode,
            final String documentFilename,
            final String documentSha256,
            final Instant issuedAt,
            final Instant updatedAt
    ) {
        super(anId);
        this.version = version;
        this.protocol = this.assertArgumentNotEmpty(protocol, "'protocol' should not be empty");
        this.customerId = this.assertArgumentNotNull(aCustomerId, "'customerId' should not be null");
        this.conversationId = this.assertArgumentNotNull(aConversationId, "'conversationId' should not be null");
        this.assignor = this.assertArgumentNotNull(assignor, "'assignor' should not be null");
        this.offer = this.assertArgumentNotNull(offer, "'offer' should not be null");
        this.expectedCreditDate = this.assertArgumentNotNull(expectedCreditDate, "'expectedCreditDate' should not be null");
        this.signature = this.assertArgumentNotNull(signature, "'signature' should not be null");
        this.authenticationCode = authenticationCode;
        this.documentFilename = documentFilename;
        this.documentSha256 = documentSha256;
        this.issuedAt = this.assertArgumentNotNull(issuedAt, "'issuedAt' should not be null");
        this.updatedAt = this.assertArgumentNotNull(updatedAt, "'updatedAt' should not be null");
    }

    public static Contract issue(
            final ContractId anId,
            final String protocol,
            final CustomerId aCustomerId,
            final ConversationId aConversationId,
            final Party assignor,
            final AnticipationOffer offer,
            final LocalDate expectedCreditDate,
            final ElectronicSignature signature
    ) {
        final var now = InstantUtils.now();
        final var aContract = new Contract(anId, 0, protocol, aCustomerId, aConversationId, assignor, offer, expectedCreditDate,
                signature, null, null, null, now, now);
        aContract.authenticationCode = aContract.computeAuthenticationCode();
        aContract.registerEvent(new ContractIssued(aContract));
        return aContract;
    }

    public static Contract with(
            final ContractId anId,
            final int version,
            final String protocol,
            final CustomerId aCustomerId,
            final ConversationId aConversationId,
            final Party assignor,
            final AnticipationOffer offer,
            final LocalDate expectedCreditDate,
            final ElectronicSignature signature,
            final String authenticationCode,
            final String documentFilename,
            final String documentSha256,
            final Instant issuedAt,
            final Instant updatedAt
    ) {
        return new Contract(anId, version, protocol, aCustomerId, aConversationId, assignor, offer, expectedCreditDate, signature,
                authenticationCode, documentFilename, documentSha256, issuedAt, updatedAt);
    }

    public void execute(final ContractCommand... cmds) {
        if (cmds == null || cmds.length == 0) {
            return;
        }

        for (var cmd : cmds) {
            switch (cmd) {
                case AttachDocument(var filename, var sha256) -> {
                    this.documentFilename = filename;
                    this.documentSha256 = sha256;
                }
            }
        }

        this.updatedAt = InstantUtils.now();
    }

    /**
     * Fingerprint of the contract terms (parties, boletos, prices, dates and signature evidence) printed on the
     * document, so anyone holding a copy can check it against the record.
     */
    private String computeAuthenticationCode() {
        final var receivables = offer.receivables().stream()
                .map(r -> "%s:%s:%s".formatted(r.id().value(), r.dueDate(), r.amount().amount().toPlainString()))
                .collect(Collectors.joining(","));

        final var terms = String.join("|",
                id().value(), protocol, customerId.value(), String.valueOf(assignor.documentNumber()),
                offer.offerId(), receivables,
                offer.grossAmount().amount().toPlainString(), offer.feeAmount().amount().toPlainString(),
                offer.netAmount().amount().toPlainString(), offer.monthlyRate().toPlainString(),
                expectedCreditDate.toString(), signature.biometricVerificationId(), signature.signedAt().toString());

        try {
            final var digest = MessageDigest.getInstance("SHA-256").digest(terms.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean hasDocument() {
        return documentSha256 != null;
    }

    public int receivablesCount() {
        return offer.receivables().size();
    }

    public LocalDate lastDueDate() {
        return offer.receivables().stream().map(Receivable::dueDate).max(LocalDate::compareTo).orElseThrow();
    }

    public int version() {
        return version;
    }

    public String protocol() {
        return protocol;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public ConversationId conversationId() {
        return conversationId;
    }

    public Party assignor() {
        return assignor;
    }

    public AnticipationOffer offer() {
        return offer;
    }

    public LocalDate expectedCreditDate() {
        return expectedCreditDate;
    }

    public ElectronicSignature signature() {
        return signature;
    }

    public String authenticationCode() {
        return authenticationCode;
    }

    public String documentFilename() {
        return documentFilename;
    }

    public String documentSha256() {
        return documentSha256;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
