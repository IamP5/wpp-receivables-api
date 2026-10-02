package com.tubadev.receivables.application.campaign.impl;

import com.tubadev.receivables.application.campaign.GetCampaign;
import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.exceptions.DomainException;

import java.util.Objects;

public class DefaultGetCampaign extends GetCampaign {

    private final CampaignGateway campaignGateway;

    public DefaultGetCampaign(final CampaignGateway campaignGateway) {
        this.campaignGateway = Objects.requireNonNull(campaignGateway);
    }

    @Override
    public Campaign execute(final Input in) {
        final var campaignId = new CampaignId(in.campaignId());
        return this.campaignGateway.campaignOfId(campaignId)
                .orElseThrow(() -> DomainException.notFound(Campaign.class, campaignId));
    }
}
