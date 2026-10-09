package org.eda.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eda.resolver.domain.ResolverService;
import org.eda.resolver.persistence.OutboxRepository;
import org.eda.resolver.persistence.UriRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * ADR-0016 under adversarial conditions: if the outbox write fails, the entire
 * transaction must roll back and no {@code uri} row must be left behind.
 */
@Import(OutboxTransactionalityTest.ThrowingOutboxConfig.class)
class OutboxTransactionalityTest extends AbstractResolverIntegrationTest {

    static final AtomicBoolean THROW_ON_INSERT = new AtomicBoolean(false);

    @Autowired ResolverService service;
    @Autowired UriRepository uriRepo;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void truncate() {
        jdbc.sql("TRUNCATE outbox, uri_tombstone, uri_alias, uri CASCADE").update();
    }

    @AfterEach
    void disarm() {
        THROW_ON_INSERT.set(false);
    }

    @Test
    void ADR_0016_outbox_failure_rolls_back_the_URI_insert() {
        THROW_ON_INSERT.set(true);

        assertThatThrownBy(() -> service.mint("Polarion", "REQ-019"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated outbox failure");

        assertThat(uriRepo.findByToolAndNativeId("Polarion", "REQ-019"))
                .as("URI row must not exist after outbox failure")
                .isEmpty();
        assertThat(uriRepo.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @TestConfiguration
    static class ThrowingOutboxConfig {
        @Bean
        @Primary
        OutboxRepository throwingOutbox(JdbcClient jdbc, ObjectMapper mapper) {
            return new OutboxRepository(jdbc, mapper) {
                @Override
                public void insert(OutboxEvent event) {
                    if (THROW_ON_INSERT.get()) {
                        throw new RuntimeException("simulated outbox failure");
                    }
                    super.insert(event);
                }
            };
        }
    }
}
