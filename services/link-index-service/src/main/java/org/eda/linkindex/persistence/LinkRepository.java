package org.eda.linkindex.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.eda.linkindex.domain.LinkRecord;
import org.eda.linkindex.domain.LinkState;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LinkRepository {

    private final JdbcClient jdbc;

    public LinkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(LinkRecord r) {
        jdbc.sql("""
                INSERT INTO link_link
                    (id, relation, source_native_id, target_native_id,
                     source_uri, target_uri, source_version, target_version,
                     state, cause, actor, updated_at)
                VALUES
                    (:id, :rel, :sNid, :tNid, :sUri, :tUri, :sVer, :tVer,
                     :state, :cause, :actor, :updated)
                """)
                .param("id", r.id())
                .param("rel", r.relation())
                .param("sNid", r.sourceNativeId())
                .param("tNid", r.targetNativeId())
                .param("sUri", r.sourceUri())
                .param("tUri", r.targetUri())
                .param("sVer", r.sourceVersion())
                .param("tVer", r.targetVersion())
                .param("state", r.state().wire())
                .param("cause", r.cause())
                .param("actor", r.actor())
                .param("updated", r.updatedAt())
                .update();
    }

    public void update(LinkRecord r) {
        jdbc.sql("""
                UPDATE link_link
                   SET source_version = :sVer,
                       target_version = :tVer,
                       state = :state,
                       cause = :cause,
                       actor = :actor,
                       updated_at = :updated
                 WHERE id = :id
                """)
                .param("sVer", r.sourceVersion())
                .param("tVer", r.targetVersion())
                .param("state", r.state().wire())
                .param("cause", r.cause())
                .param("actor", r.actor())
                .param("updated", r.updatedAt())
                .param("id", r.id())
                .update();
    }

    public Optional<LinkRecord> findById(String id) {
        return jdbc.sql("SELECT * FROM link_link WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> map(rs))
                .optional();
    }

    public List<LinkRecord> all() {
        return jdbc.sql("SELECT * FROM link_link ORDER BY id")
                .query((rs, i) -> map(rs))
                .list();
    }

    public List<LinkRecord> findBySourceUri(String uri) {
        return jdbc.sql("SELECT * FROM link_link WHERE source_uri = :uri AND state <> 'dangling'")
                .param("uri", uri)
                .query((rs, i) -> map(rs))
                .list();
    }

    public List<LinkRecord> findByTargetUri(String uri) {
        return jdbc.sql("SELECT * FROM link_link WHERE target_uri = :uri AND state <> 'dangling'")
                .param("uri", uri)
                .query((rs, i) -> map(rs))
                .list();
    }

    public List<LinkRecord> findByEndpointUri(String uri) {
        return jdbc.sql("""
                SELECT * FROM link_link
                 WHERE (source_uri = :uri OR target_uri = :uri)
                   AND state <> 'dangling'
                 ORDER BY id
                """)
                .param("uri", uri)
                .query((rs, i) -> map(rs))
                .list();
    }

    public List<LinkRecord> filter(String source, String target, LinkState state, String relation) {
        StringBuilder sql = new StringBuilder("SELECT * FROM link_link WHERE 1=1");
        if (source != null) {
            sql.append(" AND source_native_id = :source");
        }
        if (target != null) {
            sql.append(" AND target_native_id = :target");
        }
        if (state != null) {
            sql.append(" AND state = :state");
        }
        if (relation != null) {
            sql.append(" AND relation = :relation");
        }
        sql.append(" ORDER BY id");
        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (source != null) spec = spec.param("source", source);
        if (target != null) spec = spec.param("target", target);
        if (state != null) spec = spec.param("state", state.wire());
        if (relation != null) spec = spec.param("relation", relation);
        return spec.query((rs, i) -> map(rs)).list();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM link_link").query(Long.class).single();
    }

    static LinkRecord map(ResultSet rs) throws SQLException {
        return new LinkRecord(
                rs.getString("id"),
                rs.getString("relation"),
                rs.getString("source_native_id"),
                rs.getString("target_native_id"),
                rs.getString("source_uri"),
                rs.getString("target_uri"),
                rs.getString("source_version"),
                rs.getString("target_version"),
                LinkState.fromWire(rs.getString("state")),
                rs.getString("cause"),
                rs.getString("actor"),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
