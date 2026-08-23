package com.ghost616.platform.service.agent;

import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.ContextDataProvider.AgentContextData;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionSkill;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.entity.SessionVariable;
import com.ghost616.platform.entity.SkillConfig;
import com.ghost616.platform.entity.ToolConfig;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.AgentSkillMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.repository.SessionVariableMapper;
import com.ghost616.platform.repository.SkillConfigMapper;
import com.ghost616.platform.repository.SkillToolMapper;
import com.ghost616.platform.repository.ToolConfigMapper;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.entity.User;
import com.ghost616.platform.service.tool.ToolConfigService;
import com.ghost616.platform.session.UserContext;
import com.ghost616.platform.session.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultContextDataProviderTest {

    @Mock private SessionMapper sessionMapper;
    @Mock private AgentConfigMapper agentConfigMapper;
    @Mock private SessionVariableMapper sessionVariableMapper;
    @Mock private AgentSkillMapper agentSkillMapper;
    @Mock private SkillConfigMapper skillConfigMapper;
    @Mock private SkillToolMapper skillToolMapper;
    @Mock private ToolConfigService toolConfigService;
    @Mock private SessionSkillMapper sessionSkillMapper;
    @Mock private ModelConfigMapper modelConfigMapper;
    @Mock private ToolConfigMapper toolConfigMapper;
    @Mock private SessionToolMapper sessionToolMapper;
    @Mock private MessageDataProvider messageDataProvider;

    @Captor private ArgumentCaptor<Session> sessionCaptor;
    @Captor private ArgumentCaptor<SessionTool> sessionToolCaptor;
    @Captor private ArgumentCaptor<SessionSkill> sessionSkillCaptor;

    private DefaultContextDataProvider provider;

    private static final Long CURRENT_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(CURRENT_USER_ID);
        UserContext.set(new UserSession("session-test", user, System.currentTimeMillis()));

        provider = new DefaultContextDataProvider(sessionMapper, agentConfigMapper,
                sessionVariableMapper, agentSkillMapper, skillConfigMapper,
                skillToolMapper, toolConfigService, sessionSkillMapper,
                modelConfigMapper, toolConfigMapper, sessionToolMapper,
                messageDataProvider);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createChildSession_所有参数有效_创建子会话并返回ID() {
        Long parentId = 1L;
        Long modelId = 10L;
        List<Long> toolIds = List.of(100L, 101L);
        List<Long> skillIds = List.of(200L, 201L);

        when(sessionMapper.selectById(parentId)).thenReturn(new Session());
        when(modelConfigMapper.selectById(modelId)).thenReturn(new ModelConfig());
        when(toolConfigMapper.selectById(100L)).thenReturn(new ToolConfig());
        when(toolConfigMapper.selectById(101L)).thenReturn(new ToolConfig());
        when(skillConfigMapper.selectById(200L)).thenReturn(new SkillConfig());
        when(skillConfigMapper.selectById(201L)).thenReturn(new SkillConfig());
        doAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setId(999L);
            return null;
        }).when(sessionMapper).insert(any(Session.class));

        String result = provider.createChildSession("1", "test-agent", "desc", "10", List.of("100", "101"), List.of("200", "201"), "system prompt");

        assertEquals("999", result);
        verify(sessionMapper).insert(sessionCaptor.capture());
        Session saved = sessionCaptor.getValue();
        assertEquals(CURRENT_USER_ID, saved.getUserId());
        assertEquals("test-agent", saved.getTitle());
        assertEquals("system prompt", saved.getSystemPrompt());
        assertEquals("desc", saved.getDescription());
        assertEquals(parentId, saved.getParentSessionId());
        assertTrue(saved.getIsChild());
        assertEquals(modelId, saved.getModelId());
        assertNull(saved.getAgentId());

        verify(sessionToolMapper, times(2)).insert(sessionToolCaptor.capture());
        List<SessionTool> stList = sessionToolCaptor.getAllValues();
        assertEquals(100L, stList.get(0).getToolId());
        assertEquals(999L, stList.get(0).getSessionId());
        assertEquals(SessionAuthType.ALL, stList.get(0).getSessionAuth());
        assertEquals(101L, stList.get(1).getToolId());
        assertEquals(999L, stList.get(1).getSessionId());
        assertEquals(SessionAuthType.ALL, stList.get(1).getSessionAuth());

        verify(sessionSkillMapper, times(2)).insert(sessionSkillCaptor.capture());
        List<SessionSkill> ssList = sessionSkillCaptor.getAllValues();
        assertEquals(200L, ssList.get(0).getSkillId());
        assertEquals(999L, ssList.get(0).getSessionId());
        assertEquals(SessionAuthType.ALL, ssList.get(0).getSessionAuth());
        assertEquals(201L, ssList.get(1).getSkillId());
        assertEquals(999L, ssList.get(1).getSessionId());
        assertEquals(SessionAuthType.ALL, ssList.get(1).getSessionAuth());
    }

    @Test
    void createChildSession_parentSession不存在_抛异常() {
        when(sessionMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> provider.createChildSession("1", "a", null, null, null, null, null));
                
    }

    @Test
    void createChildSession_model不存在_抛异常() {
        when(sessionMapper.selectById(1L)).thenReturn(new Session());
        when(modelConfigMapper.selectById(10L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> provider.createChildSession("1", "a", null, "10", null, null, null));
                
    }

    @Test
    void createChildSession_tool不存在_抛异常() {
        when(sessionMapper.selectById(1L)).thenReturn(new Session());
        when(modelConfigMapper.selectById(10L)).thenReturn(new ModelConfig());
        when(toolConfigMapper.selectById(100L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> provider.createChildSession("1", "a", null, "10", List.of("100"), null, null));
    }

    @Test
    void createChildSession_skill不存在_抛异常() {
        when(sessionMapper.selectById(1L)).thenReturn(new Session());
        when(modelConfigMapper.selectById(10L)).thenReturn(new ModelConfig());
        when(skillConfigMapper.selectById(200L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> provider.createChildSession("1", "a", null, "10", null, List.of("200"), null));
    }

    @Test
    void createChildSession_tooList为null_跳过工具校验和插入() {
        when(sessionMapper.selectById(1L)).thenReturn(new Session());
        doAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setId(999L);
            return null;
        }).when(sessionMapper).insert(any(Session.class));

        provider.createChildSession("1", "a", null, null, null, null, "p");

        verify(toolConfigMapper, never()).selectById(any());
        verify(sessionToolMapper, never()).insert(any(SessionTool.class));
        verify(sessionSkillMapper, never()).insert(any(SessionSkill.class));
    }

    @Test
    void createChildSession_skillIds为null_跳过技能校验和插入() {
        when(sessionMapper.selectById(1L)).thenReturn(new Session());
        when(modelConfigMapper.selectById(10L)).thenReturn(new ModelConfig());
        when(toolConfigMapper.selectById(100L)).thenReturn(new ToolConfig());
        doAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setId(999L);
            return null;
        }).when(sessionMapper).insert(any(Session.class));

        provider.createChildSession("1", "a", null, "10", List.of("100"), null, "p");

        verify(sessionToolMapper).insert(any(SessionTool.class));
        verify(sessionSkillMapper, never()).insert(any(SessionSkill.class));
    }

    @Test
    void loadAgentContext_session不存在_返回null() {
        when(sessionMapper.selectById(99L)).thenReturn(null);

        AgentContextData result = provider.loadAgentContext("99");

        assertNull(result);
    }

    @Test
    void loadAgentContext_子会话_parentSessionId和childSessions和agentId正确() {
        Session childSession = new Session();
        childSession.setId(2L);
        childSession.setIsChild(true);
        childSession.setParentSessionId(1L);
        childSession.setAgentId(null);
        childSession.setSystemPrompt("child prompt");
        childSession.setModelId(200L);
        when(sessionMapper.selectById(2L)).thenReturn(childSession);
        when(sessionSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("2");

        assertNotNull(result);
        assertNull(result.agentId());
        assertEquals("1", result.parentSessionId());
        assertNull(result.childSessions());
    }

    @Test
    void loadAgentContext_子会话_parentSessionId为null_不查询parentSession() {
        Session orphan = new Session();
        orphan.setId(3L);
        orphan.setIsChild(true);
        orphan.setParentSessionId(null);
        when(sessionMapper.selectById(3L)).thenReturn(orphan);
        when(sessionSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("3");

        assertNull(result.recentMessageCount());
        verify(sessionMapper, times(1)).selectById(any());
    }

    @Test
    void loadAgentContext_子会话_parentSessionAgentId为null_recentMessageCount为null() {
        Session child = new Session();
        child.setId(4L);
        child.setIsChild(true);
        child.setParentSessionId(1L);
        when(sessionMapper.selectById(4L)).thenReturn(child);
        Session parent = new Session();
        parent.setAgentId(null);
        when(sessionMapper.selectById(1L)).thenReturn(parent);
        when(sessionSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("4");

        assertNull(result.recentMessageCount());
    }

    @Test
    void loadAgentContext_子会话_从父会话继承recentMessageCount() {
        Session child = new Session();
        child.setId(5L);
        child.setIsChild(true);
        child.setParentSessionId(1L);
        when(sessionMapper.selectById(5L)).thenReturn(child);
        Session parent = new Session();
        parent.setAgentId(10L);
        when(sessionMapper.selectById(1L)).thenReturn(parent);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setRecentMessageCount(50);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(sessionSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("5");

        assertEquals(Integer.valueOf(50), result.recentMessageCount());
    }

    @Test
    void loadAgentContext_普通会话_parentSessionId为null() {
        Session session = new Session();
        session.setId(6L);
        session.setIsChild(false);
        session.setAgentId(10L);
        when(sessionMapper.selectById(6L)).thenReturn(session);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSystemPrompt("prompt");
        agentConfig.setModelId(100L);
        agentConfig.setRecentMessageCount(20);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("6");

        assertNull(result.parentSessionId());
    }

    @Test
    void loadAgentContext_普通会话_childSessions查询并映射正确() {
        Session session = new Session();
        session.setId(7L);
        session.setIsChild(false);
        session.setAgentId(10L);
        when(sessionMapper.selectById(7L)).thenReturn(session);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setRecentMessageCount(10);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        Session child1 = new Session();
        child1.setId(71L);
        child1.setTitle("child1");
        child1.setDescription("desc1");
        child1.setModelId(200L);
        Session child2 = new Session();
        child2.setId(72L);
        child2.setTitle("child2");
        child2.setDescription("desc2");
        child2.setModelId(201L);
        when(sessionMapper.selectList(any())).thenReturn(List.of(child1, child2));

        AgentContextData result = provider.loadAgentContext("7");

        assertNotNull(result.childSessions());
        assertEquals(2, result.childSessions().size());
        assertEquals("71", result.childSessions().get(0).sessionId());
        assertEquals("child1", result.childSessions().get(0).sessionName());
        assertEquals("desc1", result.childSessions().get(0).description());
        assertEquals("200", result.childSessions().get(0).modelId());
        assertEquals("72", result.childSessions().get(1).sessionId());
        assertEquals("child2", result.childSessions().get(1).sessionName());
    }

    @Test
    void loadAgentContext_普通会话_无子会话时childSessions不为null且为空() {
        Session session = new Session();
        session.setId(8L);
        session.setIsChild(false);
        session.setAgentId(10L);
        when(sessionMapper.selectById(8L)).thenReturn(session);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setRecentMessageCount(10);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("8");

        assertNotNull(result.childSessions());
        assertTrue(result.childSessions().isEmpty());
    }

    @Test
    void loadAgentContext_普通会话_agentId等字段正常填充() {
        Session session = new Session();
        session.setId(9L);
        session.setIsChild(false);
        session.setAgentId(10L);
        when(sessionMapper.selectById(9L)).thenReturn(session);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSystemPrompt("sys prompt");
        agentConfig.setModelId(300L);
        agentConfig.setRecentMessageCount(15);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("9");

        assertEquals("10", result.agentId());
        assertEquals("sys prompt", result.systemPrompt());
        assertEquals("300", result.defaultModelId());
        assertEquals(Integer.valueOf(15), result.recentMessageCount());
        assertNotNull(result.sessionVariables());
    }

    @Test
    void loadAgentContext_普通会话_lastResponseId正确填充() {
        Session session = new Session();
        session.setId(11L);
        session.setIsChild(false);
        session.setAgentId(10L);
        session.setLastResponseId("resp_111");
        when(sessionMapper.selectById(11L)).thenReturn(session);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setRecentMessageCount(10);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("11");

        assertEquals("resp_111", result.lastResponseId());
    }

    @Test
    void loadAgentContext_普通会话_lastResponseId为null时返回null() {
        Session session = new Session();
        session.setId(12L);
        session.setIsChild(false);
        session.setAgentId(10L);
        when(sessionMapper.selectById(12L)).thenReturn(session);
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setRecentMessageCount(10);
        when(agentConfigMapper.selectById(10L)).thenReturn(agentConfig);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("12");

        assertNull(result.lastResponseId());
    }

    @Test
    void loadAgentContext_子会话_lastResponseId正确填充() {
        Session childSession = new Session();
        childSession.setId(13L);
        childSession.setIsChild(true);
        childSession.setParentSessionId(1L);
        childSession.setLastResponseId("resp_131");
        when(sessionMapper.selectById(13L)).thenReturn(childSession);
        when(sessionSkillMapper.selectList(any())).thenReturn(List.of());
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        AgentContextData result = provider.loadAgentContext("13");

        assertEquals("resp_131", result.lastResponseId());
    }

    @Test
    void updateLastResponseId_会话存在_更新lastResponseId() {
        Session session = new Session();
        session.setId(1L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        provider.updateLastResponseId("1", "resp_001");

        assertEquals("resp_001", session.getLastResponseId());
        verify(sessionMapper).updateById(session);
    }

    @Test
    void updateLastResponseId_会话不存在_不执行更新() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        provider.updateLastResponseId("999", "resp_001");

        verify(sessionMapper, never()).updateById(any(Session.class));
    }

    @Test
    void getLatestMessages_委托调用MessageDataProvider并返回结果() {
        String sessionId = "100";
        MessageDataProvider.MessageDTO msg1 = new MessageDataProvider.MessageDTO(
                "1", "100", "user", "hello", null, null, null, null, null, null, null, null, null, null, null, null);
        MessageDataProvider.MessageDTO msg2 = new MessageDataProvider.MessageDTO(
                "2", "100", "assistant", "world", null, null, null, null, null, null, null, null, null, null, null, null);
        when(messageDataProvider.getMessages(sessionId)).thenReturn(List.of(msg1, msg2));

        List<MessageDataProvider.MessageDTO> result = provider.getLatestMessages(sessionId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("hello", result.get(0).content());
        assertEquals("assistant", result.get(1).role());
        verify(messageDataProvider).getMessages(sessionId);
    }

    @Test
    void getLatestMessages_返回空列表() {
        when(messageDataProvider.getMessages("200")).thenReturn(List.of());

        List<MessageDataProvider.MessageDTO> result = provider.getLatestMessages("200");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageDataProvider).getMessages("200");
    }

    @Test
    void getLatestSessionVariables_查询sessionVariable并返回Map() {
        Long sessionId = 10L;
        SessionVariable sv1 = new SessionVariable();
        sv1.setVariableKey("key1");
        sv1.setVariableValue("val1");
        SessionVariable sv2 = new SessionVariable();
        sv2.setVariableKey("key2");
        sv2.setVariableValue("val2");
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of(sv1, sv2));

        Map<String, String> result = provider.getLatestSessionVariables(String.valueOf(sessionId));

        assertEquals(2, result.size());
        assertEquals("val1", result.get("key1"));
        assertEquals("val2", result.get("key2"));
    }

    @Test
    void getLatestSessionVariables_无变量时返回空Map() {
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        Map<String, String> result = provider.getLatestSessionVariables("20");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getLatestConversationVariables_返回空Map() {
        Map<String, String> result = provider.getLatestConversationVariables("30");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void saveSessionVariable_变量不存在_新建并填充当前用户ID() {
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        provider.saveSessionVariable("10", "key1", "val1");

        ArgumentCaptor<SessionVariable> captor = ArgumentCaptor.forClass(SessionVariable.class);
        verify(sessionVariableMapper).insert(captor.capture());
        SessionVariable saved = captor.getValue();
        assertEquals(10L, saved.getSessionId());
        assertEquals(CURRENT_USER_ID, saved.getUserId());
        assertEquals("key1", saved.getVariableKey());
        assertEquals("val1", saved.getVariableValue());
    }

    @Test
    void saveSessionVariable_变量已存在_仅更新值不覆盖用户ID() {
        SessionVariable existing = new SessionVariable();
        existing.setId(1L);
        existing.setSessionId(10L);
        existing.setUserId(7L);
        existing.setVariableKey("key1");
        existing.setVariableValue("old");
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of(existing));

        provider.saveSessionVariable("10", "key1", "new");

        verify(sessionVariableMapper).updateById(existing);
        verify(sessionVariableMapper, never()).insert(any(SessionVariable.class));
        assertEquals("new", existing.getVariableValue());
        assertEquals(7L, existing.getUserId());
    }

    @Test
    void deleteSessionVariable_按会话和key删除() {
        provider.deleteSessionVariable("10", "key1");

        verify(sessionVariableMapper).delete(any());
    }

    @Test
    void getLatestChildSessions_查询子会话并映射为ChildSession列表() {
        Long sessionId = 50L;
        Session child1 = new Session();
        child1.setId(51L);
        child1.setTitle("child-a");
        child1.setDescription("desc-a");
        child1.setModelId(300L);
        Session child2 = new Session();
        child2.setId(52L);
        child2.setTitle("child-b");
        child2.setDescription("desc-b");
        child2.setModelId(301L);
        when(sessionMapper.selectList(any())).thenReturn(List.of(child1, child2));

        List<AgentExecutionContext.ChildSession> result = provider.getLatestChildSessions(String.valueOf(sessionId));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("51", result.get(0).sessionId());
        assertEquals("child-a", result.get(0).sessionName());
        assertEquals("desc-a", result.get(0).description());
        assertEquals("300", result.get(0).modelId());
        assertEquals("52", result.get(1).sessionId());
        assertEquals("child-b", result.get(1).sessionName());
    }

    @Test
    void getLatestChildSessions_无子会话时返回空列表() {
        when(sessionMapper.selectList(any())).thenReturn(List.of());

        List<AgentExecutionContext.ChildSession> result = provider.getLatestChildSessions("60");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getLatestChildSessions_selectList返回null时返回空列表() {
        when(sessionMapper.selectList(any())).thenReturn(null);

        List<AgentExecutionContext.ChildSession> result = provider.getLatestChildSessions("70");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void saveSessionVariable_无用户上下文_新建变量userId为null() {
        UserContext.clear();
        when(sessionVariableMapper.selectList(any())).thenReturn(List.of());

        provider.saveSessionVariable("10", "key1", "val1");

        ArgumentCaptor<SessionVariable> captor = ArgumentCaptor.forClass(SessionVariable.class);
        verify(sessionVariableMapper).insert(captor.capture());
        assertEquals(10L, captor.getValue().getSessionId());
        assertEquals("key1", captor.getValue().getVariableKey());
        assertNull(captor.getValue().getUserId(), "无用户上下文时新建变量 userId 应为 null（容忍系统级异步流程）");
    }

    @Test
    void createChildSession_无用户上下文_子会话userId为null() {
        UserContext.clear();
        when(sessionMapper.selectById(1L)).thenReturn(new Session());
        doAnswer(invocation -> {
            Session s = invocation.getArgument(0);
            s.setId(999L);
            return null;
        }).when(sessionMapper).insert(any(Session.class));

        provider.createChildSession("1", "a", null, null, null, null, "p");

        verify(sessionMapper).insert(sessionCaptor.capture());
        assertNull(sessionCaptor.getValue().getUserId(), "无用户上下文时子会话 userId 应为 null（容忍系统级异步流程）");
    }
}
