package org.eda.fixtures.model;

import java.util.List;
import java.util.Map;

public record MaturityScale(
        Map<String, List<String>> order,
        Map<String, Map<String, String>> nativeToCanonical,
        String note) {
}
