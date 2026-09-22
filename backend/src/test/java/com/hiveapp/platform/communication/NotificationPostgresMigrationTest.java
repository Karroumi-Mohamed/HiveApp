package com.hiveapp.platform.communication;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(
    named = "HIVEAPP_NOTIFICATION_TEST_PG_URL",
    matches = "jdbc:postgresql:.*")
class NotificationPostgresMigrationTest {
  @Test
  void upgradeIsRepeatableAndPreservesHistoricalMessageAndReplyData() throws Exception {
    String schema = "notification_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var credentials = new Properties();
    credentials.setProperty(
        "user", System.getenv().getOrDefault("HIVEAPP_NOTIFICATION_TEST_PG_USER", "postgres"));
    credentials.setProperty(
        "password", System.getenv().getOrDefault("HIVEAPP_NOTIFICATION_TEST_PG_PASSWORD", ""));
    try (var connection =
            DriverManager.getConnection(
                System.getenv("HIVEAPP_NOTIFICATION_TEST_PG_URL"), credentials);
        var statement = connection.createStatement()) {
      statement.execute("create schema " + schema);
      try {
        statement.execute("set search_path to " + schema);
        String sql;
        try (var resource =
            getClass().getResourceAsStream("/db/manual/2026-09-22-notifications.sql")) {
          assertThat(resource).isNotNull();
          sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        statement.execute(sql);
        UUID entry = UUID.randomUUID(), account = UUID.randomUUID(), actor = UUID.randomUUID();
        statement.execute(
            "insert into"
                + " communication_entries(id,created_at,updated_at,account_id,source,source_id,kind,purpose,message_title,message_body,available_at,replies)"
                + " values ('"
                + entry
                + "',now(),now(),'"
                + account
                + "','ADMIN','"
                + UUID.randomUUID()
                + "','MESSAGE','SERVICE','Historical','Keep this content',now(),true)");
        statement.execute(
            "insert into"
                + " communication_replies(id,created_at,updated_at,entry_id,actor_id,command_id,reply_body)"
                + " values ('"
                + UUID.randomUUID()
                + "',now(),now(),'"
                + entry
                + "','"
                + actor
                + "','"
                + UUID.randomUUID()
                + "','Keep this historical reply')");
        statement.execute("alter table communication_entries drop column audience");
        statement.execute("alter table communication_entries alter column account_id set not null");
        statement.execute(sql);
        statement.execute(sql);
        try (var result =
            statement.executeQuery(
                "select kind,replies,closed,audience,message_body from communication_entries where"
                    + " id='"
                    + entry
                    + "'")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("NOTICE");
          assertThat(result.getBoolean(2)).isFalse();
          assertThat(result.getBoolean(3)).isTrue();
          assertThat(result.getString(4)).isEqualTo("ACCOUNT");
          assertThat(result.getString(5)).isEqualTo("Keep this content");
        }
        try (var result = statement.executeQuery("select reply_body from communication_replies")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("Keep this historical reply");
        }
        assertThatThrownBy(
                () ->
                    statement.execute(
                        "insert into"
                            + " notification_events(id,created_at,updated_at,dedupe_key,definition_key,audience,available_at,state,next_attempt_at,message_title,message_body)"
                            + " values ('"
                            + UUID.randomUUID()
                            + "',now(),now(),'invalid','account.information','MEMBER',now(),'PENDING',now(),'Invalid','Missing"
                            + " account and recipient')"))
            .isInstanceOf(java.sql.SQLException.class);
      } finally {
        statement.execute("rollback");
        statement.execute("set search_path to public");
        statement.execute("drop schema " + schema + " cascade");
      }
    }
  }
}
