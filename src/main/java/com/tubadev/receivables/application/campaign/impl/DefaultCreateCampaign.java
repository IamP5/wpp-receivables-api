package com.tubadev.receivables.application.campaign.impl;

import com.tubadev.receivables.application.campaign.CreateCampaign;
import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.campaign.CampaignTemplate;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.exceptions.NotificationException;
import com.tubadev.receivables.domain.validation.handler.Notification;

import java.util.List;
import java.util.Objects;

public class DefaultCreateCampaign extends CreateCampaign {

    private final CampaignGateway campaignGateway;

    public DefaultCreateCampaign(final CampaignGateway campaignGateway) {
        this.campaignGateway = Objects.requireNonNull(campaignGateway);
    }

    @Override
    public Output execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultCreateCampaign should not be null");
        }

        final var notification = Notification.create();
        final var template = notification.validate(() -> CampaignTemplate.of(in.templateName(), in.templateLanguage(), in.templateParameters()));
        final var recipients = notification.validate(() -> recipientsOf(in.customerIds()));

        if (notification.hasError()) {
            throw NotificationException.with("Could not create campaign", notification);
        }

        final var aCampaign = notification.validate(() -> Campaign.newCampaign(this.campaignGateway.nextId(), in.name(), template, recipients));

        if (notification.hasError()) {
            throw NotificationException.with("Could not create campaign", notification);
        }

        this.campaignGateway.save(aCampaign);
        return new StdOutput(aCampaign.id());
    }

    private static List<CustomerId> recipientsOf(final List<String> customerIds) {
        return customerIds == null ? List.of() : customerIds.stream().distinct().map(CustomerId::new).toList();
    }

    record StdOutput(CampaignId campaignId) implements Output {
    }
}
