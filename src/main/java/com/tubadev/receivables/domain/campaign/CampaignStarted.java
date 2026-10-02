package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Instant;

public record CampaignStarted(String campaignId, String name, int recipients, Instant occurredOn) implements CampaignEvent {

    public CampaignStarted {
        this.assertArgumentNotEmpty(campaignId, "'campaignId' should not be empty");
        this.assertArgumentNotNull(occurredOn, "'occurredOn' should not be null");
    }

    public CampaignStarted(final Campaign aCampaign) {
        this(aCampaign.id().value(), aCampaign.name(), aCampaign.recipients().size(), InstantUtils.now());
    }
}
