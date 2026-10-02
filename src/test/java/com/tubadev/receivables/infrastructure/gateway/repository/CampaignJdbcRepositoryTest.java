package com.tubadev.receivables.infrastructure.gateway.repository;

import com.tubadev.receivables.AbstractRepositoryTest;
import com.tubadev.receivables.domain.Fixture;
import com.tubadev.receivables.domain.campaign.CampaignCommand;
import com.tubadev.receivables.domain.campaign.CampaignStarted;
import com.tubadev.receivables.domain.campaign.DispatchStats;
import com.tubadev.receivables.domain.campaign.status.CampaignStatus;
import com.tubadev.receivables.domain.customer.CustomerId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class CampaignJdbcRepositoryTest extends AbstractRepositoryTest {

    @Test
    void givenCampaign_whenSavedAndStarted_shouldRoundTripAndStoreStartedEvent() {
        final var aCampaign = Fixture.Campaigns.anticipationOffer(new CustomerId("c2"), new CustomerId("c1"));
        campaignRepository().save(aCampaign);

        final var loaded = campaignRepository().campaignOfId(aCampaign.id()).orElseThrow();
        Assertions.assertEquals(List.of(new CustomerId("c2"), new CustomerId("c1")), List.copyOf(loaded.recipients()));
        Assertions.assertEquals(aCampaign.template(), loaded.template());

        loaded.execute(new CampaignCommand.StartCampaign());
        campaignRepository().save(loaded);

        final var events = eventRepository().eventsOfAggregate(aCampaign.id().value());
        Assertions.assertTrue(events.stream().anyMatch(CampaignStarted.class::isInstance));

        final var running = campaignRepository().campaignOfId(aCampaign.id()).orElseThrow();
        running.execute(new CampaignCommand.CompleteCampaign(new DispatchStats(1, 0, 1)));
        campaignRepository().save(running);

        final var completed = campaignRepository().campaignOfId(aCampaign.id()).orElseThrow();
        Assertions.assertEquals(CampaignStatus.COMPLETED, completed.status().value());
        Assertions.assertEquals(new DispatchStats(1, 0, 1), completed.stats());
    }
}
