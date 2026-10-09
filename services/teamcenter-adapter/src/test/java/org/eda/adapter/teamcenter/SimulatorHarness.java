package org.eda.adapter.teamcenter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.eda.simulator.teamcenter.TeamcenterSimulatorApplication;
import org.eda.simulator.teamcenter.state.InMemoryTeamcenterState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class SimulatorHarness {

    private static volatile ConfigurableApplicationContext context;
    private static volatile int port;

    private SimulatorHarness() {
    }

    public static synchronized int start() {
        if (context == null) {
            context = new SpringApplicationBuilder(TeamcenterSimulatorApplication.class)
                    .properties(
                            "server.port=0",
                            "spring.main.allow-bean-definition-overriding=true",
                            "spring.autoconfigure.exclude="
                                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                    + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                                    + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                            "eda.fixtures.scenario=skid400",
                            "eda.base-domain=https://eda.example.org",
                            "spring.application.name=tc-sim-under-test")
                    .run();
            port = Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (context != null) {
                    context.close();
                }
            }));
        }
        return port;
    }

    public static String baseUrl() {
        return "http://localhost:" + port;
    }

    public static InMemoryTeamcenterState state() {
        return context.getBean(InMemoryTeamcenterState.class);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Returns the newly-minted URI from the revise response body. */
    public static ReviseResult revise(String part, String currentRev,
                                      String newRev, String description) {
        try {
            ObjectMapper m = MAPPER;
            String body = m.writeValueAsString(new ReviseBody(newRev, description));
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create(baseUrl() + "/_mutate/revise/" + part + "/" + currentRev))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response =
                    HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new IllegalStateException("revise failed: " + response.statusCode()
                        + " / " + response.body());
            }
            JsonNode node = m.readTree(response.body());
            return new ReviseResult(node.get("nativeId").asText(), node.get("uri").asText());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("revise failed", e);
        }
    }

    public record ReviseBody(String newRevision, String description) {
    }

    public record ReviseResult(String nativeId, String uri) {
    }
}
