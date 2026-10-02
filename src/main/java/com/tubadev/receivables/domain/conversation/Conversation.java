package com.tubadev.receivables.domain.conversation;

import com.tubadev.receivables.domain.AggregateRoot;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.conversation.ConversationCommand.AdvanceTo;
import com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound;
import com.tubadev.receivables.domain.conversation.journey.JourneyStage;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.exceptions.DomainException;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.domain.utils.InstantUtils;

import java.time.Duration;
import java.time.Instant;

/**
 * One run of the anticipation journey with a WhatsApp user. It owns the journey stage and the
 * 24h customer service window rule of the WhatsApp Business Platform.
 */
public class Conversation extends AggregateRoot<ConversationId> {

    public static final Duration SERVICE_WINDOW = Duration.ofHours(24);

    private int version;
    private PhoneNumber phoneNumber;
    private CustomerId customerId;
    private CampaignId campaignId;
    private JourneyStage stage;
    private Instant lastInboundAt;
    private Instant createdAt;
    private Instant updatedAt;

    private Conversation(
            final ConversationId anId,
            final int version,
            final PhoneNumber aPhoneNumber,
            final CustomerId aCustomerId,
            final CampaignId aCampaignId,
            final JourneyStage aStage,
            final Instant lastInboundAt,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        super(anId);
        this.setVersion(version);
        this.setPhoneNumber(aPhoneNumber);
        this.setCustomerId(aCustomerId);
        this.setCampaignId(aCampaignId);
        this.setStage(aStage);
        this.setLastInboundAt(lastInboundAt);
        this.setCreatedAt(createdAt);
        this.setUpdatedAt(updatedAt);
    }

    /**
     * @param aCustomerId may be null when the phone number is not registered in the Customer service
     * @param aCampaignId the campaign that started the conversation, null when the customer reached out first
     */
    public static Conversation newConversation(
            final ConversationId anId,
            final PhoneNumber aPhoneNumber,
            final CustomerId aCustomerId,
            final CampaignId aCampaignId
    ) {
        final var now = InstantUtils.now();
        final var aConversation = new Conversation(anId, 0, aPhoneNumber, aCustomerId, aCampaignId,
                new JourneyStage.Started(), null, now, now);
        aConversation.registerEvent(new ConversationStarted(aConversation));
        return aConversation;
    }

    public static Conversation with(
            final ConversationId anId,
            final int version,
            final PhoneNumber aPhoneNumber,
            final CustomerId aCustomerId,
            final CampaignId aCampaignId,
            final JourneyStage aStage,
            final Instant lastInboundAt,
            final Instant createdAt,
            final Instant updatedAt
    ) {
        return new Conversation(anId, version, aPhoneNumber, aCustomerId, aCampaignId, aStage, lastInboundAt, createdAt, updatedAt);
    }

    public void execute(final ConversationCommand... cmds) {
        if (cmds == null || cmds.length == 0) {
            return;
        }

        for (var cmd : cmds) {
            switch (cmd) {
                case RegisterInbound(var receivedAt) -> applyInbound(receivedAt);
                case AdvanceTo(var next) -> applyAdvance(next);
            }
        }

        this.setUpdatedAt(InstantUtils.now());
    }

    public boolean isOpen() {
        return !this.stage.isTerminal();
    }

    public boolean isWithinServiceWindow(final Instant now) {
        return lastInboundAt != null && now.isBefore(lastInboundAt.plus(SERVICE_WINDOW));
    }

    /**
     * Free-form messages (text, buttons...) are only allowed within 24h of the last customer message;
     * outside it, the business must use an approved template.
     */
    public void assertCanSend(final MessageContent content, final Instant now) {
        if (content.requiresServiceWindow() && !isWithinServiceWindow(now)) {
            throw DomainException.with(
                    "Conversation %s is outside the 24h customer service window: only templates can be sent".formatted(id.value()));
        }
    }

    public int version() {
        return version;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public CampaignId campaignId() {
        return campaignId;
    }

    public JourneyStage stage() {
        return stage;
    }

    public Instant lastInboundAt() {
        return lastInboundAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void applyInbound(final Instant receivedAt) {
        if (this.lastInboundAt == null || receivedAt.isAfter(this.lastInboundAt)) {
            this.setLastInboundAt(receivedAt);
        }
    }

    private void applyAdvance(final JourneyStage next) {
        final var previous = this.stage;
        if (previous.equals(next)) {
            return;
        }

        if (!previous.canAdvanceTo(next)) {
            throw DomainException.with("Journey can´t advance from %s to %s".formatted(previous.value(), next.value()));
        }

        this.setStage(next);
        this.registerEvent(new JourneyAdvanced(this, previous.value()));

        if (next.isTerminal()) {
            this.registerEvent(new ConversationEnded(this));
        }
    }

    private void setVersion(final int version) {
        this.version = version;
    }

    private void setPhoneNumber(final PhoneNumber phoneNumber) {
        this.phoneNumber = this.assertArgumentNotNull(phoneNumber, "'phoneNumber' should not be null");
    }

    private void setCustomerId(final CustomerId customerId) {
        this.customerId = customerId;
    }

    private void setCampaignId(final CampaignId campaignId) {
        this.campaignId = campaignId;
    }

    private void setStage(final JourneyStage stage) {
        this.stage = this.assertArgumentNotNull(stage, "'stage' should not be null");
    }

    private void setLastInboundAt(final Instant lastInboundAt) {
        this.lastInboundAt = lastInboundAt;
    }

    private void setCreatedAt(final Instant createdAt) {
        this.createdAt = this.assertArgumentNotNull(createdAt, "'createdAt' should not be null");
    }

    private void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = this.assertArgumentNotNull(updatedAt, "'updatedAt' should not be null");
    }
}
