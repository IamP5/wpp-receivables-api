package com.tubadev.receivables.infrastructure.observer.domainevent;

import com.tubadev.receivables.application.campaign.DispatchCampaign;
import com.tubadev.receivables.domain.DomainEvent;
import com.tubadev.receivables.domain.campaign.CampaignStarted;
import com.tubadev.receivables.infrastructure.observer.Subscriber;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DispatchCampaignSubscriber implements Subscriber<DomainEvent> {

    private final DispatchCampaign dispatchCampaign;

    public DispatchCampaignSubscriber(final DispatchCampaign dispatchCampaign) {
        this.dispatchCampaign = Objects.requireNonNull(dispatchCampaign);
    }

    @Override
    public boolean test(final DomainEvent ev) {
        return ev instanceof CampaignStarted;
    }

    @Override
    public void onEvent(final DomainEvent ev) {
        if (ev instanceof CampaignStarted(var campaignId, _, _, _)) {
            record DispatchInput(String campaignId) implements DispatchCampaign.Input {}
            this.dispatchCampaign.execute(new DispatchInput(campaignId));
        }
    }
}
