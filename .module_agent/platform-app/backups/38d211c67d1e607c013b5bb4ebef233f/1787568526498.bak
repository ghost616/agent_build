package com.ghost616.platform.service.memory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ghost616.agentbase.dto.model.EmbeddingRequest;
import com.ghost616.agentbase.dto.model.EmbeddingResponse;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import com.ghost616.agentinteg.knowledge.SearchType;
import com.ghost616.agentinteg.memory.MemoryResult;
import com.ghost616.agentinteg.memory.MessageSeqByRole;
import com.ghost616.agentinteg.memory.SeqRange;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.enums.AggregationType;
import com.ghost616.platform.model.SessionMemoryDocument;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.service.search.SessionMemoryESClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryQueryProviderImplTest {

    @Mock
    private SessionMemoryESClient sessionMemoryESClient;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private AgentConfigMapper agentConfigMapper;
    @Mock
    private ModelConfigMapper modelConfigMapper;
    @Mock
    private ModelInvokerManager modelInvokerManager;
    @Mock
    private ModelInvoker modelInvoker;

    private MemoryQueryProviderImpl provider;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Session.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AgentConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ModelConfig.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Message.class);
        provider = new MemoryQueryProviderImpl(sessionMemoryESClient, messageMapper, sessionMapper,
                agentConfigMapper, modelConfigMapper, modelInvokerManager);
    }

    private SessionMemoryDocument doc(String text, Integer startSeq, Integer endSeq, AggregationType type) {
        return SessionMemoryDocument.builder()
                .sessionId("100")
                .aggregationType(type)
                .aggregationStartSeq(startSeq)
                .aggregationEndSeq(endSeq)
                .aggregationText(text)
                .build();
    }

    private Session session(Long agentId) {
        Session s = new Session();
        s.setId(100L);
        s.setAgentId(agentId);
        return s;
    }

    private AgentConfig agentConfig(Long vectorModelId) {
        AgentConfig c = new AgentConfig();
        c.setId(10L);
        c.setVectorModelId(vectorModelId);
        return c;
    }

    private ModelConfig modelConfig() {
        ModelConfig m = new ModelConfig();
        m.setId(5L);
        m.setApiKey("key");
        m.setBaseUrl("http://localhost");
        m.setModelName("embed-model");
        m.setTemperature(0.1);
        m.setMaxTokens(1024);
        return m;
    }

    private Message message(Long sessionId, String role, Integer seq) {
        Message m = new Message();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setSequenceNum(seq);
        m.setRollback(false);
        return m;
    }

    private void stubEmbedding() {
        when(sessionMapper.selectById(100L)).thenReturn(session(10L));
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig(5L));
        when(modelConfigMapper.selectById(5L)).thenReturn(modelConfig());
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.embed(any(EmbeddingRequest.class))).thenReturn(EmbeddingResponse.builder()
                .embeddings(List.of(EmbeddingResponse.EmbeddingItem.builder()
                        .index(0).embedding(List.of(0.1f, 0.2f)).build()))
                .build());
    }

    // ---------- getMemories ----------

    @Test
    @DisplayName("getMemories: sessionId 为 null/空白或 searchType 为 null 时返回空列表")
    void getMemories_invalidArgs() {
        assertTrue(provider.getMemories(null, SearchType.VECTOR, null, null, null, "q").isEmpty());
        assertTrue(provider.getMemories("  ", SearchType.VECTOR, null, null, null, "q").isEmpty());
        assertTrue(provider.getMemories("100", null, null, null, null, "q").isEmpty());
        verifyNoInteractions(sessionMemoryESClient);
    }

    @Test
    @DisplayName("getMemories: VECTOR 搜索调用 vectorSearch 并透传向量与过滤条件")
    void getMemories_vectorSearch() {
        stubEmbedding();
        when(sessionMemoryESClient.vectorSearch(eq("100"), isNull(), eq("GROUP"), eq(1L), eq(2L), eq(List.of(0.1f, 0.2f)), eq(20)))
                .thenReturn(List.of(doc("记忆A", 1, 3, AggregationType.GROUP)));

        List<MemoryResult> result = provider.getMemories("100", SearchType.VECTOR, "GROUP", 1L, 2L, "q");

        assertEquals(1, result.size());
        MemoryResult r = result.get(0);
        assertEquals("记忆A", r.content());
        assertEquals(1, r.startSeq());
        assertEquals(3, r.endSeq());
        assertEquals("GROUP", r.memoryType());
        verify(sessionMemoryESClient).vectorSearch("100", null, "GROUP", 1L, 2L, List.of(0.1f, 0.2f), 20);
    }

    @Test
    @DisplayName("getMemories: FULLTEXT 搜索调用 fullTextSearch，不触发向量化")
    void getMemories_fullTextSearch() {
        when(sessionMemoryESClient.fullTextSearch("100", null, null, null, null, "q", 20))
                .thenReturn(List.of(doc("记忆B", 5, 6, AggregationType.DAILY)));

        List<MemoryResult> result = provider.getMemories("100", SearchType.FULLTEXT, null, null, null, "q");

        assertEquals(1, result.size());
        assertEquals("记忆B", result.get(0).content());
        assertEquals(5, result.get(0).startSeq());
        assertEquals(6, result.get(0).endSeq());
        assertEquals("DAILY", result.get(0).memoryType());
        verify(sessionMemoryESClient).fullTextSearch("100", null, null, null, null, "q", 20);
        verify(modelInvokerManager, never()).getInvoker(any());
    }

    @Test
    @DisplayName("getMemories: HYBRID 搜索调用 hybridSearch，同时携带向量与文本")
    void getMemories_hybridSearch() {
        stubEmbedding();
        when(sessionMemoryESClient.hybridSearch(eq("100"), isNull(), isNull(), isNull(), isNull(), anyList(), eq("q"), eq(20)))
                .thenReturn(List.of(doc("记忆C", 7, 8, AggregationType.GROUP)));

        List<MemoryResult> result = provider.getMemories("100", SearchType.HYBRID, null, null, null, "q");

        assertEquals(1, result.size());
        assertEquals("记忆C", result.get(0).content());
        verify(sessionMemoryESClient).hybridSearch("100", null, null, null, null, List.of(0.1f, 0.2f), "q", 20);
    }

    @Test
    @DisplayName("getMemories: aggregationStartSeq/EndSeq 为 null 时映射为 0，aggregationType 为 null 时 memoryType 为 null")
    void getMemories_nullSeqMapping() {
        SessionMemoryDocument doc = SessionMemoryDocument.builder()
                .sessionId("100")
                .aggregationText("记忆D")
                .build();
        when(sessionMemoryESClient.fullTextSearch("100", null, null, null, null, "q", 20))
                .thenReturn(List.of(doc));

        List<MemoryResult> result = provider.getMemories("100", SearchType.FULLTEXT, null, null, null, "q");

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).startSeq());
        assertEquals(0, result.get(0).endSeq());
        assertNull(result.get(0).memoryType());
    }

    @Test
    @DisplayName("getMemories: 无匹配结果时返回空列表（非错误）")
    void getMemories_noResult() {
        when(sessionMemoryESClient.fullTextSearch("100", null, null, null, null, "q", 20))
                .thenReturn(List.of());

        List<MemoryResult> result = provider.getMemories("100", SearchType.FULLTEXT, null, null, null, "q");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getMemories: 向量化链路 - session/agentConfig/modelConfig/invoker 逐级缺失时返回空向量")
    void getMemories_embedQueryFallbacks() {
        // session 不存在
        when(sessionMapper.selectById(100L)).thenReturn(null);
        when(sessionMemoryESClient.vectorSearch("100", null, null, null, null, List.of(), 20))
                .thenReturn(List.of());
        assertTrue(provider.getMemories("100", SearchType.VECTOR, null, null, null, "q").isEmpty());

        // session.agentId 为 null
        when(sessionMapper.selectById(100L)).thenReturn(session(null));
        when(sessionMemoryESClient.vectorSearch("100", null, null, null, null, List.of(), 20))
                .thenReturn(List.of());
        assertTrue(provider.getMemories("100", SearchType.VECTOR, null, null, null, "q").isEmpty());

        // agentConfig 不存在
        when(sessionMapper.selectById(100L)).thenReturn(session(10L));
        when(agentConfigMapper.selectById(10L)).thenReturn(null);
        when(sessionMemoryESClient.vectorSearch("100", null, null, null, null, List.of(), 20))
                .thenReturn(List.of());
        assertTrue(provider.getMemories("100", SearchType.VECTOR, null, null, null, "q").isEmpty());

        // agentConfig.vectorModelId 为 null
        when(sessionMapper.selectById(100L)).thenReturn(session(10L));
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig(null));
        when(sessionMemoryESClient.vectorSearch("100", null, null, null, null, List.of(), 20))
                .thenReturn(List.of());
        assertTrue(provider.getMemories("100", SearchType.VECTOR, null, null, null, "q").isEmpty());

        // modelConfig 不存在
        when(sessionMapper.selectById(100L)).thenReturn(session(10L));
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig(5L));
        when(modelConfigMapper.selectById(5L)).thenReturn(null);
        when(sessionMemoryESClient.vectorSearch("100", null, null, null, null, List.of(), 20))
                .thenReturn(List.of());
        assertTrue(provider.getMemories("100", SearchType.VECTOR, null, null, null, "q").isEmpty());

        verify(modelInvokerManager, never()).getInvoker(any());
    }

    // ---------- getMessageSeqsByRole ----------

    @Test
    @DisplayName("getMessageSeqsByRole: sessionId 为 null/空白或 ranges 为空时返回空三列表")
    void getMessageSeqsByRole_invalidSessionId() {
        MessageSeqByRole result = provider.getMessageSeqsByRole(null, List.of(new SeqRange(1, 5)));
        assertTrue(result.userSeqList().isEmpty());
        assertTrue(result.toolSeqList().isEmpty());
        assertTrue(result.assistantSeqList().isEmpty());

        MessageSeqByRole emptyRanges = provider.getMessageSeqsByRole("100", List.of());
        assertTrue(emptyRanges.userSeqList().isEmpty());
        assertTrue(emptyRanges.toolSeqList().isEmpty());
        assertTrue(emptyRanges.assistantSeqList().isEmpty());

        verifyNoInteractions(messageMapper);
    }

    @Test
    @DisplayName("getMessageSeqsByRole: 按 role 分类返回序号列表，批量区间一次查询并去重")
    void getMessageSeqsByRole_classifyByRole() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                message(100L, "user", 1),
                message(100L, "tool", 2),
                message(100L, "assistant", 3),
                message(100L, "user", 4),
                message(100L, "assistant", 5),
                message(100L, "user", 4)));

        MessageSeqByRole result = provider.getMessageSeqsByRole("100",
                List.of(new SeqRange(1, 3), new SeqRange(3, 5)));

        assertEquals(List.of(1, 4), result.userSeqList());
        assertEquals(List.of(2), result.toolSeqList());
        assertEquals(List.of(3, 5), result.assistantSeqList());

        verify(messageMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        verify(messageMapper).selectList(argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            return sql.contains("rollback") && sql.contains("sequence_num");
        }));
    }

    @Test
    @DisplayName("getMessageSeqsByRole: sequenceNum 为 null 的消息被跳过")
    void getMessageSeqsByRole_skipNullSeq() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                message(100L, "user", 1),
                message(100L, "tool", null)));

        MessageSeqByRole result = provider.getMessageSeqsByRole("100", List.of(new SeqRange(1, 5)));

        assertEquals(List.of(1), result.userSeqList());
        assertTrue(result.toolSeqList().isEmpty());
        assertTrue(result.assistantSeqList().isEmpty());
    }

    @Test
    @DisplayName("getMessageSeqsByRole: 无匹配消息时返回空三列表")
    void getMessageSeqsByRole_noMessages() {
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        MessageSeqByRole result = provider.getMessageSeqsByRole("100", List.of(new SeqRange(1, 5)));

        assertTrue(result.userSeqList().isEmpty());
        assertTrue(result.toolSeqList().isEmpty());
        assertTrue(result.assistantSeqList().isEmpty());
    }
}
