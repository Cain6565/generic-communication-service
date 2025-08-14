package org.argela.genericcommunicationservice.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL bağlantı durumu health check.
 * /actuator/health veya /actuator/db üzerinden görülebilir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Health health() {
        try {
            jdbcTemplate.execute("SELECT 1");
            log.debug("Database connection OK");
            return Health.up().withDetail("database", "PostgreSQL").build();
        } catch (Exception e) {
            log.error("Database connection FAILED", e);
            return Health.down(e).withDetail("database", "PostgreSQL").build();
        }
    }
}
