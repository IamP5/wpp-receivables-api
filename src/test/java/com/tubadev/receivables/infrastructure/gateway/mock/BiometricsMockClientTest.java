package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.biometrics.BiometricResult;
import com.tubadev.receivables.domain.biometrics.BiometricResult.Rejected;
import com.tubadev.receivables.domain.biometrics.Selfie;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.infrastructure.configuration.properties.MockProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Tag("unitTest")
class BiometricsMockClientTest {

    private static final CustomerId CUSTOMER = new CustomerId("cus_1");
    private static final Selfie GOOD = new Selfie(new byte[20_000], "image/jpeg");

    @Test
    void givenRules_shouldRejectBadPhotosAndApproveGoodOnes() {
        final var target = client(0);

        Assertions.assertEquals(new Rejected(Rejected.UNSUPPORTED_MEDIA), target.verify(CUSTOMER, new Selfie(new byte[20_000], "image/webp")));
        Assertions.assertEquals(new Rejected(Rejected.LOW_QUALITY), target.verify(CUSTOMER, new Selfie(new byte[100], "image/jpeg")));
        final var approved = Assertions.assertInstanceOf(BiometricResult.Approved.class, target.verify(CUSTOMER, GOOD));
        Assertions.assertEquals(new BigDecimal("0.97"), approved.score());
        Assertions.assertEquals(Instant.parse("2026-10-01T15:00:00Z"), approved.verifiedAt());
    }

    @Test
    void givenFailFirstAttempts_shouldRejectThoseAttemptsAsFaceMismatch() {
        final var target = client(1);

        Assertions.assertEquals(new Rejected(Rejected.FACE_MISMATCH), target.verify(CUSTOMER, GOOD));
        Assertions.assertInstanceOf(BiometricResult.Approved.class, target.verify(CUSTOMER, GOOD));
    }

    private static BiometricsMockClient client(final int failFirstAttempts) {
        final var properties = new MockProperties(List.of(), null,
                new MockProperties.Biometrics(10_240, failFirstAttempts, new BigDecimal("0.97")));
        return new BiometricsMockClient(Clock.fixed(Instant.parse("2026-10-01T15:00:00Z"), ZoneOffset.UTC), properties);
    }
}
