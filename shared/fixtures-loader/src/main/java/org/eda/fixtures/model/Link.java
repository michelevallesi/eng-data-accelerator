package org.eda.fixtures.model;

public record Link(
        String id,
        String source,
        String target,
        String relation,
        String state) {
}
