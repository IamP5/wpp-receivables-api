package com.tubadev.receivables.infrastructure.jdbc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DatabaseClient {

    <T> Optional<T> queryOne(String sql, Map<String, Object> params, RowMap<T> mapper);

    <T> List<T> query(String sql, Map<String, Object> params, RowMap<T> mapper);

    int update(String sql, Map<String, Object> params);
}
