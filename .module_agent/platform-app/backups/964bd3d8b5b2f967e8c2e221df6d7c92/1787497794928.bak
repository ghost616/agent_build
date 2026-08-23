package com.ghost616.platform.service.memory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.EmbeddingRequest;
import com.ghost616.agentbase.dto.model.EmbeddingResponse;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import com.ghost616.platform.dto.memory.MemoryRegenerateStatusDTO;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.enums.AggregationType;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.model.SessionMemoryDocument;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.service.search.SessionMemoryESClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionMemoryServiceTest {

    @Mock
    private AgentConfigMapper agentConfigMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ModelConfigMapper modelConfigMapper;
    @Mock
    private ModelInvokerManager modelInvokerManager;
    @Mock
    private ModelInvoker llmInvoker;
    @Mock
    private ModelInvoker embedInvoker;
    @Mock
    private SessionMemoryESClient sessionMemoryESClient;
    @Mock
    private ThreadVariableHandler threadVariableHandler;

    private SessionMemoryService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Message.class);
    }

    @BeforeEach
    void setUp() {
        service = new SessionMemoryService(agentConfigMapper, sessionMapper, messageMapper,
                modelConfigMapper, modelInvokerManager, sessionMemoryESClient, threadVariableHandler);
        lenient().when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong()))
                .thenReturn(true);
    }

    private AgentConfig memoryAgent(Long id) {
        AgentConfig agent = new AgentConfig();
        agent.setId(id);
        agent.setMemoryEnabled(true);
        agent.setMemoryGroupCount(2);
        agent.setVectorModelId(20L);
        agent.setModelId(10L);
        return agent;
    }

    private ModelConfig llmModel(Long id) {
        ModelConfig m = new ModelConfig();
        m.setId(id);
        m.setModelName("llm-model");
        return m;
    }

    private ModelConfig vectorModel(Long id) {
        ModelConfig m = new ModelConfig();
        m.setId(id);
        m.setModelName("embed-model");
        return m;
    }

    private Session session(Long id, Long agentId, Integer memoryPoint) {
        Session s = new Session();
        s.setId(id);
        s.setAgentId(agentId);
        s.setModelId(10L);
        s.setMemoryPointSequenceNum(memoryPoint);
        return s;
    }

    private Message message(Long id, Long sessionId, String role, String content, int seq, boolean rollback) {
        Message m = new Message();
        m.setId(id);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setSequenceNum(seq);
        m.setRollback(rollback);
        m.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(seq));
        return m;
    }

    private long epochMillis(int seq) {
        return LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(seq)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void stubInvokers() {
        when(modelConfigMapper.selectById(10L)).thenReturn(llmModel(10L));
        when(modelConfigMapper.selectById(20L)).thenReturn(vectorModel(20L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenAnswer(invocation -> {
            ModelConfigData data = invocation.getArgument(0);
            return "embed-model".equals(data.modelName()) ? embedInvoker : llmInvoker;
        });
        when(embedInvoker.embed(any(EmbeddingRequest.class))).thenReturn(
                EmbeddingResponse.builder().embeddings(List.of(
                        EmbeddingResponse.EmbeddingItem.builder().index(0).embedding(List.of(0.1f, 0.2f)).build()))
                        .build());
    }

    @Test
    @DisplayName("无 memoryEnabled=true 的智能体时直接返回")
    void aggregate_noMemoryAgents_return() {
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.aggregateSessionMemories();

        verify(sessionMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("有新消息时按组内容-主题归类-大组汇总生成文档并写入ES、更新记忆点")
    void aggregate_newMessages_writesAndUpdates() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(3L);
        when(messageMapper.findNthUserSequenceNum(100L, 1)).thenReturn(4);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 技术").build())
                .thenReturn(ChatResponse.builder().content("用户完成了数据库配置，结论为使用连接池。").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(1, docs.size());
        SessionMemoryDocument doc = docs.get(0);
        assertEquals("100", doc.getSessionId());
        assertEquals(AggregationType.GROUP, doc.getAggregationType());
        assertEquals(2, doc.getAggregationStartSeq());
        assertEquals(3, doc.getAggregationEndSeq());
        assertEquals(epochMillis(2), doc.getAggregationStartTime());
        assertEquals(epochMillis(3), doc.getAggregationEndTime());
        assertEquals("用户完成了数据库配置，结论为使用连接池。", doc.getAggregationText());
        assertEquals(2, doc.getVector().size());
        verify(llmInvoker, times(2)).invoke(any(ChatRequest.class));

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionMapper).updateById(sessionCaptor.capture());
        assertEquals(4, sessionCaptor.getValue().getMemoryPointSequenceNum());
    }

    @Test
    @DisplayName("记忆点不变时跳过")
    void aggregate_noNewMessages_skip() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 4);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);

        service.aggregateSessionMemories();

        verify(sessionMemoryESClient, never()).batchSave(any());
        verify(sessionMapper, never()).updateById(any(Session.class));
    }

    @Test
    @DisplayName("未配置向量模型时跳过")
    void aggregate_noVectorModel_skip() {
        AgentConfig agent = memoryAgent(5L);
        agent.setVectorModelId(null);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 3);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        service.aggregateSessionMemories();

        verify(sessionMemoryESClient, never()).batchSave(any());
        verify(sessionMapper, never()).updateById(any(Session.class));
    }

    @Test
    @DisplayName("会话处理失败重试5次后记录日志")
    void aggregate_failure_retries5Times() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 3);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenThrow(new RuntimeException("boom"));

        service.aggregateSessionMemories();

        verify(messageMapper, times(5)).countUserMessages(100L);
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("主题归类 LLM 返回 null 时回退为每组合并前各自成组，直接使用原始内容")
    void aggregate_summaryFail_skipGroup() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content(null).build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(2, docs.size());
        assertEquals("【user】: q1\n【assistant】: a1", docs.get(0).getAggregationText());
        assertEquals(epochMillis(2), docs.get(0).getAggregationStartTime());
        assertEquals(epochMillis(3), docs.get(0).getAggregationEndTime());
        assertEquals("【user】: q2\n【assistant】: a2", docs.get(1).getAggregationText());
        assertEquals(epochMillis(4), docs.get(1).getAggregationStartTime());
        assertEquals(epochMillis(5), docs.get(1).getAggregationEndTime());
        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("多组消息归类为同一主题时合并为一个文档，startSeq/endSeq 覆盖完整区间")
    void aggregate_multipleGroupsSameTopic_mergeToOneDocument() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 数据库\n2. 数据库").build())
                .thenReturn(ChatResponse.builder().content("汇总：围绕数据库索引与查询性能展开了深入讨论。").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(1, docs.size());
        SessionMemoryDocument doc = docs.get(0);
        assertEquals(2, doc.getAggregationStartSeq());
        assertEquals(5, doc.getAggregationEndSeq());
        assertEquals(epochMillis(2), doc.getAggregationStartTime());
        assertEquals(epochMillis(5), doc.getAggregationEndTime());
        assertEquals("汇总：围绕数据库索引与查询性能展开了深入讨论。", doc.getAggregationText());
        verify(llmInvoker, times(2)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("多组消息归类为不同主题时分别生成文档")
    void aggregate_multipleGroupsDifferentTopics_multipleDocuments() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 行政\n2. 项目").build())
                .thenReturn(ChatResponse.builder().content("汇总1：团建安排已确认。").build())
                .thenReturn(ChatResponse.builder().content("汇总2：项目上线时间确定为周五。").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(2, docs.size());
        assertEquals(2, docs.get(0).getAggregationStartSeq());
        assertEquals(3, docs.get(0).getAggregationEndSeq());
        assertEquals(epochMillis(2), docs.get(0).getAggregationStartTime());
        assertEquals(epochMillis(3), docs.get(0).getAggregationEndTime());
        assertEquals("汇总1：团建安排已确认。", docs.get(0).getAggregationText());
        assertEquals(4, docs.get(1).getAggregationStartSeq());
        assertEquals(5, docs.get(1).getAggregationEndSeq());
        assertEquals(epochMillis(4), docs.get(1).getAggregationStartTime());
        assertEquals(epochMillis(5), docs.get(1).getAggregationEndTime());
        assertEquals("汇总2：项目上线时间确定为周五。", docs.get(1).getAggregationText());
        verify(llmInvoker, times(3)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("主题归类结果无法解析时每组合并前各自成组，跳过汇总直接使用原始内容")
    void aggregate_unparseableTopics_eachGroupSeparate() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("无法识别的归类输出").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(2, docs.size());
        assertEquals(2, docs.get(0).getAggregationStartSeq());
        assertEquals(3, docs.get(0).getAggregationEndSeq());
        assertEquals("【user】: q1\n【assistant】: a1", docs.get(0).getAggregationText());
        assertEquals(4, docs.get(1).getAggregationStartSeq());
        assertEquals(5, docs.get(1).getAggregationEndSeq());
        assertEquals("【user】: q2\n【assistant】: a2", docs.get(1).getAggregationText());
        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("主题归类结果数量不匹配时回退为每组合并前各自成组，跳过汇总直接使用原始内容")
    void aggregate_mismatchedTopicCount_eachGroupSeparate() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 技术").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(2, docs.size());
        assertEquals(2, docs.get(0).getAggregationStartSeq());
        assertEquals(3, docs.get(0).getAggregationEndSeq());
        assertEquals("【user】: q1\n【assistant】: a1", docs.get(0).getAggregationText());
        assertEquals(4, docs.get(1).getAggregationStartSeq());
        assertEquals(5, docs.get(1).getAggregationEndSeq());
        assertEquals("【user】: q2\n【assistant】: a2", docs.get(1).getAggregationText());
        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("LLM 乱序输出主题时按序号映射而非按顺位匹配，同主题仍合并")
    void aggregate_outOfOrderTopics_mapByIndex() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("2. 数据库\n1. 数据库").build())
                .thenReturn(ChatResponse.builder().content("汇总：两组均为数据库优化话题。").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(1, docs.size());
        assertEquals(2, docs.get(0).getAggregationStartSeq());
        assertEquals(5, docs.get(0).getAggregationEndSeq());
        assertEquals(epochMillis(2), docs.get(0).getAggregationStartTime());
        assertEquals(epochMillis(5), docs.get(0).getAggregationEndTime());
        assertEquals("汇总：两组均为数据库优化话题。", docs.get(0).getAggregationText());
        verify(llmInvoker, times(2)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("主题归类输出包含空白主题时按空白过滤后数量不足则回退为每组合并前各自成组")
    void aggregate_blankTopicFiltered_eachGroupSeparate() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(4L);
        when(messageMapper.findNthUserSequenceNum(100L, 2)).thenReturn(6);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 数据库\n2. ").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(2, docs.size());
        assertEquals("【user】: q1\n【assistant】: a1", docs.get(0).getAggregationText());
        assertEquals(epochMillis(2), docs.get(0).getAggregationStartTime());
        assertEquals(epochMillis(3), docs.get(0).getAggregationEndTime());
        assertEquals("【user】: q2\n【assistant】: a2", docs.get(1).getAggregationText());
        assertEquals(epochMillis(4), docs.get(1).getAggregationStartTime());
        assertEquals(epochMillis(5), docs.get(1).getAggregationEndTime());
        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("主题序列 A→B→A 时不相邻的同主题不合并，连续合并切分为三段")
    void aggregate_sameTopicNonAdjacent_splitByContiguity() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(5L);
        when(messageMapper.findNthUserSequenceNum(100L, 3)).thenReturn(8);
        List<Message> newMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false),
                message(4L, 100L, "user", "q2", 4, false),
                message(5L, 100L, "assistant", "a2", 5, false),
                message(6L, 100L, "user", "q3", 6, false),
                message(7L, 100L, "assistant", "a3", 7, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 技术\n2. 项目\n3. 技术").build())
                .thenReturn(ChatResponse.builder().content("汇总A1").build())
                .thenReturn(ChatResponse.builder().content("汇总B").build())
                .thenReturn(ChatResponse.builder().content("汇总A2").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(3, docs.size());
        assertEquals(2, docs.get(0).getAggregationStartSeq());
        assertEquals(3, docs.get(0).getAggregationEndSeq());
        assertEquals(epochMillis(2), docs.get(0).getAggregationStartTime());
        assertEquals(epochMillis(3), docs.get(0).getAggregationEndTime());
        assertEquals("汇总A1", docs.get(0).getAggregationText());
        assertEquals(4, docs.get(1).getAggregationStartSeq());
        assertEquals(5, docs.get(1).getAggregationEndSeq());
        assertEquals(epochMillis(4), docs.get(1).getAggregationStartTime());
        assertEquals(epochMillis(5), docs.get(1).getAggregationEndTime());
        assertEquals("汇总B", docs.get(1).getAggregationText());
        assertEquals(6, docs.get(2).getAggregationStartSeq());
        assertEquals(7, docs.get(2).getAggregationEndSeq());
        assertEquals(epochMillis(6), docs.get(2).getAggregationStartTime());
        assertEquals(epochMillis(7), docs.get(2).getAggregationEndTime());
        assertEquals("汇总A2", docs.get(2).getAggregationText());
        verify(llmInvoker, times(4)).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("createTime 为 null 时 startTime/endTime 为 null，不抛异常")
    void aggregate_nullCreateTime_nullTimes() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(3L);
        when(messageMapper.findNthUserSequenceNum(100L, 1)).thenReturn(4);
        List<Message> newMessages = new ArrayList<>(List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false)));
        newMessages.forEach(m -> m.setCreateTime(null));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 技术").build())
                .thenReturn(ChatResponse.builder().content("摘要").build());

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(1, docs.size());
        assertNull(docs.get(0).getAggregationStartTime());
        assertNull(docs.get(0).getAggregationEndTime());
    }

    @Test
    @DisplayName("按天聚合：checkDailyExists 无重叠时按自然日窗口全量摘要生成唯一 DAILY 文档并写入")
    void aggregate_daily_newMessages_writesDailyDocs() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);

        List<Message> dailyMessages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(dailyMessages);

        stubInvokers();
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("每日摘要").build());

        service.aggregateSessionMemories();

        ArgumentCaptor<String> sidCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> startCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(sessionMemoryESClient).checkDailyExists(sidCaptor.capture(), startCaptor.capture(), endCaptor.capture());
        assertEquals("100", sidCaptor.getValue());
        long todayStart = LocalDate.now().atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long yesterdayStart = LocalDate.now().minusDays(1).atStartOfDay()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(yesterdayStart, startCaptor.getValue());
        assertEquals(todayStart, endCaptor.getValue());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        List<SessionMemoryDocument> docs = docCaptor.getValue();
        assertEquals(1, docs.size());
        SessionMemoryDocument doc = docs.get(0);
        assertEquals("100", doc.getSessionId());
        assertEquals(AggregationType.DAILY, doc.getAggregationType());
        assertEquals(2, doc.getAggregationStartSeq());
        assertEquals(3, doc.getAggregationEndSeq());
        assertEquals(yesterdayStart, doc.getAggregationStartTime());
        assertEquals(todayStart, doc.getAggregationEndTime());
        assertEquals("每日摘要", doc.getAggregationText());
        assertTrue(doc.getAggregationEndTime() > doc.getAggregationStartTime());
        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
        verify(sessionMapper, never()).updateById(any(Session.class));
    }

    @Test
    @DisplayName("按天聚合：全量内容提取忽略空/空白并整体 trim，LLM 收到拼接后的完整文本")
    void aggregate_daily_extractAllContent_concatAndTrim() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);
        List<Message> dailyMessages = List.of(
                message(2L, 100L, "user", "  q1", 2, false),
                message(3L, 100L, "assistant", null, 3, false),
                message(4L, 100L, "user", "   ", 4, false),
                message(5L, 100L, "assistant", "a1  ", 5, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(dailyMessages);

        stubInvokers();
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("每日摘要").build());

        service.aggregateSessionMemories();

        ArgumentCaptor<ChatRequest> reqCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmInvoker).invoke(reqCaptor.capture());
        List<com.ghost616.agentbase.dto.model.Message> msgs = reqCaptor.getValue().getMessages();
        assertEquals("q1\na1", msgs.get(msgs.size() - 1).getContent());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SessionMemoryDocument>> docCaptor = ArgumentCaptor.forClass(List.class);
        verify(sessionMemoryESClient).batchSave(docCaptor.capture());
        SessionMemoryDocument doc = docCaptor.getValue().get(0);
        assertEquals(2, doc.getAggregationStartSeq());
        assertEquals(5, doc.getAggregationEndSeq());
    }

    @Test
    @DisplayName("按天聚合：当天消息全为空白内容时跳过写入，LLM 不调用")
    void aggregate_daily_blankContent_skip() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                message(2L, 100L, "user", "   ", 2, false),
                message(3L, 100L, "assistant", null, 3, false)));
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);

        service.aggregateSessionMemories();

        verify(llmInvoker, never()).invoke(any(ChatRequest.class));
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("按天聚合：LLM 摘要为空时跳过写入")
    void aggregate_daily_emptySummary_skip() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false)));
        when(modelConfigMapper.selectById(10L)).thenReturn(llmModel(10L));
        when(modelConfigMapper.selectById(20L)).thenReturn(vectorModel(20L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenAnswer(invocation -> {
            ModelConfigData data = invocation.getArgument(0);
            return "embed-model".equals(data.modelName()) ? embedInvoker : llmInvoker;
        });
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content(null).build());

        service.aggregateSessionMemories();

        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("按天聚合：向量化为空时跳过写入")
    void aggregate_daily_emptyVector_skip() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false)));
        when(modelConfigMapper.selectById(10L)).thenReturn(llmModel(10L));
        when(modelConfigMapper.selectById(20L)).thenReturn(vectorModel(20L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenAnswer(invocation -> {
            ModelConfigData data = invocation.getArgument(0);
            return "embed-model".equals(data.modelName()) ? embedInvoker : llmInvoker;
        });
        when(embedInvoker.embed(any(EmbeddingRequest.class)))
                .thenReturn(EmbeddingResponse.builder().embeddings(List.of()).build());
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("每日摘要").build());

        service.aggregateSessionMemories();

        verify(llmInvoker, times(1)).invoke(any(ChatRequest.class));
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("按天聚合：时间范围查询为半开区间 [昨天00:00, 今天00:00)，结束边界为 lt")
    void aggregate_daily_queryTimeRange_halfOpen() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);

        service.aggregateSessionMemories();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Message>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(messageMapper).selectList(wrapperCaptor.capture());
        String expression = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(expression.contains("create_time < #{"), "结束边界应为 lt（半开区间），实际: " + expression);
        assertFalse(expression.contains("create_time <="), "不应使用 lte 结束边界，实际: " + expression);
        assertTrue(expression.contains("create_time >= #{"), "起始边界应为 ge，实际: " + expression);
    }

    @Test
    @DisplayName("按天聚合：checkDailyExists 已有重叠 DAILY 文档时跳过按日写入")
    void aggregate_daily_alreadyExists_skip() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);

        service.aggregateSessionMemories();

        verify(sessionMemoryESClient).checkDailyExists(eq("100"), anyLong(), anyLong());
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("按天聚合：自然日窗口内无消息时跳过按日写入")
    void aggregate_daily_noMessages_skip() {
        AgentConfig agent = memoryAgent(5L);
        when(agentConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(agent));
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));
        when(agentConfigMapper.selectBatchIds(any())).thenReturn(List.of(agent));

        when(messageMapper.countUserMessages(100L)).thenReturn(2L);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(sessionMemoryESClient.checkDailyExists(anyString(), anyLong(), anyLong())).thenReturn(false);

        service.aggregateSessionMemories();

        verify(sessionMemoryESClient).checkDailyExists(eq("100"), anyLong(), anyLong());
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("手动触发：会话不存在抛 SESSION_NOT_FOUND")
    void trigger_sessionNotFound_throws() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.triggerSessionMemory(999L));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("手动触发：智能体不存在抛 AGENT_NOT_FOUND")
    void trigger_agentNotFound_throws() {
        Session s = session(100L, 999L, 1);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        when(agentConfigMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.triggerSessionMemory(100L));
        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("手动触发：智能体未开启记忆功能抛 AGENT_MEMORY_NOT_ENABLED")
    void trigger_memoryDisabled_throws() {
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        AgentConfig agent = memoryAgent(5L);
        agent.setMemoryEnabled(false);
        when(agentConfigMapper.selectById(5L)).thenReturn(agent);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.triggerSessionMemory(100L));
        assertEquals(ErrorCode.AGENT_MEMORY_NOT_ENABLED, ex.getErrorCode());
        verify(sessionMemoryESClient, never()).batchSave(any());
    }

    @Test
    @DisplayName("手动触发：校验通过后异步聚合生成记忆文档")
    void trigger_success_generatesDocuments() throws Exception {
        Session s = session(100L, 5L, null);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        when(agentConfigMapper.selectById(5L)).thenReturn(memoryAgent(5L));

        when(messageMapper.countUserMessages(100L)).thenReturn(3L);
        when(messageMapper.findNthUserSequenceNum(100L, 1)).thenReturn(4);
        List<Message> newMessages = List.of(
                message(1L, 100L, "user", "q1", 1, false),
                message(2L, 100L, "assistant", "a1", 2, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(newMessages);

        stubInvokers();
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("1. 技术").build())
                .thenReturn(ChatResponse.builder().content("汇总").build());

        service.triggerSessionMemory(100L);

        verify(sessionMapper).selectById(100L);
        verify(agentConfigMapper).selectById(5L);
        verify(sessionMemoryESClient, timeout(5000)).batchSave(any());
        verify(sessionMapper, timeout(5000)).updateById(s);
    }

    @Test
    @DisplayName("getMemoryPrompt：会话存在时返回 memory_prompt")
    void getMemoryPrompt_sessionExists_returnsPrompt() {
        Session s = session(100L, 5L, 1);
        s.setMemoryPrompt("自定义提示语");
        when(sessionMapper.selectById(100L)).thenReturn(s);

        String prompt = service.getMemoryPrompt(100L);

        assertEquals("自定义提示语", prompt);
    }

    @Test
    @DisplayName("getMemoryPrompt：会话不存在抛 SESSION_NOT_FOUND")
    void getMemoryPrompt_sessionNotFound_throws() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getMemoryPrompt(999L));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("saveMemoryPrompt：保存 memory_prompt 并更新会话")
    void saveMemoryPrompt_updatesSession() {
        Session s = session(100L, 5L, 1);
        when(sessionMapper.selectById(100L)).thenReturn(s);

        service.saveMemoryPrompt(100L, "新提示语");

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals("新提示语", captor.getValue().getMemoryPrompt());
    }

    @Test
    @DisplayName("saveMemoryPrompt：会话不存在抛 SESSION_NOT_FOUND")
    void saveMemoryPrompt_sessionNotFound_throws() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveMemoryPrompt(999L, "p"));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
        verify(sessionMapper, never()).updateById(any(Session.class));
    }

    @Test
    @DisplayName("regenerateSummary：返回 RUNNING 状态并异步生成聚合文本")
    void regenerateSummary_returnsRunningAndCompletes() throws Exception {
        Session s = session(100L, 5L, null);
        s.setModelId(10L);
        when(sessionMapper.selectById(100L)).thenReturn(s);

        List<Message> messages = List.of(
                message(2L, 100L, "user", "q1", 2, false),
                message(3L, 100L, "assistant", "a1", 3, false));
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(messages);
        when(modelConfigMapper.selectById(10L)).thenReturn(llmModel(10L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(llmInvoker);
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("重生成的摘要").build());

        MemoryRegenerateStatusDTO initial = service.regenerateSummary(100L, "100_GROUP_2_3", 2, 3, null);

        assertEquals("RUNNING", initial.getStatus());
        assertEquals("100_GROUP_2_3", initial.getDocId());

        verify(llmInvoker, timeout(5000)).invoke(any(ChatRequest.class));

        MemoryRegenerateStatusDTO status = service.getRegenerateStatus(100L);
        assertNotNull(status);
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("重生成的摘要", status.getAggregationText());
    }

    @Test
    @DisplayName("regenerateSummary：自定义 prompt 非空时透传给 LLM")
    void regenerateSummary_customPrompt_passedToLlm() throws Exception {
        Session s = session(100L, 5L, null);
        s.setModelId(10L);
        when(sessionMapper.selectById(100L)).thenReturn(s);

        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                message(2L, 100L, "user", "q1", 2, false)));
        when(modelConfigMapper.selectById(10L)).thenReturn(llmModel(10L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(llmInvoker);
        when(llmInvoker.invoke(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("按提示语生成的摘要").build());

        service.regenerateSummary(100L, "100_GROUP_2_2", 2, 2, "请用英文总结");

        ArgumentCaptor<ChatRequest> reqCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmInvoker, timeout(5000)).invoke(reqCaptor.capture());
        List<com.ghost616.agentbase.dto.model.Message> msgs = reqCaptor.getValue().getMessages();
        assertEquals("请用英文总结", msgs.get(0).getContent());
        assertEquals("q1", msgs.get(msgs.size() - 1).getContent());
    }

    @Test
    @DisplayName("regenerateSummary：序号区间内无消息时状态 FAILED")
    void regenerateSummary_noMessages_failed() throws Exception {
        Session s = session(100L, 5L, null);
        s.setModelId(10L);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.regenerateSummary(100L, "100_GROUP_2_3", 2, 3, null);

        verify(messageMapper, timeout(5000)).selectList(any(LambdaQueryWrapper.class));

        MemoryRegenerateStatusDTO status = service.getRegenerateStatus(100L);
        assertEquals("FAILED", status.getStatus());
        assertNotNull(status.getError());
        verify(llmInvoker, never()).invoke(any(ChatRequest.class));
    }

    @Test
    @DisplayName("regenerateSummary：会话不存在抛 SESSION_NOT_FOUND")
    void regenerateSummary_sessionNotFound_throws() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.regenerateSummary(999L, "doc", 1, 2, null));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("saveAggregationText：重新向量化并调用 ES updateDocument")
    void saveAggregationText_revectorizesAndUpdates() {
        Session s = session(100L, 5L, null);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        when(agentConfigMapper.selectById(5L)).thenReturn(memoryAgent(5L));
        when(modelConfigMapper.selectById(20L)).thenReturn(vectorModel(20L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(embedInvoker);
        when(embedInvoker.embed(any(EmbeddingRequest.class))).thenReturn(
                EmbeddingResponse.builder().embeddings(List.of(
                        EmbeddingResponse.EmbeddingItem.builder().index(0).embedding(List.of(0.9f, 0.8f)).build()))
                        .build());

        service.saveAggregationText(100L, "100_GROUP_2_3", "更新后的摘要");

        ArgumentCaptor<EmbeddingRequest> embedCaptor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(embedInvoker).embed(embedCaptor.capture());
        assertEquals("embed-model", embedCaptor.getValue().getModel());
        assertEquals("更新后的摘要", embedCaptor.getValue().getInput());
        verify(sessionMemoryESClient).updateDocument(eq("100_GROUP_2_3"), eq("更新后的摘要"), eq(List.of(0.9f, 0.8f)));
    }

    @Test
    @DisplayName("saveAggregationText：智能体不存在抛 AGENT_NOT_FOUND")
    void saveAggregationText_agentNotFound_throws() {
        Session s = session(100L, 999L, null);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        when(agentConfigMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveAggregationText(100L, "doc", "text"));
        assertEquals(ErrorCode.AGENT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("saveAggregationText：未配置向量模型抛 AGENT_MEMORY_VECTOR_MODEL_REQUIRED")
    void saveAggregationText_noVectorModel_throws() {
        Session s = session(100L, 5L, null);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        AgentConfig agent = memoryAgent(5L);
        agent.setVectorModelId(null);
        when(agentConfigMapper.selectById(5L)).thenReturn(agent);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveAggregationText(100L, "doc", "text"));
        assertEquals(ErrorCode.AGENT_MEMORY_VECTOR_MODEL_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("saveAggregationText：向量化结果为空抛 AGENT_MEMORY_VECTOR_MODEL_REQUIRED")
    void saveAggregationText_emptyVector_throws() {
        Session s = session(100L, 5L, null);
        when(sessionMapper.selectById(100L)).thenReturn(s);
        when(agentConfigMapper.selectById(5L)).thenReturn(memoryAgent(5L));
        when(modelConfigMapper.selectById(20L)).thenReturn(vectorModel(20L));
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(embedInvoker);
        when(embedInvoker.embed(any(EmbeddingRequest.class)))
                .thenReturn(EmbeddingResponse.builder().embeddings(List.of()).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.saveAggregationText(100L, "doc", "text"));
        assertEquals(ErrorCode.AGENT_MEMORY_VECTOR_MODEL_REQUIRED, ex.getErrorCode());
        verify(sessionMemoryESClient, never()).updateDocument(anyString(), anyString(), any());
    }
}
