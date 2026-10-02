package com.tubadev.receivables.application.campaign;

import com.tubadev.receivables.application.UnitUseCase;

/**
 * Sends the campaign template to every recipient that is opted-in, eligible for anticipation and not in the
 * middle of an active journey, then completes the campaign with the dispatch stats.
 */
public abstract class DispatchCampaign extends UnitUseCase<DispatchCampaign.Input> {

    public interface Input {
        String campaignId();
    }
}
