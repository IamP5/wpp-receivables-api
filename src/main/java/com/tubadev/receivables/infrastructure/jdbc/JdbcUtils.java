package com.tubadev.receivables.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public final class JdbcUtils {

    private JdbcUtils() {
    }

    public static Instant getInstant(final ResultSet rs, final String column) throws SQLException {
        final var ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    /** pgjdbc can´t bind {@link Instant} directly. */
    public static Timestamp toTimestamp(final Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
