package com.tubadev.receivables.domain.contract;

import com.tubadev.receivables.domain.ValueObject;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evidence that the assignor agreed to the contract: the channel and phone number used, and the biometric
 * validation of the selfie taken at signature time.
 */
public record ElectronicSignature(
        String channel,
        String phoneNumber,
        String biometricVerificationId,
        BigDecimal biometricScore,
        Instant signedAt
) implements ValueObject {

    public static final String WHATSAPP = "whatsapp";

    public ElectronicSignature {
        this.assertArgumentNotEmpty(channel, "'signature.channel' should not be empty");
        this.assertArgumentNotEmpty(phoneNumber, "'signature.phoneNumber' should not be empty");
        this.assertArgumentNotEmpty(biometricVerificationId, "'signature.biometricVerificationId' should not be empty");
        this.assertArgumentNotNull(biometricScore, "'signature.biometricScore' should not be null");
        this.assertArgumentNotNull(signedAt, "'signature.signedAt' should not be null");
    }
}
