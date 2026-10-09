package org.eda.linkindex.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.eda.linkindex.domain.DispositionOutcome;
import org.eda.linkindex.domain.DispositionRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DispositionRepository {

    private final JdbcClient jdbc;

    public DispositionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(DispositionRecord d) {
        jdbc.sql("""
                INSERT INTO link_disposition
                    (id, link_id, owner, previous_version, new_version,
                     cause_event_id, opened_at, resolved_at, outcome, actor)
                VALUES
                    (:id, :link, :owner, :prev, :newVer, :cause, :opened, :resolved, :outcome, :actor)
                """)
                .param("id", d.id())
                .param("link", d.linkId())
                .param("owner", d.owner())
                .param("prev", d.previousVersion())
                .param("newVer", d.newVersion())
                .param("cause", d.causeEventId())
                .param("opened", d.openedAt())
                .param("resolved", d.resolvedAt())
                .param("outcome", d.outcome() == null ? null : d.outcome().wire())
                .param("actor", d.actor())
                .update();
    }

    public void resolve(UUID id, DispositionOutcome outcome, String actor, OffsetDateTime at) {
        jdbc.sql("""
                UPDATE link_disposition
                   SET outcome = :outcome, actor = :actor, resolved_at = :at
                 WHERE id = :id
                """)
                .param("outcome", outcome.wire())
                .param("actor", actor)
                .param("at", at)
                .param("id", id)
                .update();
    }

    public Optional<DispositionRecord> findById(UUID id) {
        return jdbc.sql("SELECT * FROM link_disposition WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> map(rs))
                .optional();
    }

    public List<DispositionRecord> openByLink(String linkId) {
        return jdbc.sql("""
                SELECT * FROM link_disposition
                 WHERE link_id = :lid AND resolved_at IS NULL
                 ORDER BY opened_at
                """)
                .param("lid", linkId)
                .query((rs, i) -> map(rs))
                .list();
    }

    public List<DispositionRecord> filter(String owner, Boolean open) {
        StringBuilder sql = new StringBuilder("SELECT * FROM link_disposition WHERE 1=1");
        if (owner != null) {
            sql.append(" AND owner = :owner");
        }
        if (open != null) {
            sql.append(open ? " AND resolved_at IS NULL" : " AND resolved_at IS NOT NULL");
        }
        sql.append(" ORDER BY opened_at");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (owner != null) spec = spec.param("owner", owner);
        return spec.query((rs, i) -> map(rs)).list();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM link_disposition").query(Long.class).single();
    }

    static DispositionRecord map(ResultSet rs) throws SQLException {
        String outcomeStr = rs.getString("outcome");
        return new DispositionRecord(
                (UUID) rs.getObject("id"),
                rs.getString("link_id"),
                rs.getString("owner"),
                rs.getString("previous_version"),
                rs.getString("new_version"),
                rs.getString("cause_event_id"),
                rs.getObject("opened_at", OffsetDateTime.class),
                rs.getObject("resolved_at", OffsetDateTime.class),
                outcomeStr == null ? null : DispositionOutcome.fromWire(outcomeStr),
                rs.getString("actor"));
    }
}
