package com.tubadev.receivables.application.campaign.impl;

import com.tubadev.receivables.application.campaign.DispatchCampaign;
import com.tubadev.receivables.application.message.SendMessage;
import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignCommand.CompleteCampaign;
import com.tubadev.receivables.domain.campaign.CampaignGateway;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.campaign.DispatchStats;
import com.tubadev.receivables.domain.campaign.RecipientContext;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationGateway;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage.Closed;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerGateway;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.status.MessageStatus;
import com.tubadev.receivables.domain.receivable.EligibilityGateway;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

public class DefaultDispatchCampaign extends DispatchCampaign {

    private static final Logger LOG = Logger.getLogger(DefaultDispatchCampaign.class.getName());

    private final CampaignGateway campaignGateway;
    private final Clock clock;
    private final ConversationGateway conversationGateway;
    private final CustomerGateway customerGateway;
    private final EligibilityGateway eligibilityGateway;
    private final SendMessage sendMessage;
    private final int maxConcurrency;

    public DefaultDispatchCampaign(
            final CampaignGateway campaignGateway,
            final Clock clock,
            final ConversationGateway conversationGateway,
            final CustomerGateway customerGateway,
            final EligibilityGateway eligibilityGateway,
            final SendMessage sendMessage,
            final int maxConcurrency
    ) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("'maxConcurrency' should be greater than zero");
        }
        this.campaignGateway = Objects.requireNonNull(campaignGateway);
        this.clock = Objects.requireNonNull(clock);
        this.conversationGateway = Objects.requireNonNull(conversationGateway);
        this.customerGateway = Objects.requireNonNull(customerGateway);
        this.eligibilityGateway = Objects.requireNonNull(eligibilityGateway);
        this.sendMessage = Objects.requireNonNull(sendMessage);
        this.maxConcurrency = maxConcurrency;
    }

    /** Result of dispatching to one recipient. */
    sealed interface Outcome {
        record Sent(CustomerId customerId) implements Outcome {}
        record Failed(CustomerId customerId, String reason) implements Outcome {}
        record Skipped(CustomerId customerId, String reason) implements Outcome {}
    }

    @Override
    public void execute(final Input in) {
        if (in == null) {
            throw new IllegalArgumentException("Input of DefaultDispatchCampaign should not be null");
        }

        final var campaignId = new CampaignId(in.campaignId());
        final var aCampaign = this.campaignGateway.campaignOfId(campaignId)
                .orElseThrow(() -> DomainException.notFound(Campaign.class, campaignId));

        if (!aCampaign.isRunning()) {
            LOG.info(() -> "Campaign %s is %s, nothing to dispatch".formatted(campaignId.value(), aCampaign.status().value()));
            return;
        }

        final var outcomes = dispatchAll(aCampaign);

        aCampaign.execute(new CompleteCampaign(statsOf(outcomes)));
        this.campaignGateway.save(aCampaign);
    }

    /**
     * One virtual thread per recipient, throttled by a semaphore to respect the Cloud API throughput.
     */
    private List<Outcome> dispatchAll(final Campaign aCampaign) {
        final var permits = new Semaphore(maxConcurrency);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<Outcome>> futures = aCampaign.recipients().stream()
                    .map(recipient -> executor.submit(() -> {
                        permits.acquire();
                        try {
                            return dispatchTo(aCampaign, recipient);
                        } finally {
                            permits.release();
                        }
                    }))
                    .toList();

            return futures.stream().map(DefaultDispatchCampaign::await).toList();
        }
    }

    private Outcome dispatchTo(final Campaign aCampaign, final CustomerId recipient) {
        try {
            final var aCustomer = this.customerGateway.customerOfId(recipient).orElse(null);
            if (aCustomer == null) {
                return new Outcome.Skipped(recipient, "customer_not_found");
            }
            if (!aCustomer.canReceiveCampaigns()) {
                return new Outcome.Skipped(recipient, "not_opted_in");
            }

            final var eligibility = this.eligibilityGateway.eligibilityOf(recipient);
            if (!eligibility.eligible()) {
                return new Outcome.Skipped(recipient, "not_eligible");
            }

            final var aConversation = conversationFor(aCampaign, aCustomer);
            if (aConversation == null) {
                return new Outcome.Skipped(recipient, "journey_in_progress");
            }

            final var content = aCampaign.template().renderFor(new RecipientContext(aCustomer, eligibility));
            final var out = this.sendMessage.execute(new SendTemplateInput(aConversation.id(), content));

            return MessageStatus.FAILED.equals(out.status())
                    ? new Outcome.Failed(recipient, out.failureReason())
                    : new Outcome.Sent(recipient);
        } catch (final Exception ex) {
            return new Outcome.Failed(recipient, ex.getMessage());
        }
    }

    /**
     * A customer actively talking to us (within the service window) is not interrupted by a campaign.
     * A stale open conversation is closed and replaced by a new one, attributed to this campaign.
     */
    private Conversation conversationFor(final Campaign aCampaign, final Customer aCustomer) {
        final var current = this.conversationGateway.openConversationOf(aCustomer.phoneNumber());

        if (current.isPresent()) {
            final var open = current.get();
            if (open.isWithinServiceWindow(clock.instant())) {
                return null;
            }
            open.execute(new AdvanceTo(new Closed("superseded_by_campaign")));
            this.conversationGateway.save(open);
        }

        final var aConversation = Conversation.newConversation(
                this.conversationGateway.nextId(), aCustomer.phoneNumber(), aCustomer.id(), aCampaign.id());
        this.conversationGateway.save(aConversation);
        return aConversation;
    }

    private static DispatchStats statsOf(final List<Outcome> outcomes) {
        int sent = 0, failed = 0, skipped = 0;
        for (var outcome : outcomes) {
            switch (outcome) {
                case Outcome.Sent _ -> sent++;
                case Outcome.Failed(var customerId, var reason) -> {
                    failed++;
                    LOG.warning(() -> "Campaign dispatch failed [customer:%s]: %s".formatted(customerId.value(), reason));
                }
                case Outcome.Skipped _ -> skipped++;
            }
        }
        return new DispatchStats(sent, failed, skipped);
    }

    private static Outcome await(final Future<Outcome> future) {
        try {
            return future.get();
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Campaign dispatch interrupted", ex);
        } catch (final Exception ex) {
            throw new IllegalStateException("Campaign dispatch failed", ex);
        }
    }

    record SendTemplateInput(ConversationId conversationId, MessageContent content) implements SendMessage.Input {
    }
}
