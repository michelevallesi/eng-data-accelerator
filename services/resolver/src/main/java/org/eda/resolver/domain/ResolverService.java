package org.eda.resolver.domain;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.eda.resolver.events.UriCloudEvents;
import org.eda.resolver.persistence.OutboxRepository;
import org.eda.resolver.persistence.UriRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResolverService {

    private final UriGenerator uriGenerator;
    private final UriRepository uriRepository;
    private final OutboxRepository outboxRepository;
    private final UriCloudEvents events;
    private final ToolRegistry toolRegistry;
    private final Clock clock;

    public ResolverService(UriGenerator uriGenerator,
                           UriRepository uriRepository,
                           OutboxRepository outboxRepository,
                           UriCloudEvents events,
                           ToolRegistry toolRegistry,
                           Clock clock) {
        this.uriGenerator = uriGenerator;
        this.uriRepository = uriRepository;
        this.outboxRepository = outboxRepository;
        this.events = events;
        this.toolRegistry = toolRegistry;
        this.clock = clock;
    }

    @Transactional
    public UriRecord mint(String tool, String nativeId) {
        Optional<UriRecord> existing = uriRepository.findByToolAndNativeId(tool, nativeId);
        if (existing.isPresent()) {
            return existing.get();
        }
        String namespace = toolRegistry.namespaceOf(tool)
                .orElseThrow(() -> new ResolverExceptions.UnknownToolException(tool));
        String uri = uriGenerator.generate(namespace, UUID.randomUUID()).toString();
        OffsetDateTime now = OffsetDateTime.now(clock);
        UriRecord record = new UriRecord(uri, tool, nativeId, toolRegistry.deepLinkFor(tool, nativeId), now);
        uriRepository.insert(record);
        outboxRepository.insert(events.minted(uri, tool, nativeId, now));
        return record;
    }

    /**
     * Imports a pre-minted URI during seeding. Fails if the (tool, nativeId) already
     * exists. Separate from {@link #mint(String, String)} so the mint path stays
     * UUID-only (AD-03).
     */
    @Transactional
    public UriRecord importSeeded(String tool, String nativeId, String uri) {
        if (uriRepository.findByUri(uri).isPresent()) {
            throw new IllegalStateException("already imported: " + uri);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        UriRecord record = new UriRecord(uri, tool, nativeId, toolRegistry.deepLinkFor(tool, nativeId), now);
        uriRepository.insert(record);
        outboxRepository.insert(events.minted(uri, tool, nativeId, now));
        return record;
    }

    @Transactional
    public void alias(String fromUri, String toUri) {
        if (uriRepository.findByUri(fromUri).isEmpty()) {
            throw new ResolverExceptions.UnknownUriException(fromUri);
        }
        if (uriRepository.findByUri(toUri).isEmpty()) {
            throw new ResolverExceptions.UnknownUriException(toUri);
        }
        if (uriRepository.hasAliasFrom(fromUri)) {
            throw new ResolverExceptions.AliasConflictException(fromUri);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        uriRepository.insertAlias(fromUri, toUri);
        outboxRepository.insert(events.aliased(fromUri, toUri, now));
    }

    @Transactional
    public void tombstone(String uri, String reason) {
        if (uriRepository.findByUri(uri).isEmpty()) {
            throw new ResolverExceptions.UnknownUriException(uri);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        uriRepository.insertTombstone(uri, reason, now);
        outboxRepository.insert(events.tombstoned(uri, reason, now));
    }

    @Transactional(readOnly = true)
    public ResolvedUri resolve(String uri) {
        UriRecord record = uriRepository.findByUri(uri)
                .orElseThrow(() -> new ResolverExceptions.UnknownUriException(uri));

        Optional<UriRepository.Tombstone> tomb = uriRepository.findTombstone(uri);
        if (tomb.isPresent()) {
            throw new ResolverExceptions.TombstonedUriException(uri, tomb.get().reason(), tomb.get().tombstonedAt());
        }

        Optional<String> aliasTo = uriRepository.aliasTarget(uri);
        if (aliasTo.isPresent()) {
            UriRecord target = uriRepository.findByUri(aliasTo.get())
                    .orElseThrow(() -> new ResolverExceptions.UnknownUriException(aliasTo.get()));
            return new ResolvedUri(uri, target.tool(), target.nativeId(), target.deepLink(), target.uri());
        }
        return new ResolvedUri(uri, record.tool(), record.nativeId(), record.deepLink(), uri);
    }
}
