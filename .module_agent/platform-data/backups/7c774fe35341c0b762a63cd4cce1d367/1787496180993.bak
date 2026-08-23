package com.ghost616.platform.repository;

import com.ghost616.platform.entity.Message;
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
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageMapperTest {

    private static final String JDBC_URL = "jdbc:h2:mem:mapper_test;DB_CLOSE_DELAY=-1";

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE IF EXISTS message");
                st.execute("CREATE TABLE message (" +
                        "id BIGINT PRIMARY KEY," +
                        "session_id BIGINT," +
                        "role VARCHAR(20)," +
                        "content VARCHAR(1000)," +
                        "reasoning VARCHAR(1000)," +
                        "sequence_num INT," +
                        "tool_call_id VARCHAR(100)," +
                        "tool_result VARCHAR(1000)," +
                        "token_usage VARCHAR(1000)," +
                        "rollback TINYINT NOT NULL DEFAULT 0," +
                        "conversation_id VARCHAR(50)," +
                        "create_time TIMESTAMP)");
            }
        }

        Environment environment = new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", ""));
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MessageMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    private void insert(long id, String conversationId, int rollback, String createTime) throws Exception {
        insertMessage(id, 100L, "user", (int) id, rollback, conversationId, createTime);
    }

    private void insertMessage(long id, long sessionId, String role, int sequenceNum, int rollback,
                               String conversationId, String createTime) throws Exception {
        String sql = "INSERT INTO message (id, session_id, role, content, sequence_num, rollback, conversation_id, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(JDBC_URL, "sa", "")) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.setLong(2, sessionId);
                ps.setString(3, role);
                ps.setString(4, "content-" + id);
                ps.setInt(5, sequenceNum);
                ps.setInt(6, rollback);
                ps.setString(7, conversationId);
                ps.setString(8, createTime);
                ps.executeUpdate();
            }
        }
    }

    @Test
    void selectByConversationId_按会话精确查询且按createTime升序() throws Exception {
        insert(1, "conv-1", 0, "2026-01-01 10:00:00");
        insert(2, "conv-1", 0, "2026-01-01 09:00:00");
        insert(3, "conv-1", 0, "2026-01-01 11:00:00");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            List<Message> result = mapper.selectByConversationId("conv-1");

            assertEquals(3, result.size());
            assertEquals(2L, result.get(0).getId());
            assertEquals(1L, result.get(1).getId());
            assertEquals(3L, result.get(2).getId());
        }
    }

    @Test
    void selectByConversationId_过滤已回滚记录() throws Exception {
        insert(1, "conv-1", 0, "2026-01-01 10:00:00");
        insert(2, "conv-1", 1, "2026-01-01 11:00:00");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            List<Message> result = mapper.selectByConversationId("conv-1");

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
        }
    }

    @Test
    void selectByConversationId_只返回匹配会话的消息() throws Exception {
        insert(1, "conv-1", 0, "2026-01-01 10:00:00");
        insert(2, "conv-2", 0, "2026-01-01 10:00:00");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            List<Message> result = mapper.selectByConversationId("conv-1");

            assertEquals(1, result.size());
            assertEquals("conv-1", result.get(0).getConversationId());
        }
    }

    @Test
    void selectByConversationId_无匹配返回空列表() throws Exception {
        insert(1, "conv-1", 0, "2026-01-01 10:00:00");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            List<Message> result = mapper.selectByConversationId("conv-none");

            assertTrue(result.isEmpty());
        }
    }

    @Test
    void countUserMessages_统计user且未回滚消息总数() throws Exception {
        insertMessage(1, 100L, "user", 1, 0, "conv-1", "2026-01-01 10:00:00");
        insertMessage(2, 100L, "assistant", 2, 0, "conv-1", "2026-01-01 10:00:01");
        insertMessage(3, 100L, "user", 3, 0, "conv-1", "2026-01-01 10:00:02");
        insertMessage(4, 100L, "user", 4, 1, "conv-1", "2026-01-01 10:00:03");
        insertMessage(5, 200L, "user", 1, 0, "conv-2", "2026-01-01 10:00:04");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            assertEquals(2L, mapper.countUserMessages(100L));
            assertEquals(1L, mapper.countUserMessages(200L));
            assertEquals(0L, mapper.countUserMessages(999L));
        }
    }

    @Test
    void findNthUserSequenceNum_按sequenceNum升序返回第n个user未回滚消息() throws Exception {
        insertMessage(1, 100L, "user", 1, 0, "conv-1", "2026-01-01 10:00:00");
        insertMessage(2, 100L, "assistant", 2, 0, "conv-1", "2026-01-01 10:00:01");
        insertMessage(3, 100L, "user", 3, 0, "conv-1", "2026-01-01 10:00:02");
        insertMessage(4, 100L, "assistant", 4, 1, "conv-1", "2026-01-01 10:00:03");
        insertMessage(5, 100L, "user", 5, 1, "conv-1", "2026-01-01 10:00:04");
        insertMessage(6, 100L, "user", 7, 0, "conv-1", "2026-01-01 10:00:05");
        insertMessage(7, 100L, "user", 6, 0, "conv-1", "2026-01-01 10:00:06");

        try (SqlSession session = sqlSessionFactory.openSession()) {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            assertEquals(1, mapper.findNthUserSequenceNum(100L, 0));
            assertEquals(3, mapper.findNthUserSequenceNum(100L, 1));
            assertEquals(6, mapper.findNthUserSequenceNum(100L, 2));
            assertEquals(7, mapper.findNthUserSequenceNum(100L, 3));
            assertTrue(mapper.findNthUserSequenceNum(100L, 4) == null);
        }
    }
}
