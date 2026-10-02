package com.tubadev.receivables.domain.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class InstantUtils {

    /** Business dates (offer validity, protocol, credit date, contract date) follow Brasília time. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private InstantUtils() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public static Instant fromEpochSeconds(final String epochSeconds) {
        if (epochSeconds == null || epochSeconds.isBlank()) {
            return null;
        }
        return Instant.ofEpochSecond(Long.parseLong(epochSeconds));
    }
}
