package com.tubadev.receivables.application.campaign;

import com.tubadev.receivables.application.UseCase;
import com.tubadev.receivables.domain.campaign.CampaignId;

import java.util.List;

public abstract class CreateCampaign extends UseCase<CreateCampaign.Input, CreateCampaign.Output> {

    public interface Input {
        String name();
        String templateName();
        String templateLanguage();
        /** Literals or placeholders such as {@code {{customer.first_name}}} and {@code {{receivables.available_amount}}}. */
        List<String> templateParameters();
        List<String> customerIds();
    }

    public interface Output {
        CampaignId campaignId();
    }
}
