package com.tubadev.receivables.application.campaign;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.campaign.Campaign;

public abstract class GetCampaign extends UseCase<GetCampaign.Input, Campaign> {

    public interface Input {
        String campaignId();
    }
}
