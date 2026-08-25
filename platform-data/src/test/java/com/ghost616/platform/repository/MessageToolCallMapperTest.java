package com.ghost616.platform.repository;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MessageToolCallMapper.deleteByMessageIds 软删除行为集成测试（H2 内存库 + 原生 MyBatis SqlSession）。
 */
class MessageToolCallMapperTest {

    private static final String JDBC_URL = "jdbc:h2:mem:message_tool_call_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS message_tool_call");
                st.execute("CREATE TABLE message_tool_call (" +
                        "id BIGINT PRIMARY KEY," +
                        "message_id BIGINT," +
                        "tool_call_id VARCHAR(100)," +
                        "tool_call_name VARCHAR(100)," +
                        "tool_call_arguments VARCHAR(1000)," +
                        "type VARCHAR(32) DEFAULT 'function'," +
                        "web_search_call VARCHAR(1000)," +
                        "custom_tool_call VARCHAR(1000)," +
                        "deleted INTEGER DEFAULT 0)");
            }
        }

        Environment environment = new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", ""));
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MessageToolCallMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    private void insert(long id, long messageId, int deleted) throws Exception {
        String sql = "INSERT INTO message_tool_call (id, message_id, tool_call_id, deleted) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.setLong(2, messageId);
                ps.setString(3, "tool-" + id);
                ps.setInt(4, deleted);
                ps.executeUpdate();
            }
        }
    }

    private int countByFlag(long messageId, int deleted) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM message_tool_call WHERE message_id = ? AND deleted = ?")) {
                ps.setLong(1, messageId);
                ps.setInt(2, deleted);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        }
    }

    @Test
    void deleteByMessageIds_软删除匹配消息的工具调用记录() throws Exception {
        insert(1, 100L, 0);
        insert(2, 100L, 0);
        insert(3, 200L, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            MessageToolCallMapper mapper = session.getMapper(MessageToolCallMapper.class);
            int affected = mapper.deleteByMessageIds(List.of(100L));

            assertEquals(2, affected);
            assertEquals(2, countByFlag(100L, 1), "匹配记录应被软删除");
            assertEquals(2, countByFlag(100L, 0) + countByFlag(100L, 1), "记录应保留而非物理删除");
            assertEquals(0, countByFlag(200L, 1), "其它消息记录不应被删除");
            assertEquals(1, countByFlag(200L, 0));
        }
    }

    @Test
    void deleteByMessageIds_不影响已软删除记录_幂等() throws Exception {
        insert(1, 100L, 1);
        insert(2, 100L, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            MessageToolCallMapper mapper = session.getMapper(MessageToolCallMapper.class);
            int affected = mapper.deleteByMessageIds(List.of(100L));

            assertEquals(1, affected, "已软删除记录不应重复更新");
            assertEquals(2, countByFlag(100L, 1));
            assertEquals(2, countByFlag(100L, 0) + countByFlag(100L, 1));
        }
    }

    @Test
    void deleteByMessageIds_不匹配时返回0() throws Exception {
        insert(1, 100L, 0);

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            MessageToolCallMapper mapper = session.getMapper(MessageToolCallMapper.class);
            int affected = mapper.deleteByMessageIds(List.of(999L));

            assertEquals(0, affected);
            assertEquals(0, countByFlag(100L, 1));
            assertEquals(1, countByFlag(100L, 0));
        }
    }
}