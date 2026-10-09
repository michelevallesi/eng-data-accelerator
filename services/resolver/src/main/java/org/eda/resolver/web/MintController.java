package org.eda.resolver.web;

import jakarta.validation.constraints.NotBlank;
import org.eda.resolver.domain.ResolverService;
import org.eda.resolver.domain.UriRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resolver")
@Validated
public class MintController {

    private final ResolverService service;

    public MintController(ResolverService service) {
        this.service = service;
    }

    @PostMapping("/mint")
    public ResponseEntity<MintResponse> mint(@RequestBody MintRequest request) {
        if (request.tool() == null || request.tool().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.nativeId() == null || request.nativeId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UriRecord record = service.mint(request.tool(), request.nativeId());
        return ResponseEntity.ok(new MintResponse(record.uri(), record.tool(), record.nativeId()));
    }

    @PostMapping("/alias")
    public ResponseEntity<Void> alias(@RequestBody AliasRequest request) {
        service.alias(request.fromUri(), request.toUri());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tombstone")
    public ResponseEntity<Void> tombstone(@RequestBody TombstoneRequest request) {
        service.tombstone(request.uri(), request.reason());
        return ResponseEntity.noContent().build();
    }

    public record MintRequest(@NotBlank String tool, @NotBlank String nativeId) {
    }

    public record MintResponse(String uri, String tool, String nativeId) {
    }

    public record AliasRequest(@NotBlank String fromUri, @NotBlank String toUri) {
    }

    public record TombstoneRequest(@NotBlank String uri, String reason) {
    }
}
