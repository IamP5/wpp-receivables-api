package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.MessagingGateway;
import com.tubadev.receivables.domain.message.SendResult;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.domain.utils.IdUtils;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import com.tubadev.receivables.infrastructure.gateway.client.whatsapp.RecipientNotAllowed;
import com.tubadev.receivables.infrastructure.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Runs the whole journey locally without Meta: outbound messages are only logged ({@code whatsapp.adapter=mock}).
 * Honors {@code whatsapp.allowed-country-codes} like the real adapter, so local runs fail the same way the sandbox does.
 */
@Component
@ConditionalOnProperty(name = "whatsapp.adapter", havingValue = "mock")
public class MessagingMockClient implements MessagingGateway {

    private static final Logger LOG = LoggerFactory.getLogger(MessagingMockClient.class);

    private final WhatsAppProperties properties;

    public MessagingMockClient(final WhatsAppProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public SendResult send(final PhoneNumber to, final MessageContent content) {
        if (!properties.allowsRecipient(to)) {
            return RecipientNotAllowed.of(to, properties);
        }

        final var wamid = "wamid.MOCK_" + IdUtils.uniqueId();
        LOG.info("[whatsapp-mock] → {} ({}): {}", to.value(), content.type(), Json.writeValueAsString(content));
        return new SendResult.Accepted(wamid);
    }
}
