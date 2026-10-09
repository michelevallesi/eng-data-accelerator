package org.eda.linkindex.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.eda.linkindex.domain.ArtefactRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ArtefactRepository {

    private final JdbcClient jdbc;

    public ArtefactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(ArtefactRecord a) {
        jdbc.sql("""
                INSERT INTO link_artefact
                    (native_id, uri, version, tool, native_state, deleted_at)
                VALUES
                    (:nid, :uri, :ver, :tool, :state, :deletedAt)
                ON CONFLICT (native_id) DO UPDATE
                   SET uri = EXCLUDED.uri,
                       version = EXCLUDED.version,
                       tool = EXCLUDED.tool,
                       native_state = EXCLUDED.native_state,
                       deleted_at = EXCLUDED.deleted_at
                """)
                .param("nid", a.nativeId())
                .param("uri", a.uri())
                .param("ver", a.version())
                .param("tool", a.tool())
                .param("state", a.nativeState())
                .param("deletedAt", a.deletedAt())
                .update();
    }

    public Optional<ArtefactRecord> findByUri(String uri) {
        return jdbc.sql("SELECT * FROM link_artefact WHERE uri = :uri")
                .param("uri", uri)
                .query((rs, i) -> map(rs))
                .optional();
    }

    public Optional<ArtefactRecord> findByNativeId(String nativeId) {
        return jdbc.sql("SELECT * FROM link_artefact WHERE native_id = :nid")
                .param("nid", nativeId)
                .query((rs, i) -> map(rs))
                .optional();
    }

    public List<ArtefactRecord> all() {
        return jdbc.sql("SELECT * FROM link_artefact ORDER BY native_id")
                .query((rs, i) -> map(rs))
                .list();
    }

    public void markVersion(String uri, String newVersion) {
        jdbc.sql("UPDATE link_artefact SET version = :v WHERE uri = :uri")
                .param("v", newVersion)
                .param("uri", uri)
                .update();
    }

    public void markDeleted(String uri, OffsetDateTime at) {
        jdbc.sql("UPDATE link_artefact SET deleted_at = :at WHERE uri = :uri")
                .param("at", at)
                .param("uri", uri)
                .update();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM link_artefact").query(Long.class).single();
    }

    private static ArtefactRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ArtefactRecord(
                rs.getString("native_id"),
                rs.getString("uri"),
                rs.getString("version"),
                rs.getString("tool"),
                rs.getString("native_state"),
                rs.getObject("deleted_at", OffsetDateTime.class));
    }
}
