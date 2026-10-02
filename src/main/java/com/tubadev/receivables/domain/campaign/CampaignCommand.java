package com.tubadev.receivables.domain.campaign;

import com.tubadev.receivables.domain.AssertionConcern;

public sealed interface CampaignCommand extends AssertionConcern {

    record StartCampaign() implements CampaignCommand {}

    record CompleteCampaign(DispatchStats stats) implements CampaignCommand {
        public CompleteCampaign {
            this.assertArgumentNotNull(stats, "'stats' should not be null");
        }
    }

    record CancelCampaign() implements CampaignCommand {}
}
