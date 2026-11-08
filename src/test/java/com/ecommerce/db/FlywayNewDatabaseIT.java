package com.ecommerce.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new, empty PostgreSQL (the database production uses; other tests run on H2 with a
 * Hibernate-built schema): the Flyway migrations build it and Hibernate's validation finds every
 * table and column the entities expect, the way the prod profile starts. Needs Docker.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
})
@ActiveProfiles("e2e")
@DisplayName("Flyway on a new PostgreSQL database")
class FlywayNewDatabaseIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("migrates, passes Hibernate's schema validation and takes the seed data")
    void migratesAndValidates() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isGreaterThanOrEqualTo(3);
        // TestDataInitializer (e2e profile) inserted users and products through JPA
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isGreaterThanOrEqualTo(3);
    }
}
