package com.tubadev.receivables.application.campaign;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.campaign.CampaignId;

/**
 * Moves the campaign to RUNNING. The dispatch itself happens asynchronously, driven by the
 * {@code CampaignStarted} event through the outbox.
 */
public abstract class StartCampaign extends UseCase<StartCampaign.Input, StartCampaign.Output> {

    public interface Input {
        String campaignId();
    }

    public interface Output {
        CampaignId campaignId();
        String status();
    }
}
