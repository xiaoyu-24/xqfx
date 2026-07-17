package com.xqfx.requirements;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MySqlMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:5.7.32")
            .withDatabaseName("requirements_test")
            .withUsername("requirements_app")
            .withPassword("requirements_app");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesMigrationsAndEnforcesActiveVersionNameUniqueness() {
        var databaseVersion = jdbc.queryForObject("SELECT VERSION()", String.class);
        assertTrue(databaseVersion != null && databaseVersion.startsWith("5.7.32"),
                "Expected MySQL 5.7.32, actual: " + databaseVersion);
        var completedMigrations = jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class);
        assertTrue(completedMigrations >= 7);
        assertTrue(jdbc.queryForList("SHOW INDEX FROM system_versions WHERE Key_name = 'uk_system_versions_active_name'").size() > 0);
        assertTrue(jdbc.queryForList("SHOW INDEX FROM requirements WHERE Key_name = 'idx_requirements_status_deleted'").size() > 0);
        assertTrue(jdbc.queryForList("SHOW COLUMNS FROM requirements WHERE Field IN ('completed_at', 'handled_by', 'completion_description')").size() == 3);

        jdbc.update("INSERT INTO systems (name, active_name_key, owner_name, status, deleted) VALUES (?, ?, ?, ?, false)",
                "MySQL 集成测试系统", "mysql-集成测试系统", "负责人", "ACTIVE");
        var systemId = jdbc.queryForObject("SELECT id FROM systems WHERE name = ?", Long.class, "MySQL 集成测试系统");
        jdbc.update("INSERT INTO system_versions (system_id, name, active_name_key, status, deleted) VALUES (?, ?, ?, ?, false)",
                systemId, "V1.0", "v1.0", "ACTIVE");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO system_versions (system_id, name, active_name_key, status, deleted) VALUES (?, ?, ?, ?, false)",
                systemId, " v1.0 ", "v1.0", "ACTIVE"));
    }
}
