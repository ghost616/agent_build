package com.ghost616.platform.migration;

import com.ghost616.platform.config.MessageSchemaMigration;
import com.ghost616.platform.config.PrimarySchemaMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaMigrationTest {

    private static final int PRIMARY_ALTER_COUNT = 92;
    private static final int MESSAGE_ALTER_COUNT = 16;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ApplicationArguments applicationArguments;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    private DriverManagerDataSource h2DataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:schema_migration;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Test
    void primary_nullBackfill_所有表执行正确SQL() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(4)).update(sqlCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();
        assertEquals(4, sqls.size());

        assertTrue(sqls.get(0).contains("\"session_tool\""));
        assertTrue(sqls.get(1).contains("\"agent_tool\""));
        assertTrue(sqls.get(2).contains("\"agent_skill\""));
        assertTrue(sqls.get(3).contains("\"session_skill\""));
        for (String sql : sqls) {
            assertTrue(sql.contains("SET \"session_auth\" = 0"));
            assertTrue(sql.contains("WHERE \"session_auth\" IS NULL"));
        }
    }

    @Test
    void primary_nullBackfill_update抛出异常时捕获并记录warn_不中断启动() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenThrow(new DataAccessException("mock db error") {});

        assertDoesNotThrow(() -> new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments));
    }

    @Test
    void primary_nullBackfill_部分表异常不影响其他表回填() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(contains("session_tool"))).thenThrow(new DataAccessException("mock error") {});
        when(jdbcTemplate.update(contains("agent_tool"))).thenReturn(3);
        when(jdbcTemplate.update(contains("agent_skill"))).thenReturn(0);
        when(jdbcTemplate.update(contains("session_skill"))).thenReturn(1);

        assertDoesNotThrow(() -> new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments));
        verify(jdbcTemplate, times(4)).update(anyString());
    }

    @Test
    void primary_alterTable_所有迁移正常执行() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        assertDoesNotThrow(() -> new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments));
        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(anyString());
    }

    @Test
    void primary_alterTable_SQLException列已存在时跳过不中断() {
        doThrow(new RuntimeException(new SQLException("duplicate column name: auth_config")))
                .when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        assertDoesNotThrow(() -> new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments));
        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(anyString());
    }

    @Test
    void primary_alterTable_未知异常记录error日志不中断() {
        doThrow(new RuntimeException("connection lost"))
                .when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        assertDoesNotThrow(() -> new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments));
        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(anyString());
    }

    @Test
    void primary_alterTable_带DEFAULT值的列生成正确SQL() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(sqlCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();

        assertTrue(sqls.stream().anyMatch(s -> s.contains("DEFAULT 0")));
        assertTrue(sqls.stream().anyMatch(s -> s.contains("DEFAULT 10")));
    }

    @Test
    void primary_alterTable_不带DEFAULT值的列生成SQL不含DEFAULT关键字() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(sqlCaptor.capture());
        long defaultCount = sqlCaptor.getAllValues().stream()
                .filter(s -> s.contains("DEFAULT")).count();
        assertTrue(defaultCount > 0 && defaultCount < PRIMARY_ALTER_COUNT);
    }

    @Test
    void primary_migration_包含total_token_used列() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(sqlCaptor.capture());
        assertTrue(sqlCaptor.getAllValues().stream()
                .anyMatch(s -> s.contains("total_token_used") && s.contains("BIGINT")));
    }

    @Test
    void primary_migration_包含sub_session_open_mode列() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(sqlCaptor.capture());
        assertTrue(sqlCaptor.getAllValues().stream()
                .anyMatch(s -> s.contains("sub_session_open_mode") && s.contains("VARCHAR"))
                && sqlCaptor.getAllValues().stream()
                .anyMatch(s -> s.contains("sub_session_open_mode") && s.contains("'TOOL_CALL'")));
    }

    @Test
    void primary_migration_不含message与agent_log列() {
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        new PrimarySchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(PRIMARY_ALTER_COUNT)).execute(sqlCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();
        assertTrue(sqls.stream().noneMatch(s -> s.contains("\"message\"")),
                "primary migration should not touch message table");
        assertTrue(sqls.stream().noneMatch(s -> s.contains("\"agent_log\"")),
                "primary migration should not touch agent_log table");
    }

    @Test
    void message_alterTable_所有迁移正常执行() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(h2DataSource());
        doNothing().when(jdbcTemplate).execute(anyString());

        new MessageSchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(MESSAGE_ALTER_COUNT)).execute(anyString());
    }

    @Test
    void message_alterTable_SQLException列已存在时跳过不中断() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(h2DataSource());
        doThrow(new RuntimeException(new SQLException("duplicate column name: tool_result")))
                .when(jdbcTemplate).execute(anyString());

        assertDoesNotThrow(() -> new MessageSchemaMigration(jdbcTemplate).run(applicationArguments));
        verify(jdbcTemplate, times(MESSAGE_ALTER_COUNT)).execute(anyString());
    }

    @Test
    void message_alterTable_未知异常记录error日志不中断() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(h2DataSource());
        doThrow(new RuntimeException("connection lost"))
                .when(jdbcTemplate).execute(anyString());

        assertDoesNotThrow(() -> new MessageSchemaMigration(jdbcTemplate).run(applicationArguments));
        verify(jdbcTemplate, times(MESSAGE_ALTER_COUNT)).execute(anyString());
    }

    @Test
    void message_migration_包含message与agent_log列() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(h2DataSource());
        doNothing().when(jdbcTemplate).execute(anyString());

        new MessageSchemaMigration(jdbcTemplate).run(applicationArguments);

        verify(jdbcTemplate, times(MESSAGE_ALTER_COUNT)).execute(sqlCaptor.capture());
        List<String> sqls = sqlCaptor.getAllValues();
        assertTrue(sqls.stream().anyMatch(s -> s.contains("\"message\"") && s.contains("tool_result")),
                "message migration should generate ALTER TABLE message ADD COLUMN tool_result");
        assertTrue(sqls.stream().anyMatch(s -> s.contains("\"agent_log\"") && s.contains("session_variables") && s.contains("TEXT")),
                "should generate ALTER TABLE agent_log ADD COLUMN session_variables TEXT");
        assertTrue(sqls.stream().anyMatch(s -> s.contains("\"agent_log\"") && s.contains("conversation_variables") && s.contains("TEXT")),
                "should generate ALTER TABLE agent_log ADD COLUMN conversation_variables TEXT");
    }
}
