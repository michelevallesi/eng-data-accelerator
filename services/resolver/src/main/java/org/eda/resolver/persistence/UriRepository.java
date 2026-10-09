package org.eda.resolver.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.eda.resolver.domain.UriRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UriRepository {

    private final JdbcClient jdbc;

    public UriRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UriRecord> findByToolAndNativeId(String tool, String nativeId) {
        return jdbc.sql("""
                SELECT uri, tool, native_id, deep_link, created_at
                  FROM uri
                 WHERE tool = :tool AND native_id = :nativeId
                """)
                .param("tool", tool)
                .param("nativeId", nativeId)
                .query(UriRepository::toRecord)
                .optional();
    }

    public Optional<UriRecord> findByUri(String uri) {
        return jdbc.sql("""
                SELECT uri, tool, native_id, deep_link, created_at
                  FROM uri
                 WHERE uri = :uri
                """)
                .param("uri", uri)
                .query(UriRepository::toRecord)
                .optional();
    }

    public void insert(UriRecord record) {
        jdbc.sql("""
                INSERT INTO uri (uri, tool, native_id, deep_link, created_at)
                VALUES (:uri, :tool, :nativeId, :deepLink, :createdAt)
                """)
                .param("uri", record.uri())
                .param("tool", record.tool())
                .param("nativeId", record.nativeId())
                .param("deepLink", record.deepLink())
                .param("createdAt", record.createdAt())
                .update();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM uri")
                .query(Long.class)
                .single();
    }

    public Optional<String> aliasTarget(String fromUri) {
        try {
            return Optional.ofNullable(jdbc.sql("SELECT to_uri FROM uri_alias WHERE from_uri = :uri")
                    .param("uri", fromUri)
                    .query(String.class)
                    .single());
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean hasAliasFrom(String fromUri) {
        return jdbc.sql("SELECT count(*) FROM uri_alias WHERE from_uri = :uri")
                .param("uri", fromUri)
                .query(Long.class)
                .single() > 0;
    }

    public void insertAlias(String fromUri, String toUri) {
        jdbc.sql("""
                INSERT INTO uri_alias (from_uri, to_uri, created_at)
                VALUES (:from, :to, now())
                """)
                .param("from", fromUri)
                .param("to", toUri)
                .update();
    }

    public Optional<Tombstone> findTombstone(String uri) {
        return jdbc.sql("""
                SELECT uri, reason, tombstoned_at
                  FROM uri_tombstone
                 WHERE uri = :uri
                """)
                .param("uri", uri)
                .query((rs, rowNum) -> new Tombstone(
                        rs.getString("uri"),
                        rs.getString("reason"),
                        rs.getObject("tombstoned_at", OffsetDateTime.class)))
                .optional();
    }

    public void insertTombstone(String uri, String reason, OffsetDateTime tombstonedAt) {
        jdbc.sql("""
                INSERT INTO uri_tombstone (uri, reason, tombstoned_at)
                VALUES (:uri, :reason, :tombstonedAt)
                ON CONFLICT (uri) DO NOTHING
                """)
                .param("uri", uri)
                .param("reason", reason)
                .param("tombstonedAt", tombstonedAt)
                .update();
    }

    private static UriRecord toRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UriRecord(
                rs.getString("uri"),
                rs.getString("tool"),
                rs.getString("native_id"),
                rs.getString("deep_link"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    public record Tombstone(String uri, String reason, OffsetDateTime tombstonedAt) {
    }
}
