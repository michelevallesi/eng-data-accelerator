package org.eda.fixtures.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Tool(
        @JsonProperty("Tool") String name,
        @JsonProperty("Role") String role,
        @JsonProperty("URI namespace") String uriNamespace,
        @JsonProperty("Access") @JsonAlias("Adapter") String access) {
}
