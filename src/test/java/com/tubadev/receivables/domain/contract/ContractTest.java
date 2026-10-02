package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.contract.ContractCommand.AttachDocument;
import com.tubadev.receivables.domain.conversation.ConversationId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class ContractTest extends UnitTest {

    private static final ConversationId CONVERSATION = new ConversationId("conv_1");

    @Test
    void givenRequestedAnticipation_whenIssuing_shouldFingerprintTheTermsAndRegisterEvent() {
        final var aContract = Fixture.Contracts.issued(Fixture.Customers.maria(), CONVERSATION);

        Assertions.assertTrue(aContract.authenticationCode().matches("[0-9A-F]{64}"));
        Assertions.assertEquals("Maria Silva", aContract.assignor().name());
        Assertions.assertEquals("11222333000181", aContract.assignor().documentNumber());
        Assertions.assertEquals(2, aContract.receivablesCount());
        Assertions.assertFalse(aContract.hasDocument());
        final var event = Assertions.assertInstanceOf(ContractIssued.class, aContract.domainEvents().getFirst());
        Assertions.assertEquals(aContract.protocol(), event.protocol());
    }

    @Test
    void givenDifferentTerms_shouldHaveDifferentAuthenticationCodes() {
        final var original = Fixture.Contracts.issued(Fixture.Customers.maria(), CONVERSATION);
        final var otherDate = Contract.issue(original.id(), original.protocol(), original.customerId(), CONVERSATION,
                original.assignor(), original.offer(), LocalDate.of(2026, 10, 5), original.signature());
        final var sameTerms = Contract.issue(original.id(), original.protocol(), original.customerId(), CONVERSATION,
                original.assignor(), original.offer(), original.expectedCreditDate(), original.signature());

        Assertions.assertNotEquals(original.authenticationCode(), otherDate.authenticationCode());
        Assertions.assertEquals(original.authenticationCode(), sameTerms.authenticationCode());
    }

    @Test
    void givenRenderedDocument_whenAttaching_shouldKeepNameAndHash() {
        final var aContract = Fixture.Contracts.issued(Fixture.Customers.maria(), CONVERSATION);
        final var document = new ContractDocument("%PDF-1.7".getBytes(), "contrato.pdf");

        aContract.execute(new AttachDocument(document.filename(), document.sha256()));

        Assertions.assertTrue(aContract.hasDocument());
        Assertions.assertEquals(64, aContract.documentSha256().length());
        Assertions.assertEquals("contrato.pdf", aContract.documentFilename());
    }
}
