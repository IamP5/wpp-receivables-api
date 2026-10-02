package com.tubadev.receivables.infrastructure.mediator;

import com.tubadev.receivables.application.conversation.HandleInboundMessage;
import com.tubadev.receivables.application.message.UpdateMessageStatus;
import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.utils.InstantUtils;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import com.tubadev.receivables.infrastructure.rest.models.webhook.WebhookPayload;
import com.tubadev.receivables.infrastructure.rest.models.webhook.WebhookPayload.InboundMessage;
import com.tubadev.receivables.infrastructure.rest.models.webhook.WebhookPayload.Status;
import com.tubadev.receivables.infrastructure.rest.models.webhook.WebhookPayload.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Splits a webhook delivery into use case calls. Each item is isolated: one poison message does not make
 * Meta retry (and re-deliver) the whole batch. Inbound handling is idempotent by wamid anyway.
 */
@Component
public class WebhookMediator {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookMediator.class);

    private final HandleInboundMessage handleInboundMessage;
    private final UpdateMessageStatus updateMessageStatus;
    private final WhatsAppProperties properties;

    public WebhookMediator(
            final HandleInboundMessage handleInboundMessage,
            final UpdateMessageStatus updateMessageStatus,
            final WhatsAppProperties properties
    ) {
        this.handleInboundMessage = Objects.requireNonNull(handleInboundMessage);
        this.updateMessageStatus = Objects.requireNonNull(updateMessageStatus);
        this.properties = Objects.requireNonNull(properties);
    }

    public void process(final WebhookPayload payload) {
        payload.messageValues()
                .filter(this::isForOurPhoneNumber)
                .forEach(value -> {
                    nullSafe(value.messages()).forEach(this::handleMessage);
                    nullSafe(value.statuses()).forEach(this::handleStatus);
                });
    }

    private boolean isForOurPhoneNumber(final Value value) {
        final var configured = properties.phoneNumberId();
        if (configured == null || configured.isBlank() || value.metadata() == null) {
            return true;
        }
        final var matches = configured.equals(value.metadata().phoneNumberId());
        if (!matches) {
            LOG.debug("Ignoring webhook for phone number id {}", value.metadata().phoneNumberId());
        }
        return matches;
    }

    private void handleMessage(final InboundMessage message) {
        record Input(String wamid, String from, MessageContent content, Instant receivedAt) implements HandleInboundMessage.Input {}

        try {
            final var receivedAt = InstantUtils.fromEpochSeconds(message.timestamp());
            final var out = this.handleInboundMessage.execute(new Input(
                    message.id(), message.from(), message.toContent(), receivedAt == null ? InstantUtils.now() : receivedAt));
            LOG.info("Inbound processed [wamid:{}] [conversation:{}] [stage:{}] [replies:{}] [duplicated:{}]",
                    message.id(), out.conversationId().value(), out.stage(), out.repliesSent(), out.duplicated());
        } catch (final Exception ex) {
            LOG.error("Failed to process inbound message [wamid:{}]", message.id(), ex);
        }
    }

    private void handleStatus(final Status status) {
        record Input(String wamid, String status, String errorReason) implements UpdateMessageStatus.Input {}

        try {
            this.updateMessageStatus.execute(new Input(status.id(), status.status(), status.errorReason()));
        } catch (final Exception ex) {
            LOG.error("Failed to process status [wamid:{}] [status:{}]", status.id(), status.status(), ex);
        }
    }

    private static <T> List<T> nullSafe(final List<T> list) {
        return list == null ? List.of() : list;
    }
}
