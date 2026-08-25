package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.enums.LogType;
import com.ghost616.agentbase.enums.ToolType;
import com.ghost616.agentbase.sendmessage.ChildCreateSession;
import com.ghost616.agentbase.sendmessage.ConversationIdMessage;
import com.ghost616.agentbase.sendmessage.HistoryMessage;
import com.ghost616.agentbase.sendmessage.VariableMessage;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.CacheRemoveLogData;
import com.ghost616.agentbase.service.agent.log.ChildSessionLogData;
import com.ghost616.agentbase.service.agent.log.ContextBuildLogData;
import com.ghost616.agentbase.service.agent.log.HandleMessageLogData;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.RefreshLogData;
import com.ghost616.agentbase.service.agent.log.SendMessageLogData;
import com.ghost616.agentbase.service.agent.log.SendParentMessageLogData;
import com.ghost616.agentbase.service.agent.log.SessionErrorLogData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentContextManagerLogTest {

    @Mock
    private ContextDataProvider dataProvider;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private AgentLog agentLog;

    private AgentComponentRegistry registry;
    private AgentContextManager agentContextManager;

    private final String sessionId = "1";
    private final String agentId = "100";

    @BeforeEach
    void setUp() {
        registry = new AgentComponentRegistry();
        registry.setContextDataProvider(dataProvider);
        registry.setSessionManager(sessionManager);
        registry.setToolManager(toolManager);
        registry.setAgentLog(agentLog);
        agentContextManager = new AgentContextManager(registry);
    }

    private void stubBasicContext() {
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(sessionManager.getMessages(sessionId)).thenReturn(List.of());
        when(toolManager.getSessionTools(eq(sessionId), anyBoolean())).thenReturn(List.of());
    }

    private void buildContext() {
        stubBasicContext();
        // 懒构建：build 仅轻量构建，访问 context() 触发完整构建并记录 CONTEXT_BUILD 日志
        agentContextManager.build(sessionId).build().context();
    }

    @Test
    void agentLog为null时build不抛异常且addLog不调用() {
        registry.setAgentLog(null);
        stubBasicContext();

        AgentContextManager.AgentSessionContext ctx =
                assertDoesNotThrow(() -> agentContextManager.build(sessionId).build());

        assertNotNull(ctx);
        // 懒构建：触发完整构建以使用 stubBasicContext 中的 stub
        assertDoesNotThrow(ctx::context);
        verify(agentLog, never()).addLog(any());
    }

    @Test
    void doBuild应记录CONTEXT_BUILD日志包含完整字段() {
        buildContext();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        List<LogData> captured = captor.getAllValues();

        ContextBuildLogData buildLog = captured.stream()
                .filter(l -> l.logType() == LogType.CONTEXT_BUILD)
                .map(l -> (ContextBuildLogData) l)
                .findFirst().orElse(null);

        assertNotNull(buildLog);
        assertEquals(LogLevel.INFO, buildLog.getLogLevel());
        assertEquals(sessionId, buildLog.getSessionId());
        assertEquals(agentId, buildLog.getAgentId());
        assertEquals("200", buildLog.getModelId());
        assertEquals(0, buildLog.getToolCount());
        assertEquals(0, buildLog.getHistoryCount());
        assertFalse(buildLog.isSubSession());
        assertFalse(buildLog.isCacheHit());
        assertNotNull(buildLog.getSessionVariables());
        assertTrue(buildLog.getSessionVariables().isEmpty());
    }

    @Test
    void doBuild应记录会话变量且为防御性复制() {
        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put("k1", "v1");
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(), sessionVars, null, null, null, null));
        when(sessionManager.getMessages(sessionId)).thenReturn(List.of());
        when(toolManager.getSessionTools(eq(sessionId), anyBoolean())).thenReturn(List.of());

        // 懒构建：build 仅轻量构建，访问 context() 触发完整构建并记录 CONTEXT_BUILD 日志
        agentContextManager.build(sessionId).build().context();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        ContextBuildLogData buildLog = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.CONTEXT_BUILD)
                .map(l -> (ContextBuildLogData) l)
                .findFirst().orElse(null);

        assertNotNull(buildLog);
        assertEquals("v1", buildLog.getSessionVariables().get("k1"));
        assertNotSame(sessionVars, buildLog.getSessionVariables());
    }

    @Test
    void doBuild子会话应记录isSubSession为true() {
        String childSessionId = "2";
        String parentId = "1";
        when(dataProvider.loadAgentContext(parentId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "parent", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "sub prompt", "200", 10, List.of(), Map.of(), parentId, null, null, null));
        when(sessionManager.getMessages(anyString())).thenReturn(List.of());
        when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());

        // 懒构建：build 仅轻量构建，访问 context() 触发完整构建并记录 CONTEXT_BUILD 日志
        agentContextManager.build(childSessionId).build().context();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        ContextBuildLogData buildLog = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.CONTEXT_BUILD)
                .map(l -> (ContextBuildLogData) l)
                .filter(l -> childSessionId.equals(l.getSessionId()))
                .findFirst().orElse(null);

        assertNotNull(buildLog);
        assertTrue(buildLog.isSubSession());
    }

    @Test
    void doBuild工具与历史数量应正确记录() {
        ToolConfigDTO tool = ToolConfigDTO.builder().name("t1").toolType(ToolType.JAVA).implPath("com.x.T").build();
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(toolManager.getSessionTools(eq(sessionId), anyBoolean())).thenReturn(List.of(
                new ToolManager.ToolSessionObject(tool, null, null, List.of(), List.of())));
        when(sessionManager.getMessages(sessionId)).thenReturn(List.of(
                new MessageDataProvider.MessageDTO("1", sessionId, "user", "hello", null, null,
                        LocalDateTime.now(), null, null, null, null, null, null, null, null, null),
                new MessageDataProvider.MessageDTO("2", sessionId, "assistant", "hi", null, null,
                        LocalDateTime.now(), null, null, null, null, null, null, null, null, null)));

        // 懒构建：build 仅轻量构建，访问 context() 触发完整构建并记录 CONTEXT_BUILD 日志
        agentContextManager.build(sessionId).build().context();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        ContextBuildLogData buildLog = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.CONTEXT_BUILD)
                .map(l -> (ContextBuildLogData) l)
                .findFirst().orElse(null);

        assertNotNull(buildLog);
        assertEquals(1, buildLog.getToolCount());
        assertEquals(2, buildLog.getHistoryCount());
    }

    @Test
    void 会话未找到时应记录ERROR日志() {
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(null);

        assertThrows(com.ghost616.agentbase.exception.AgentException.class,
                () -> agentContextManager.build(sessionId).build());

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();

        assertEquals(LogType.ERROR_LOG, logData.logType());
        SessionErrorLogData errorLog = (SessionErrorLogData) logData;
        assertEquals(LogLevel.ERROR, errorLog.getLogLevel());
        assertEquals(sessionId, errorLog.getSessionId());
        assertEquals("SESSION-001", errorLog.getErrorCode());
        assertTrue(errorLog.getMessage().contains(sessionId));
    }

    @Test
    void createChildSession应记录CHILD_SESSION日志() {
        stubBasicContext();
        String parentId = "1";
        String newChildId = "99";
        when(dataProvider.createChildSession(eq(parentId), eq("child"), eq("desc"), eq("300"),
                any(), any(), any())).thenReturn(newChildId);

        AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();
        String result = ctx.context().createChildSession("child", "desc", "300", List.of("t1"), List.of("s1"), "prompt");

        assertEquals(newChildId, result);
        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        ChildSessionLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.CHILD_SESSION)
                .map(l -> (ChildSessionLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals(LogLevel.INFO, log.getLogLevel());
        assertEquals(parentId, log.getSessionId());
        assertNull(log.getConversationId());
        assertEquals(newChildId, log.getChildSessionId());
        assertEquals("child", log.getSessionName());
        assertEquals("desc", log.getDescription());
        assertEquals("300", log.getModelId());
        assertEquals(List.of("t1"), log.getToolIds());
        assertEquals(List.of("s1"), log.getSkillIds());
        assertEquals("prompt", log.getPrompt());
    }

    @Test
    void sendUserMessage应记录SEND_MESSAGE日志() {
        stubBasicContext();
        SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
        when(sessionManager.messageSave()).thenReturn(msgBuilder);

        AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();
        ctx.context().sendUserMessage("99", "hello", "300", true);

        verify(msgBuilder).sessionId("99");
        verify(msgBuilder).role("user");
        verify(msgBuilder).content("hello");
        verify(msgBuilder).conversationId(null);
        verify(msgBuilder).save();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        SendMessageLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.SEND_MESSAGE)
                .map(l -> (SendMessageLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals(LogLevel.INFO, log.getLogLevel());
        assertEquals(sessionId, log.getSessionId());
        assertNull(log.getConversationId());
        assertEquals("99", log.getChildSessionId());
        assertEquals("hello", log.getContent());
        assertEquals("300", log.getModelId());
        assertTrue(log.getThinking());
    }

    @Test
    void 子会话上下文中createChildSession和sendUserMessage日志携带父会话conversationId() {
        String parentId = "1";
        String childId = "2";
        when(dataProvider.loadAgentContext(parentId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "parent", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(dataProvider.loadAgentContext(childId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "child", "200", 10, List.of(), Map.of(), parentId, null, null, null));
        when(sessionManager.getMessages(anyString())).thenReturn(List.of());
        when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());

        AgentContextManager.AgentSessionContext parentCtx = agentContextManager.build(parentId).build();
        parentCtx.mutator().setConversationId("conv-100");

        AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childId).build();

        SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
        when(sessionManager.messageSave()).thenReturn(msgBuilder);

        when(dataProvider.createChildSession(eq(parentId), eq("child"), eq("desc"), eq("300"),
                any(), any(), any())).thenReturn("99");

        String newChildId = childCtx.mutator().createChildSessionCallback.create(parentId, "child", "desc", "300",
                List.of("t1"), List.of("s1"), "prompt");
        childCtx.context().sendUserMessage("88", "hello", "300", true);

        verify(msgBuilder).sessionId("88");
        verify(msgBuilder).role("user");
        verify(msgBuilder).content("hello");
        verify(msgBuilder).conversationId("conv-100");
        verify(msgBuilder).save();

        assertEquals("99", newChildId);
        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());

        ChildSessionLogData childLog = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.CHILD_SESSION)
                .map(l -> (ChildSessionLogData) l)
                .findFirst().orElse(null);
        assertNotNull(childLog);
        assertEquals("conv-100", childLog.getConversationId());

        SendMessageLogData sendLog = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.SEND_MESSAGE)
                .map(l -> (SendMessageLogData) l)
                .findFirst().orElse(null);
        assertNotNull(sendLog);
        assertEquals("conv-100", sendLog.getConversationId());
    }

    @Test
    void 子会话sendParentMessage应记录SEND_PARENT_MESSAGE日志() {
        String parentId = "1";
        String childId = "2";
        when(dataProvider.loadAgentContext(parentId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "parent", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(dataProvider.loadAgentContext(childId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "child", "200", 10, List.of(), Map.of(), parentId, null, null, null));
        when(sessionManager.getMessages(anyString())).thenReturn(List.of());
        when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());

        AgentContextManager.AgentSessionContext parentCtx = agentContextManager.build(parentId).build();
        parentCtx.mutator().setConversationId("conv-100");

        AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childId).build();

        SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
        when(sessionManager.messageSave()).thenReturn(msgBuilder);

        childCtx.context().sendParentMessage("hello from child");

        verify(msgBuilder).sessionId(parentId);
        verify(msgBuilder).role("user");
        verify(msgBuilder).content("hello from child");
        verify(msgBuilder).conversationId("conv-100");
        verify(msgBuilder).save();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        SendParentMessageLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.SEND_PARENT_MESSAGE)
                .map(l -> (SendParentMessageLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals(LogLevel.INFO, log.getLogLevel());
        assertEquals(childId, log.getSessionId(), "日志 sessionId 应为调用方子会话");
        assertEquals(parentId, log.getParentSessionId());
        assertEquals("conv-100", log.getConversationId());
        assertEquals("hello from child", log.getContent());
    }

    @Test
    void 主会话sendParentMessage不记录SEND_PARENT_MESSAGE日志() {
        buildContext();

        AgentContextManager.AgentSessionContext ctx = agentContextManager.get(sessionId);
        ctx.context().sendParentMessage("hello");

        verify(agentLog, never()).addLog(argThat(l -> l.logType() == LogType.SEND_PARENT_MESSAGE));
    }

    @Test
    void refreshHistory应记录REFRESH_HISTORY日志() {
        buildContext();
        when(dataProvider.getLatestMessages(sessionId)).thenReturn(List.of());

        agentContextManager.refreshHistory(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        RefreshLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.REFRESH)
                .map(l -> (RefreshLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals(LogLevel.INFO, log.getLogLevel());
        assertEquals(sessionId, log.getContext().getSessionId());
        assertEquals("HISTORY", log.getRefreshTarget());
    }

    @Test
    void refreshSessionVariables应记录REFRESH日志() {
        buildContext();
        when(dataProvider.getLatestSessionVariables(sessionId)).thenReturn(Map.of("k", "v"));

        agentContextManager.refreshSessionVariables(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        RefreshLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.REFRESH)
                .map(l -> (RefreshLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals("SESSION_VARIABLES", log.getRefreshTarget());
    }

    @Test
    void refreshConversationVariables应记录REFRESH日志() {
        buildContext();
        when(dataProvider.getLatestConversationVariables(sessionId)).thenReturn(Map.of("k", "v"));

        agentContextManager.refreshConversationVariables(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        RefreshLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.REFRESH)
                .map(l -> (RefreshLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals("CONVERSATION_VARIABLES", log.getRefreshTarget());
    }

    @Test
    void refreshChildSessions应记录REFRESH日志() {
        buildContext();
        when(dataProvider.getLatestChildSessions(sessionId)).thenReturn(List.of());

        agentContextManager.refreshChildSessions(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        RefreshLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.REFRESH)
                .map(l -> (RefreshLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals("CHILD_SESSIONS", log.getRefreshTarget());
    }

    @Test
    void 缓存无上下文时refresh不记录REFRESH日志() {
        agentContextManager.refreshHistory("999");

        verify(agentLog, never()).addLog(any());
    }

    @Test
    void refreshHistory数据更新先于日志记录() {
        buildContext();
        when(dataProvider.getLatestMessages(sessionId)).thenReturn(List.of());

        agentContextManager.refreshHistory(sessionId);

        AgentExecutionContext context = agentContextManager.get(sessionId).context();
        InOrder inOrder = inOrder(dataProvider, agentLog);
        inOrder.verify(dataProvider).getLatestMessages(sessionId);
        inOrder.verify(agentLog).addLog(argThat(l -> l.logType() == LogType.REFRESH));
        assertEquals(0, context.getHistory().size());
    }

    @Test
    void handleVariableMessage业务执行先于日志记录() {
        buildContext();

        agentContextManager.handleVariableMessage(new VariableMessage(sessionId, "SESSION", "k", "v", "PUT"));

        AgentExecutionContext context = agentContextManager.get(sessionId).context();
        InOrder inOrder = inOrder(agentLog);
        inOrder.verify(agentLog).addLog(argThat(l -> l.logType() == LogType.HANDLE_MESSAGE));
        assertEquals("v", context.getSessionVariable("k"));
    }

    @Test
    void handleChildCreateSession应记录HANDLE_MESSAGE日志() {
        buildContext();

        ChildCreateSession message = new ChildCreateSession(sessionId,
                new AgentExecutionContext.ChildSession("10", "child", "desc", "300"));
        agentContextManager.handleChildCreateSession(message);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        HandleMessageLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.HANDLE_MESSAGE)
                .map(l -> (HandleMessageLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals(LogLevel.INFO, log.getLogLevel());
        assertSame(message, log.getSessionMessage());
    }

    @Test
    void handleHistoryMessage应记录HANDLE_MESSAGE日志() {
        buildContext();

        HistoryMessage message = new HistoryMessage(sessionId,
                new AgentExecutionContext.HistoryEntry("user", "hello", null, null,
                        LocalDateTime.now(), List.of(), null, null, null, null, null));
        agentContextManager.handleHistoryMessage(message);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        HandleMessageLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.HANDLE_MESSAGE)
                .map(l -> (HandleMessageLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertSame(message, log.getSessionMessage());
    }

    @Test
    void handleVariableMessage应记录HANDLE_MESSAGE日志() {
        buildContext();

        VariableMessage message = new VariableMessage(sessionId, "SESSION", "k", "v", "PUT");
        agentContextManager.handleVariableMessage(message);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        HandleMessageLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.HANDLE_MESSAGE)
                .map(l -> (HandleMessageLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertSame(message, log.getSessionMessage());
    }

    @Test
    void handleConversationIdMessage应记录HANDLE_MESSAGE日志() {
        buildContext();

        ConversationIdMessage message = new ConversationIdMessage(sessionId, "conv-1");
        agentContextManager.handleConversationIdMessage(message);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        HandleMessageLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.HANDLE_MESSAGE)
                .map(l -> (HandleMessageLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertSame(message, log.getSessionMessage());
    }

    @Test
    void remove应记录CACHE_REMOVE日志() {
        buildContext();

        agentContextManager.remove(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        CacheRemoveLogData log = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.CACHE_REMOVE)
                .map(l -> (CacheRemoveLogData) l)
                .findFirst().orElse(null);

        assertNotNull(log);
        assertEquals(LogLevel.INFO, log.getLogLevel());
        assertEquals(sessionId, log.getSessionId());
    }

    @Test
    void agentLog抛异常时不中断主流程() {
        stubBasicContext();
        doThrow(new RuntimeException("log failure")).when(agentLog).addLog(any());

        // 懒构建：触发完整构建验证 addLog 异常不中断主流程（addLog 内部 try-catch）
        assertDoesNotThrow(() -> agentContextManager.build(sessionId).build().context());
    }
}
