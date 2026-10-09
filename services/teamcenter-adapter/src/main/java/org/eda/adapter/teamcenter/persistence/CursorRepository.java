package org.eda.adapter.teamcenter.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CursorRepository {

    private final JdbcClient jdbc;

    public CursorRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long read(String source) {
        return jdbc.sql("SELECT last_order FROM tc_trs_cursor WHERE source = :source")
                .param("source", source)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    public void write(String source, long order) {
        jdbc.sql("""
                INSERT INTO tc_trs_cursor (source, last_order, updated_at)
                VALUES (:source, :order, now())
                ON CONFLICT (source) DO UPDATE
                   SET last_order = EXCLUDED.last_order,
                       updated_at = now()
                """)
                .param("source", source)
                .param("order", order)
                .update();
    }
}
