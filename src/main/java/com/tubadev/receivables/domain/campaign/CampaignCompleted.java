package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record CampaignCompleted(String campaignId, int sent, int failed, int skipped, Instant occurredOn) implements CampaignEvent {

    public CampaignCompleted {
        this.assertArgumentNotEmpty(campaignId, "'campaignId' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public CampaignCompleted(final Campaign aCampaign) {
        this(aCampaign.id().value(), aCampaign.stats().sent(), aCampaign.stats().failed(), aCampaign.stats().skipped(), InstantUtils.now());
    }
}
