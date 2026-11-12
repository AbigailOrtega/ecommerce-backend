package com.ecommerce.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Production's case: a database built over time by ddl-auto=update, with UTC timestamps, an enum
 * CHECK from before SHIPMENT_PENDING existed and a missing index, upgraded the way the prod profile
 * does it (baseline at V1, then V2 onwards). Needs Docker.
 */
@Testcontainers
@DisplayName("Flyway on a database that predates it")
class FlywayLegacyDatabaseIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    @DisplayName("is baselined, gets current enum checks and indexes, and its timestamps move to Mexico City time")
    void upgradesLegacyDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        // the schema Hibernate had built (V1 is exactly that), without Flyway's history table
        Flyway.configure().dataSource(ds).target("1").load().migrate();
        jdbc.execute("DROP TABLE flyway_schema_history");
        jdbc.execute("ALTER TABLE orders DROP CONSTRAINT orders_status_check");
        jdbc.execute("ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN ('PENDING', 'CONFIRMED'))");
        jdbc.execute("DROP INDEX idx_orders_user_id");
        // Hibernate named its unique constraints uk_<hash> (prod), not V1's <table>_<column>_key
        jdbc.execute("ALTER TABLE categories RENAME CONSTRAINT categories_slug_key TO ukoa77yh4ckm4cq1enqgdlhxbg1");
        jdbc.execute("ALTER TABLE users RENAME CONSTRAINT users_email_key TO uk6dotkott2kjsp8vw4d0m25fb7");
        jdbc.execute("ALTER TABLE products DROP CONSTRAINT products_slug_key");
        jdbc.execute("CREATE UNIQUE INDEX ukostq1ec3toafnjok09y9l7dox ON products (slug)");
        jdbc.update("INSERT INTO categories (id, name, slug) VALUES (1, 'Faldas', 'faldas')");
        // an order placed at 12:00 in Mexico City, stored by the UTC server as 18:00
        jdbc.update("INSERT INTO orders (id, order_number, status, total_amount, pickup_cancelled, shipping_address, "
                + "shipping_city, shipping_state, shipping_zip_code, shipping_country, created_at, updated_at) "
                + "VALUES (1, 'ORD-LEGACY', 'PENDING', 100, false, 'Calle 1', 'CDMX', 'CDMX', '01000', 'MX', ?, ?)",
                Timestamp.valueOf("2026-03-10 18:00:00"), Timestamp.valueOf("2026-03-10 18:30:00"));

        Flyway.configure().dataSource(ds).baselineOnMigrate(true).baselineVersion("1").load().migrate();

        assertThat(jdbc.queryForObject("SELECT type FROM flyway_schema_history WHERE version = '1'", String.class))
                .as("V1 recorded as the baseline, not run").isEqualTo("BASELINE");
        assertThat(jdbc.queryForObject("SELECT created_at FROM orders WHERE id = 1", LocalDateTime.class))
                .isEqualTo(LocalDateTime.parse("2026-03-10T12:00:00"));
        assertThat(jdbc.queryForObject("SELECT updated_at FROM orders WHERE id = 1", LocalDateTime.class))
                .isEqualTo(LocalDateTime.parse("2026-03-10T12:30:00"));
        assertThatNoException().isThrownBy(() -> jdbc.update("UPDATE orders SET status = 'SHIPMENT_PENDING' WHERE id = 1"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_orders_user_id'", Integer.class))
                .isEqualTo(1);
        // V10: existing rows are store #1's; slugs and e-mails are unique per store whatever the old names were
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM categories WHERE id = 1", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM orders WHERE id = 1", Long.class)).isEqualTo(1L);
        jdbc.update("INSERT INTO tenants (id, slug, name) VALUES (2, 'papeleria', 'Papelería')");
        assertThatNoException().as("same slug in another store")
                .isThrownBy(() -> jdbc.update("INSERT INTO categories (id, name, slug, tenant_id) VALUES (2, 'Faldas', 'faldas', 2)"));
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbc.update("INSERT INTO categories (id, name, slug, tenant_id) VALUES (3, 'Otra', 'faldas', 1)"))
                .as("same slug in the same store").isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
