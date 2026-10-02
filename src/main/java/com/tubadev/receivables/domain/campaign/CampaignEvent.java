package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.DomainEvent;

public sealed interface CampaignEvent extends DomainEvent
        permits CampaignCreated, CampaignStarted, CampaignCompleted, CampaignCanceled {

    String TYPE = "Campaign";

    String campaignId();

    @Override
    default String aggregateId() {
        return campaignId();
    }

    @Override
    default String aggregateType() {
        return TYPE;
    }
}
