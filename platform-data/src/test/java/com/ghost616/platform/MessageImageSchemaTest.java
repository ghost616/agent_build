package com.ghost616.platform;

import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageImageSchemaTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:message_image_schema;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private String readResource(String name) throws IOException {
        try (InputStream in = MessageImageSchemaTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, "resource not found on classpath: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void primarySchemaSqlShouldNotContainMessageImageTable() throws IOException {
        String sql = readResource("schema.sql");
        assertFalse(sql.contains("message_image"),
                "schema.sql should not contain message_image table (message datasource table)");
    }

    @Test
    void messageSchemaSqlShouldContainMessageImageTableAndIndex() throws IOException {
        String sql = readResource("schema-message.sql");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS message_image"),
                "schema-message.sql should contain message_image table");
        assertTrue(sql.contains("message_id"),
                "schema-message.sql message_image should contain message_id column");
        assertTrue(sql.contains("img_id"),
                "schema-message.sql message_image should contain img_id column");
        assertTrue(sql.contains("img_text"),
                "schema-message.sql message_image should contain img_text column");
        assertTrue(sql.contains("idx_message_image_message_id"),
                "schema-message.sql should contain message_image message_id index");
    }

    @Test
    void messageSchemaSqlExecutesOnH2WithMessageImageTable() throws Exception {
        String sql = readResource("schema-message.sql");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            }
            try (Statement st = conn.createStatement()) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = 'PUBLIC'")) {
                    boolean hasMessageImage = false;
                    while (rs.next()) {
                        if ("MESSAGE_IMAGE".equalsIgnoreCase(rs.getString(1))) {
                            hasMessageImage = true;
                        }
                    }
                    assertTrue(hasMessageImage, "message_image table should exist in message datasource");
                }
            }
        }
    }

    @Test
    void messageImageMapperShouldUseMessageDatasource() throws IOException {
        Path path = Paths.get("src/main/java/com/ghost616/platform/repository/MessageImageMapper.java");
        assertTrue(Files.exists(path), "MessageImageMapper source file should exist");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(content.contains("@DS(\"message\")"),
                "MessageImageMapper should be annotated with @DS(\"message\")");
        assertTrue(content.contains("extends BaseMapper<MessageImage>"),
                "MessageImageMapper should extend BaseMapper<MessageImage>");
    }

    @Test
    void messageImageEntityShouldDeclareMappingFields() throws IOException {
        Path path = Paths.get("src/main/java/com/ghost616/platform/entity/MessageImage.java");
        assertTrue(Files.exists(path), "MessageImage entity source file should exist");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(content.contains("@TableName(\"message_image\")"),
                "MessageImage should be annotated with @TableName(\"message_image\")");
        assertTrue(content.contains("@TableField(\"message_id\")"),
                "MessageImage should map message_id field");
        assertTrue(content.contains("@TableField(\"img_id\")"),
                "MessageImage should map img_id field");
        assertTrue(content.contains("@TableField(\"img_text\")"),
                "MessageImage should map img_text field");
    }

    @Test
    void messageMigrationShouldContainMessageImageMigrations() throws IOException {
        Path path = Paths.get("src/main/java/com/ghost616/platform/config/MessageSchemaMigration.java");
        assertTrue(Files.exists(path), "MessageSchemaMigration source file should exist");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(content.contains("new Migration(\"message_image\", \"message_id\", \"BIGINT\", null)"),
                "missing migration: message_image.message_id");
        assertTrue(content.contains("new Migration(\"message_image\", \"img_id\", \"VARCHAR(255)\", null)"),
                "missing migration: message_image.img_id");
        assertTrue(content.contains("new Migration(\"message_image\", \"img_text\", \"MEDIUMTEXT\", null)"),
                "missing migration: message_image.img_text");
    }
}
