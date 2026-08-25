package com.ghost616.platform;

import com.ghost616.platform.config.MessageSchemaMigration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageToolCallSchemaTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:message_tool_call_schema;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private String readResource(String name) throws IOException {
        try (InputStream in = MessageToolCallSchemaTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "resource not found on classpath: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void primarySchemaSqlShouldNotContainMessageToolCallTable() throws IOException {
        String sql = readResource("schema.sql");
        assertFalse(sql.contains("message_tool_call"),
                "schema.sql should not contain message_tool_call table (moved to message datasource)");
    }

    @Test
    void messageSchemaSqlShouldContainMessageToolCallTableAndIndexes() throws IOException {
        String sql = readResource("schema-message.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS message_tool_call"),
                "schema-message.sql should contain message_tool_call table");
        assertTrue(sql.contains("idx_message_tool_call_message_id"),
                "schema-message.sql should contain message_tool_call message_id index");
        assertTrue(sql.contains("idx_message_tool_call_tool_call_id"),
                "schema-message.sql should contain message_tool_call tool_call_id index");
    }

    @Test
    void messageSchemaSqlExecutesOnH2WithAllThreeTables() throws Exception {
        String sql = readResource("schema-message.sql");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = 'PUBLIC'")) {
                    boolean hasMessage = false;
                    boolean hasAgentLog = false;
                    boolean hasMessageToolCall = false;
                    while (rs.next()) {
                        String tableName = rs.getString(1);
                        if ("MESSAGE".equalsIgnoreCase(tableName)) {
                            hasMessage = true;
                        }
                        if ("AGENT_LOG".equalsIgnoreCase(tableName)) {
                            hasAgentLog = true;
                        }
                        if ("MESSAGE_TOOL_CALL".equalsIgnoreCase(tableName)) {
                            hasMessageToolCall = true;
                        }
                    }
                    assertTrue(hasMessage, "message table should exist in message datasource");
                    assertTrue(hasAgentLog, "agent_log table should exist in message datasource");
                    assertTrue(hasMessageToolCall, "message_tool_call table should exist in message datasource");
                }
            }
        }
    }

    @Test
    void messageToolCallMapperShouldUseMessageDatasource() throws IOException {
        Path path = Paths.get("src/main/java/com/ghost616/platform/repository/MessageToolCallMapper.java");
        assertTrue(Files.exists(path), "MessageToolCallMapper source file should exist");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(content.contains("@DS(\"message\")"),
                "MessageToolCallMapper should be annotated with @DS(\"message\")");
    }

    @Test
    void messageMigrationShouldContainMessageToolCallMigrations() throws IOException {
        Path path = Paths.get("src/main/java/com/ghost616/platform/config/MessageSchemaMigration.java");
        assertTrue(Files.exists(path), "MessageSchemaMigration source file should exist");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(content.contains("new Migration(\"message_tool_call\", \"type\", \"VARCHAR(32)\", \"'function'\")"),
                "missing migration: message_tool_call.type");
        assertTrue(content.contains("new Migration(\"message_tool_call\", \"web_search_call\", \"TEXT\", null)"),
                "missing migration: message_tool_call.web_search_call");
        assertTrue(content.contains("new Migration(\"message_tool_call\", \"custom_tool_call\", \"TEXT\", null)"),
                "missing migration: message_tool_call.custom_tool_call");
    }

    @Test
    void primaryMigrationShouldNotContainMessageToolCallMigrations() throws IOException {
        Path path = Paths.get("src/main/java/com/ghost616/platform/config/PrimarySchemaMigration.java");
        assertTrue(Files.exists(path), "PrimarySchemaMigration source file should exist");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertFalse(content.contains("new Migration(\"message_tool_call\""),
                "primary migration should not maintain message_tool_call table");
    }

    @Test
    void messageMigrationShouldFailFastWhenSchemaScriptFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getDataSource()).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new MessageSchemaMigration(jdbcTemplate).run(null),
                "MessageSchemaMigration should throw when schema-message.sql script fails");
        assertTrue(ex.getMessage().contains("schema-message.sql"),
                "exception message should reference schema-message.sql, but was: " + ex.getMessage());
    }
}
