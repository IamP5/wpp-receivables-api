package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.AbstractRepositoryTest;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.contract.ContractCommand.AttachDocument;
import com.tubadev.receivables.domain.contract.ContractDocument;
import com.tubadev.receivables.domain.contract.ContractIssued;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ContractJdbcRepositoryTest extends AbstractRepositoryTest {

    @Test
    void givenIssuedContract_whenSaved_shouldRoundTripTermsDocumentAndEvent() {
        final var maria = Fixture.Customers.maria();
        final var aConversation = conversationRepository().save(Fixture.Conversations.of(maria));
        final var aContract = Fixture.Contracts.issued(maria, aConversation.id());
        final var document = new ContractDocument("%PDF-1.7 test".getBytes(), "contrato-%s.pdf".formatted(aContract.protocol()));
        aContract.execute(new AttachDocument(document.filename(), document.sha256()));

        final var saved = contractRepository().save(aContract);
        contractRepository().saveDocument(saved.id(), document);

        final var actual = contractRepository().contractOfId(aContract.id()).orElseThrow();
        Assertions.assertEquals(1, actual.version());
        Assertions.assertEquals(aContract.protocol(), actual.protocol());
        Assertions.assertEquals(aContract.assignor(), actual.assignor());
        Assertions.assertEquals(aContract.offer(), actual.offer());
        Assertions.assertEquals(aContract.signature(), actual.signature());
        Assertions.assertEquals(aContract.expectedCreditDate(), actual.expectedCreditDate());
        Assertions.assertEquals(aContract.authenticationCode(), actual.authenticationCode());
        Assertions.assertEquals(document.sha256(), actual.documentSha256());

        Assertions.assertEquals(document, contractRepository().documentOf(aContract.id()).orElseThrow());
        Assertions.assertInstanceOf(ContractIssued.class, eventRepository().eventsOfAggregate(aContract.id().value()).getFirst());
    }
}
