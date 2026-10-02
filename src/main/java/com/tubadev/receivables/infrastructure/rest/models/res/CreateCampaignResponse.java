package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.application.campaign.CreateCampaign;

public record CreateCampaignResponse(String campaignId) {

    public CreateCampaignResponse(final CreateCampaign.Output out) {
        this(out.campaignId().value());
    }
}
