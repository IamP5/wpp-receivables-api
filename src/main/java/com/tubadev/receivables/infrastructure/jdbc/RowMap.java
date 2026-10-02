package com.tubadev.receivables.infrastructure.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface RowMap<T> {
    T mapRow(ResultSet rs) throws SQLException;
}
