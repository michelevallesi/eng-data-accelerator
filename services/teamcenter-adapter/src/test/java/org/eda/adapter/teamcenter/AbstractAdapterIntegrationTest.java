package org.eda.adapter.teamcenter;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Push the scheduled poll and relay out of the test window; tests drive
                // both through TrsPoller.pollOnce() + KafkaRelay.relayOnce().
                "eda.teamcenter.poll-interval-ms=3600000",
                "eda.teamcenter.initial-delay-ms=3600000",
                "eda.teamcenter.outbox-relay-interval-ms=3600000",
                "eda.teamcenter.outbox-relay-initial-delay-ms=3600000"
        })
@SuppressWarnings("resource")
public abstract class AbstractAdapterIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("eda")
                    .withUsername("eda")
                    .withPassword("dev");

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void sharedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
