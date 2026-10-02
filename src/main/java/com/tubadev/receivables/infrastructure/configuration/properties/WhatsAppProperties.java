package com.tubadev.receivables.infrastructure.configuration.properties;

import com.tubadev.receivables.domain.person.PhoneNumber;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * WhatsApp Cloud API settings (Meta for Developers → App → WhatsApp → API Setup).
 *
 * @param adapter        {@code cloud-api} (real Meta API) or {@code mock} (logs messages, for local journeys)
 * @param phoneNumberId  the sender phone number ID (not the phone number itself)
 * @param accessToken    temporary (24h) or System User token with {@code whatsapp_business_messaging}
 * @param appSecret      App Settings → Basic → App secret, validates {@code X-Hub-Signature-256} of webhooks
 * @param verifyToken    any string, must match the one typed in the webhook configuration
 * @param allowedCountryCodes country calling codes the app may message (e.g. {@code 1} while the Meta app can only
 *                       reach US numbers); empty allows any recipient
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        @DefaultValue("cloud-api") String adapter,
        @DefaultValue("https://graph.facebook.com") String baseUrl,
        @DefaultValue("v23.0") String apiVersion,
        String phoneNumberId,
        String accessToken,
        String appSecret,
        String verifyToken,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        List<String> allowedCountryCodes
) {

    public WhatsAppProperties {
        allowedCountryCodes = allowedCountryCodes == null
                ? List.of()
                : allowedCountryCodes.stream().map(code -> code.replaceAll("\\D", "")).filter(code -> !code.isEmpty()).toList();
    }

    public boolean allowsRecipient(final PhoneNumber aPhoneNumber) {
        return allowedCountryCodes.isEmpty() || aPhoneNumber.hasCountryCodeIn(allowedCountryCodes);
    }

    public boolean hasAppSecret() {
        return appSecret != null && !appSecret.isBlank();
    }
}
