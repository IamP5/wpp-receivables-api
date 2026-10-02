package com.tubadev.receivables.infrastructure.gateway.client.whatsapp;

import com.tubadev.receivables.domain.message.SendResult;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local rejection for recipients outside {@code whatsapp.allowed-country-codes}. It saves a Graph API call that
 * would fail anyway (error 130497, "Business account is restricted from messaging users in this country").
 */
public final class RecipientNotAllowed {

    public static final String CODE = "recipient_not_allowed";

    private static final Logger LOG = LoggerFactory.getLogger(RecipientNotAllowed.class);

    private RecipientNotAllowed() {
    }

    public static SendResult.Rejected of(final PhoneNumber to, final WhatsAppProperties properties) {
        LOG.warn("Not sending to {}: allowed country codes are {}", to.value(), properties.allowedCountryCodes());
        return new SendResult.Rejected(CODE,
                "Recipient country is not allowed, allowed country codes: " + String.join(", ", properties.allowedCountryCodes()));
    }
}
