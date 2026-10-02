package com.tubadev.receivables.infrastructure.rest.models.res;

import com.tubadev.receivables.application.campaign.StartCampaign;

public record StartCampaignResponse(String campaignId, String status) {

    public StartCampaignResponse(final StartCampaign.Output out) {
        this(out.campaignId().value(), out.status());
    }
}
