package org.eda.fixtures.model;

import java.util.Map;

public record Owners(
        Map<String, String> byTool,
        String fallback,
        String note) {
}
