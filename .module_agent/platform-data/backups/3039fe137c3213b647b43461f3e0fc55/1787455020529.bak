package com.ghost616.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

/**
 * 消息数据源 Schema 迁移组件，负责消息数据源（message）的建表与增量列迁移。
 */
@Slf4j
@Component
@Order(1)
public class MessageSchemaMigration extends AbstractSchemaMigration implements ApplicationRunner {

    private static final String SCHEMA_MESSAGE_SQL = "schema-message.sql";

    public MessageSchemaMigration(@Qualifier("messageJdbcTemplate") JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始执行消息数据源 Schema 迁移...");

        createMessageTables();

        runAlterMigrations(List.of(
                new Migration("message", "tool_result", "TEXT", null),
                new Migration("message", "token_usage", "TEXT", null),
                new Migration("message", "conversation_id", "VARCHAR(50)", null),
                new Migration("message", "rollback", "TINYINT(1)", "0"),
                new Migration("agent_log", "session_variables", "TEXT", null),
                new Migration("agent_log", "conversation_variables", "TEXT", null),
                new Migration("message_tool_call", "type", "VARCHAR(32)", "'function'"),
                new Migration("message_tool_call", "web_search_call", "TEXT", null),
                new Migration("message_tool_call", "custom_tool_call", "TEXT", null),
                new Migration("message", "user_id", "BIGINT", "1"),
                new Migration("agent_log", "user_id", "BIGINT", "1")
        ));

        log.info("消息数据源 Schema 迁移完成");
    }

    private void createMessageTables() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource(SCHEMA_MESSAGE_SQL)));
            log.info("消息数据源建表成功: {}", SCHEMA_MESSAGE_SQL);
        } catch (Exception e) {
            log.error("消息数据源建表失败 - {}", e.getMessage());
            throw new IllegalStateException("消息数据源建表失败，应用终止启动: " + SCHEMA_MESSAGE_SQL, e);
        }
    }
}
