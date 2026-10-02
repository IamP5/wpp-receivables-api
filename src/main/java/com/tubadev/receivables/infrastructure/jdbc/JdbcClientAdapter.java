package com.tubadev.receivables.infrastructure.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JdbcClientAdapter implements DatabaseClient {

    private final JdbcClient target;

    public JdbcClientAdapter(final JdbcClient target) {
        this.target = Objects.requireNonNull(target);
    }

    @Override
    public <T> Optional<T> queryOne(final String sql, final Map<String, Object> params, final RowMap<T> mapper) {
        return this.target.sql(sql).params(params).query(new RowMapAdapter<>(mapper)).optional();
    }

    @Override
    public <T> List<T> query(final String sql, final Map<String, Object> params, final RowMap<T> mapper) {
        return this.target.sql(sql).params(params).query(new RowMapAdapter<>(mapper)).list();
    }

    @Override
    public int update(final String sql, final Map<String, Object> params) {
        return this.target.sql(sql).params(params).update();
    }
}
