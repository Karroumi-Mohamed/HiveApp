package com.hiveapp.platform.security;

import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Opt-in real database run, always in its own random schema, never public/user application tables.
 */
@EnabledIfEnvironmentVariable(
    named = "HIVEAPP_NOTIFICATION_TEST_PG_URL",
    matches = "jdbc:postgresql:.*")
class NotificationPostgresIntegrationTest extends NotificationSystemIntegrationTest {
  private static final String SCHEMA =
      "notification_test_" + UUID.randomUUID().toString().replace("-", "");
  private static boolean created;

  private static Properties credentials() {
    var p = new Properties();
    p.setProperty(
        "user", System.getenv().getOrDefault("HIVEAPP_NOTIFICATION_TEST_PG_USER", "postgres"));
    p.setProperty(
        "password", System.getenv().getOrDefault("HIVEAPP_NOTIFICATION_TEST_PG_PASSWORD", ""));
    return p;
  }

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) throws Exception {
    String url = System.getenv("HIVEAPP_NOTIFICATION_TEST_PG_URL");
    try (var connection = DriverManager.getConnection(url, credentials());
        var statement = connection.createStatement()) {
      statement.execute("create schema " + SCHEMA);
      created = true;
    }
    registry.add(
        "spring.datasource.url",
        () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", () -> credentials().getProperty("user"));
    registry.add("spring.datasource.password", () -> credentials().getProperty("password"));
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
  }

  @AfterAll
  static void dropIsolatedSchema() throws Exception {
    if (!created) return;
    try (var connection =
            DriverManager.getConnection(
                System.getenv("HIVEAPP_NOTIFICATION_TEST_PG_URL"), credentials());
        var statement = connection.createStatement()) {
      statement.execute("drop schema " + SCHEMA + " cascade");
    }
  }
}
