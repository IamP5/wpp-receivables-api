package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.UnitTest;
import com.tubadev.receivables.domain.campaign.CampaignCommand.*;
import com.tubadev.receivables.domain.campaign.status.CampaignStatus;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.receivable.Eligibility;
import com.tubadev.receivables.domain.receivable.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class CampaignTest extends UnitTest {

    @Test
    void givenNewCampaign_shouldBeDraft() {
        final var aCampaign = Fixture.Campaigns.anticipationOffer(new CustomerId("c1"), new CustomerId("c2"), new CustomerId("c1"));

        Assertions.assertEquals(CampaignStatus.DRAFT, aCampaign.status().value());
        Assertions.assertEquals(2, aCampaign.recipients().size());
        Assertions.assertInstanceOf(CampaignCreated.class, aCampaign.domainEvents().getFirst());
    }

    @Test
    void givenDraft_whenStartAndComplete_shouldRegisterEvents() {
        final var aCampaign = Fixture.Campaigns.anticipationOffer(new CustomerId("c1"));

        aCampaign.execute(new StartCampaign());
        aCampaign.execute(new CompleteCampaign(new DispatchStats(1, 0, 0)));

        Assertions.assertEquals(CampaignStatus.COMPLETED, aCampaign.status().value());
        Assertions.assertNotNull(aCampaign.startedAt());
        Assertions.assertNotNull(aCampaign.finishedAt());
        Assertions.assertInstanceOf(CampaignStarted.class, aCampaign.domainEvents().get(1));
        Assertions.assertInstanceOf(CampaignCompleted.class, aCampaign.domainEvents().get(2));
    }

    @Test
    void givenDraft_whenComplete_shouldThrow() {
        final var aCampaign = Fixture.Campaigns.anticipationOffer(new CustomerId("c1"));

        final var ex = Assertions.assertThrows(DomainException.class,
                () -> aCampaign.execute(new CompleteCampaign(DispatchStats.EMPTY)));

        Assertions.assertEquals("Campaign with status draft can´t transit to completed", ex.getMessage());
    }

    @Test
    void givenCanceled_whenStart_shouldThrow() {
        final var aCampaign = Fixture.Campaigns.anticipationOffer(new CustomerId("c1"));
        aCampaign.execute(new CancelCampaign());

        Assertions.assertThrows(DomainException.class, () -> aCampaign.execute(new StartCampaign()));
    }

    @Test
    void givenNoRecipients_whenCreating_shouldThrow() {
        Assertions.assertThrows(DomainException.class, () -> Fixture.Campaigns.anticipationOffer());
    }

    @Test
    void givenPlaceholders_whenRendering_shouldResolvePerRecipient() {
        final var template = CampaignTemplate.of("antecipacao_disponivel", "pt_BR",
                List.of("{{customer.first_name}}", "{{receivables.available_amount}}", "{{receivables.eligible_count}}", "fixo"));

        final var content = template.renderFor(new RecipientContext(
                Fixture.Customers.maria(), Eligibility.of(Money.brl("15230.5"), 4)));

        Assertions.assertEquals(List.of("Maria", "R$ 15.230,50", "4", "fixo"),
                content.bodyParameters().stream().map(p -> p.replace(' ', ' ')).toList());
    }
}
