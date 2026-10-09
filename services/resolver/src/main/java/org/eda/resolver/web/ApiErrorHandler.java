package org.eda.resolver.web;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eda.resolver.domain.ResolverExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(ResolverExceptions.TombstonedUriException.class)
    public ResponseEntity<Map<String, Object>> tombstoned(ResolverExceptions.TombstonedUriException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "Gone");
        body.put("status", 410);
        body.put("detail", e.getMessage());
        body.put("tombstonedAt", e.tombstonedAt() != null ? e.tombstonedAt().toString() : null);
        if (e.reason() != null) {
            body.put("reason", e.reason());
        }
        return ResponseEntity.status(HttpStatus.GONE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(ResolverExceptions.UnknownUriException.class)
    public ResponseEntity<Map<String, Object>> unknownUri(ResolverExceptions.UnknownUriException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ResolverExceptions.UnknownToolException.class)
    public ResponseEntity<Map<String, Object>> unknownTool(ResolverExceptions.UnknownToolException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ResolverExceptions.AliasConflictException.class)
    public ResponseEntity<Map<String, Object>> aliasConflict(ResolverExceptions.AliasConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("timestamp", OffsetDateTime.now().toString());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
