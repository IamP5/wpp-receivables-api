package com.tubadev.receivables.infrastructure.configuration.security;

import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Validates {@code X-Hub-Signature-256: sha256=<hex HMAC-SHA256 of the raw body keyed by the app secret>}.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookSignatureVerifier.class);
    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final WhatsAppProperties properties;

    public WebhookSignatureVerifier(final WhatsAppProperties properties) {
        this.properties = Objects.requireNonNull(properties);
        if (!properties.hasAppSecret()) {
            LOG.warn("whatsapp.app-secret is not set: webhook signatures will NOT be verified (local development only)");
        }
    }

    public boolean isValid(final byte[] body, final String signatureHeader) {
        if (!properties.hasAppSecret()) {
            return true;
        }
        if (body == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }

        final var expected = sign(body);
        final var received = signatureHeader.substring(PREFIX.length()).toLowerCase();
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), received.getBytes(StandardCharsets.US_ASCII));
    }

    public String sign(final byte[] body) {
        try {
            final var mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.appSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (final GeneralSecurityException ex) {
            throw new IllegalStateException("Could not compute webhook signature", ex);
        }
    }
}
