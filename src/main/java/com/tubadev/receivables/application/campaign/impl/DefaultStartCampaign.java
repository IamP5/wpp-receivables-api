package com.tubadev.receivables.application.campaign.impl;

import com.tubadev.receivables.application.campaign.StartCampaign;
import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignCommand;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.exceptions.DomainException;

import java.util.Objects;

public class DefaultStartCampaign extends StartCampaign {

    private final CampaignGateway campaignGateway;

    public DefaultStartCampaign(final CampaignGateway campaignGateway) {
        this.campaignGateway = Objects.requireNonNull(campaignGateway);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultStartCampaign should not be null");
        }

        final var campaignId = new CampaignId(in.campaignId());
        final var aCampaign = this.campaignGateway.campaignOfId(campaignId)
                .orElseThrow(() -> DomainException.notFound(Campaign.class, campaignId));

        aCampaign.execute(new CampaignCommand.StartCampaign());
        this.campaignGateway.save(aCampaign);

        return new StdOutput(aCampaign.id(), aCampaign.status().value());
    }

    record StdOutput(CampaignId campaignId, String status) implements Output {
    }
}
