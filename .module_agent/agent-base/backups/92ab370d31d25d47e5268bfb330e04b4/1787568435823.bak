package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.enums.LogType;
import com.ghost616.agentbase.service.agent.invoker.BuiltinToolInvoker;
import com.ghost616.agentbase.service.agent.invoker.HookData;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.SystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.ToolCallQueueManager;
import com.ghost616.agentbase.service.agent.invoker.ToolInvoker;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.ToolContinueLogData;
import com.ghost616.agentbase.service.agent.log.ToolExecuteLogData;
import com.ghost616.agentbase.util.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolExecutionServiceTest {

    @Mock
    private ToolCallQueueManager toolCallQueueManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private SystemToolManager systemToolManager;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ChatService chatService;
    @Mock
    private AgentContextManager agentContextManager;
    @Mock
    private ToolExecutionTracker toolExecutionTracker;
    @Mock
    private ChatDataProvider chatDataProvider;
    @Mock
    private AgentLog agentLog;

    private AgentComponentRegistry registry;
    private ToolExecutionService toolExecutionService;

    private final String sessionId = "1";

    @BeforeEach
    void setUp() {
        registry = new AgentComponentRegistry();
        registry.setHookManager(new HookManager(registry));
        registry.setToolCallQueueManager(toolCallQueueManager);
        registry.setToolManager(toolManager);
        registry.setSystemToolManager(systemToolManager);
        registry.setSessionManager(sessionManager);
        registry.setAgentContextManager(agentContextManager);
        registry.setToolExecutionTracker(toolExecutionTracker);
        registry.setChatDataProvider(chatDataProvider);
        registry.setAgentLog(agentLog);
        toolExecutionService = new ToolExecutionService(registry, chatService);
    }

    @Test
    void hookManager_shouldDelegateTriggerHooksInExecuteTool() {
        HookManager mockHookManager = mock(HookManager.class);
        registry.setHookManager(mockHookManager);

        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid_hook", "hookTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "hookTool")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid_hook", "hookTool", "{}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        toolExecutionService.executeTool(sessionId);

        verify(mockHookManager).triggerSessionHooks(eq(sessionId), eq(HookPhase.BEFORE_TOOL_CALL), eq(context), any(HookData.class));
        verify(mockHookManager).triggerHooks(eq(HookPhase.BEFORE_TOOL_CALL), eq(context), any(HookData.class));
        verify(mockHookManager, atLeastOnce()).executePostHooks(eq(context), any(HookData.class));
    }

    // ========== executeTool ==========

    @Test
    void executeTool_队列为空时返回empty() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        when(toolCallQueueManager.peek(sessionId)).thenReturn(null);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertEquals("empty", result.status());
        assertNull(result.toolId());
        assertNull(result.toolName());
    }

    @Test
    void executeTool_工具名以_sys_开头时调用SystemToolManager() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid1", "_sys_getWeather", "{\"loc\":\"Beijing\"}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        SystemTool sysInvoker = mock(SystemTool.class);
        when(systemToolManager.getSystemTool("getWeather")).thenReturn(sysInvoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid1", "_sys_getWeather", "{\"loc\":\"Beijing\"}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(systemToolManager).getSystemTool("getWeather");
        assertEquals("executing", result.status());
        assertEquals("tid1", result.toolId());
    }

    @Test
    void executeTool_非_sys_前缀时调用ToolManager() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid2", "myTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "myTool")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid2", "myTool", "{}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(toolManager).getInvoker(sessionId, "myTool");
        assertEquals("executing", result.status());
    }

    @Test
    void executeTool_invoker为null时消费队列_hasMore为false() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid3", "unknownTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(toolManager.getInvoker(sessionId, "unknownTool")).thenReturn(null);
        when(toolCallQueueManager.poll(sessionId)).thenReturn(peekData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(toolCallQueueManager).poll(sessionId);
        verify(toolCallQueueManager).hasPending(sessionId);
        assertEquals("executing", result.status());
        assertEquals("tid3", result.toolId());
        assertEquals("{}", result.arguments());
        assertFalse(result.hasMore());
        assertNull(result.message());
    }

    @Test
    void executeTool_invoker为null时消费队列_hasMore为true() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tidx", "unknownTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(toolManager.getInvoker(sessionId, "unknownTool")).thenReturn(null);
        when(toolCallQueueManager.poll(sessionId)).thenReturn(peekData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(true);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(toolCallQueueManager).poll(sessionId);
        assertTrue(result.hasMore());
        assertEquals("executing", result.status());
        assertNull(result.message());
    }

    @Test
    void executeTool_invoker为null时poll后无pending则hasMore为false() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tidy", "unknownTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(toolManager.getInvoker(sessionId, "unknownTool")).thenReturn(null);
        when(toolCallQueueManager.poll(sessionId)).thenReturn(peekData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertFalse(result.hasMore());
        assertEquals("executing", result.status());
    }

    @Test
    void executeTool_工具名以_$开头且invoker为null时使用BuiltinToolInvoker透传arguments() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid$1", "$builtinTool", "{\"key\":\"val\"}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(toolManager.getInvoker(sessionId, "$builtinTool")).thenReturn(null);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid$1", "$builtinTool", "{\"key\":\"val\"}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        when(toolManager.execute(any(ToolInvoker.class), eq(context), eq("{\"key\":\"val\"}"))).thenAnswer(inv -> inv.getArgument(2));

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertEquals("executing", result.status());
        assertEquals("tid$1", result.toolId());
        assertEquals("$builtinTool", result.toolName());
        assertEquals("{\"key\":\"val\"}", result.arguments());
        assertFalse(result.hasMore());

        verify(toolManager, timeout(2000)).execute(any(BuiltinToolInvoker.class), eq(context), eq("{\"key\":\"val\"}"));
        verify(toolExecutionTracker, timeout(2000)).setDone(eq(sessionId), eq("tid$1"), eq("{\"key\":\"val\"}"));
    }

    @Test
    void executeTool_非_$前缀且invoker为null时写入工具调用器不存在错误() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tidErr", "unknownTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(toolManager.getInvoker(sessionId, "unknownTool")).thenReturn(null);
        when(toolCallQueueManager.poll(sessionId)).thenReturn(peekData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertEquals("executing", result.status());
        verify(toolExecutionTracker).setDone(eq(sessionId), eq("tidErr"), eq("{\"status\":\"error\",\"errMsg\":\"工具调用器不存在\"}"));
        verify(toolManager, never()).execute(any(), any(), any());
    }

    @Test
    void executeTool_获取调用器抛出异常时返回failed() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid4", "_sys_broken", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(systemToolManager.getSystemTool("broken")).thenThrow(new RuntimeException("connection error"));

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertEquals("failed", result.status());
        assertEquals("tid4", result.toolId());
        assertEquals("connection error", result.message());
    }

    @Test
    void executeTool_sessionContext为null时返回error() {
        when(agentContextManager.get(sessionId)).thenReturn(null);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertEquals("error", result.status());
        assertEquals("session not found", result.message());
        verify(toolCallQueueManager, never()).peek(sessionId);
    }

    @Test
    void executeTool_contextStopped时清理并返回empty() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid6", "myTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "myTool")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid6", "myTool", "{}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(true);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(toolCallQueueManager).clear(sessionId);
        verify(toolExecutionTracker).clear(sessionId);
        assertEquals("empty", result.status());
    }

    @Test
    void executeTool_正常流程返回executing() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid7", "normalTool", "{\"key\":\"val\"}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "normalTool")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid7", "normalTool", "{\"key\":\"val\"}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(true);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(toolExecutionTracker).setExecuting(sessionId, "tid7", "normalTool", "{\"key\":\"val\"}", true);
        assertEquals("executing", result.status());
        assertEquals("tid7", result.toolId());
        assertEquals("normalTool", result.toolName());
        assertEquals("{\"key\":\"val\"}", result.arguments());
        assertTrue(result.hasMore());
    }

    @Test
    void executeTool_注入ThreadVariableHandler时提交前捕获_异步线程内应用() throws Exception {
        ThreadVariableHandler handler = mock(ThreadVariableHandler.class);
        CountDownLatch appliedLatch = new CountDownLatch(1);
        ThreadVariableWrapper wrapper = appliedLatch::countDown;
        when(handler.wrap()).thenReturn(wrapper);
        registry.setThreadVariableHandler(handler);

        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid_tv", "tvTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "tvTool")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid_tv", "tvTool", "{}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        verify(handler).wrap();
        assertTrue(appliedLatch.await(3, TimeUnit.SECONDS), "异步线程应调用 wrapper.apply()");
        assertEquals("executing", result.status());
    }

    @Test
    void executeTool_未注入ThreadVariableHandler时不影响正常执行() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid_tv2", "tvTool2", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "tvTool2")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid_tv2", "tvTool2", "{}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionService.ToolExecutionResult result = toolExecutionService.executeTool(sessionId);

        assertEquals("executing", result.status());
    }

    // ========== getToolStatus ==========

    @Test
    void getToolStatus_无执行中状态时返回idle() {
        when(toolExecutionTracker.getCurrentExecution(sessionId, "tid")).thenReturn(null);

        ToolExecutionService.ToolStatusResult result = toolExecutionService.getToolStatus(sessionId, "tid");

        assertEquals("idle", result.status());
        assertNull(result.toolId());
        assertNull(result.toolName());
    }

    @Test
    void getToolStatus_有执行中状态时返回对应数据() {
        ToolExecutionTracker.ToolExecutionStatus status = new ToolExecutionTracker.ToolExecutionStatus(
                "tid8", "runningTool", "{}", "executing", null, false);
        when(toolExecutionTracker.getCurrentExecution(sessionId, "tid8")).thenReturn(status);

        ToolExecutionService.ToolStatusResult result = toolExecutionService.getToolStatus(sessionId, "tid8");

        assertEquals("executing", result.status());
        assertEquals("tid8", result.toolId());
        assertEquals("runningTool", result.toolName());
        assertEquals("{}", result.arguments());
        assertFalse(result.hasMore());
        assertNull(result.result());
    }

    // ========== continueAfterTools ==========

    @Test
    void continueAfterTools_sessionStopped时清理并返回FluxEmpty() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(true);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        Flux<ServerSentEvent<ChatChunk>> result = toolExecutionService.continueAfterTools(sessionId);

        verify(toolCallQueueManager).clear(sessionId);
        verify(toolExecutionTracker).clear(sessionId);
        assertSame(Flux.empty(), result);
    }

    @Test
    void continueAfterTools_正常流程() throws Exception {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionTracker.ToolResult toolResult = new ToolExecutionTracker.ToolResult(
                "tid10", "doneTool", "{}", "result_ok");
        when(toolExecutionTracker.getAndClearResults(sessionId)).thenReturn(List.of(toolResult));

        SessionManager.MessageSaveBuilder saveBuilder = mock(SessionManager.MessageSaveBuilder.class);
        when(saveBuilder.sessionId(any())).thenReturn(saveBuilder);
        when(saveBuilder.role(any())).thenReturn(saveBuilder);
        when(saveBuilder.content(any())).thenReturn(saveBuilder);
        when(saveBuilder.toolInfo(any())).thenReturn(saveBuilder);
        when(saveBuilder.toolResult(any())).thenReturn(saveBuilder);
        when(saveBuilder.conversationId(any())).thenReturn(saveBuilder);
        when(saveBuilder.save()).thenReturn("100");
        when(sessionManager.messageSave()).thenReturn(saveBuilder);

        Flux<ServerSentEvent<ChatChunk>> expectedFlux = Flux.empty();
        when(chatService.chat(any())).thenReturn(expectedFlux);

        Flux<ServerSentEvent<ChatChunk>> result = toolExecutionService.continueAfterTools(sessionId);

        verify(toolExecutionTracker).getAndClearResults(sessionId);
        verify(saveBuilder).sessionId(sessionId);
        verify(saveBuilder).role("tool");
        verify(saveBuilder).content("result_ok");
        verify(saveBuilder).toolInfo(new ToolInfo("tid10", "doneTool"));
        ArgumentCaptor<String> toolResultCaptor = ArgumentCaptor.forClass(String.class);
        verify(saveBuilder).toolResult(toolResultCaptor.capture());
        String capturedResult = toolResultCaptor.getValue();
        assertTrue(capturedResult.contains("doneTool"));
        assertTrue(capturedResult.contains("result_ok"));
        verify(agentContextManager).addHistoryEntry(eq(sessionId), any(AgentExecutionContext.HistoryEntry.class));
        verify(toolCallQueueManager).clear(sessionId);
        verify(toolExecutionTracker).clear(sessionId);
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(requestCaptor.capture());
        assertEquals(sessionId, requestCaptor.getValue().getSessionId());
        assertEquals(ChatService.TOOL_CONTINUE_MARKER, requestCaptor.getValue().getContent());
        assertSame(expectedFlux, result);
    }

    @Test
    void continueAfterTools_无结果时跳过持久化和历史记录() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        when(toolExecutionTracker.getAndClearResults(sessionId)).thenReturn(List.of());

        Flux<ServerSentEvent<ChatChunk>> expectedFlux = Flux.empty();
        when(chatService.chat(any())).thenReturn(expectedFlux);

        toolExecutionService.continueAfterTools(sessionId);

        verify(sessionManager, never()).messageSave();
        verify(agentContextManager, never()).addHistoryEntry(any(), any());
    }

    // ========== 智能体日志 ==========

    @Test
    void executeTool_队列为空时记录empty日志() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        when(toolCallQueueManager.peek(sessionId)).thenReturn(null);

        toolExecutionService.executeTool(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.TOOL_EXECUTE, logData.logType());
        ToolExecuteLogData toolLog = (ToolExecuteLogData) logData;
        assertEquals(LogLevel.INFO, toolLog.getLogLevel());
        assertEquals(sessionId, toolLog.getSessionId());
        assertSame(context, toolLog.getContext());
        assertEquals("empty", toolLog.getQueueStatus());
    }

    @Test
    void executeTool_获取调用器异常时记录failed日志() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid4", "_sys_broken", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(systemToolManager.getSystemTool("broken")).thenThrow(new RuntimeException("connection error"));

        toolExecutionService.executeTool(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.TOOL_EXECUTE, logData.logType());
        ToolExecuteLogData toolLog = (ToolExecuteLogData) logData;
        assertEquals(LogLevel.ERROR, toolLog.getLogLevel());
        assertSame(context, toolLog.getContext());
        assertEquals("tid4", toolLog.getToolCallId());
        assertEquals("_sys_broken", toolLog.getToolCallName());
        assertEquals("system", toolLog.getToolType());
        assertEquals("failed", toolLog.getQueueStatus());
    }

    @Test
    void executeTool_invoker不存在时记录error日志() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tidErr", "unknownTool", "{}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        when(toolManager.getInvoker(sessionId, "unknownTool")).thenReturn(null);
        when(toolCallQueueManager.poll(sessionId)).thenReturn(peekData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(false);

        toolExecutionService.executeTool(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.TOOL_EXECUTE, logData.logType());
        ToolExecuteLogData toolLog = (ToolExecuteLogData) logData;
        assertEquals(LogLevel.ERROR, toolLog.getLogLevel());
        assertSame(context, toolLog.getContext());
        assertEquals("tidErr", toolLog.getToolCallId());
        assertEquals("unknownTool", toolLog.getToolCallName());
        assertEquals("regular", toolLog.getToolType());
        assertEquals("error", toolLog.getQueueStatus());
    }

    @Test
    void executeTool_sessionContext不存在时记录error日志() {
        when(agentContextManager.get(sessionId)).thenReturn(null);

        toolExecutionService.executeTool(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.TOOL_EXECUTE, logData.logType());
        ToolExecuteLogData toolLog = (ToolExecuteLogData) logData;
        assertEquals(LogLevel.ERROR, toolLog.getLogLevel());
        assertEquals(sessionId, toolLog.getSessionId());
        assertNull(toolLog.getToolCallId());
        assertNull(toolLog.getToolCallName());
        assertNull(toolLog.getToolType());
        assertEquals("error", toolLog.getQueueStatus());
    }

    @Test
    void executeTool_正常流程记录executing日志() {
        MessageDataProvider.ToolCallData peekData = new MessageDataProvider.ToolCallData("tid7", "normalTool", "{\"key\":\"val\"}");
        when(toolCallQueueManager.peek(sessionId)).thenReturn(peekData);
        ToolInvoker invoker = mock(ToolInvoker.class);
        when(toolManager.getInvoker(sessionId, "normalTool")).thenReturn(invoker);

        MessageDataProvider.ToolCallData pollData = new MessageDataProvider.ToolCallData("tid7", "normalTool", "{\"key\":\"val\"}");
        when(toolCallQueueManager.poll(sessionId)).thenReturn(pollData);
        when(toolCallQueueManager.hasPending(sessionId)).thenReturn(true);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        toolExecutionService.executeTool(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeastOnce()).addLog(captor.capture());
        ToolExecuteLogData toolLog = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.TOOL_EXECUTE)
                .map(l -> (ToolExecuteLogData) l)
                .filter(l -> "executing".equals(l.getQueueStatus()))
                .findFirst().orElse(null);

        assertNotNull(toolLog);
        assertEquals(LogLevel.INFO, toolLog.getLogLevel());
        assertSame(context, toolLog.getContext());
        assertEquals("tid7", toolLog.getToolCallId());
        assertEquals("normalTool", toolLog.getToolCallName());
        assertEquals("{\"key\":\"val\"}", toolLog.getToolCallArguments());
        assertEquals("regular", toolLog.getToolType());
    }

    @Test
    void continueAfterTools应记录TOOL_CONTINUE日志() {
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(context.isStopped()).thenReturn(false);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);

        ToolExecutionTracker.ToolResult toolResult = new ToolExecutionTracker.ToolResult(
                "tid10", "doneTool", "{}", "result_ok");
        when(toolExecutionTracker.getAndClearResults(sessionId)).thenReturn(List.of(toolResult));

        SessionManager.MessageSaveBuilder saveBuilder = mock(SessionManager.MessageSaveBuilder.class);
        when(saveBuilder.sessionId(any())).thenReturn(saveBuilder);
        when(saveBuilder.role(any())).thenReturn(saveBuilder);
        when(saveBuilder.content(any())).thenReturn(saveBuilder);
        when(saveBuilder.toolInfo(any())).thenReturn(saveBuilder);
        when(saveBuilder.toolResult(any())).thenReturn(saveBuilder);
        when(saveBuilder.conversationId(any())).thenReturn(saveBuilder);
        when(saveBuilder.save()).thenReturn("100");
        when(sessionManager.messageSave()).thenReturn(saveBuilder);

        when(chatService.chat(any())).thenReturn(Flux.empty());

        toolExecutionService.continueAfterTools(sessionId);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog).addLog(captor.capture());
        LogData logData = captor.getValue();
        assertEquals(LogType.TOOL_CONTINUE, logData.logType());
        ToolContinueLogData continueLog = (ToolContinueLogData) logData;
        assertEquals(LogLevel.INFO, continueLog.getLogLevel());
        assertSame(context, continueLog.getContext());
        assertEquals(sessionId, continueLog.getSessionId());
        assertEquals(1, continueLog.getResultCount());
        assertEquals(List.of("doneTool"), continueLog.getToolNames());
    }

    @Test
    void agentLog为null时addLog静默跳过() {
        registry.setAgentLog(null);
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        when(toolCallQueueManager.peek(sessionId)).thenReturn(null);

        toolExecutionService.executeTool(sessionId);

        verify(agentLog, never()).addLog(any());
    }

    @Test
    void agentLog抛异常时不中断主流程() {
        doThrow(new RuntimeException("log failure")).when(agentLog).addLog(any());
        AgentContextManager.AgentSessionContext sessionCtx = mock(AgentContextManager.AgentSessionContext.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);
        when(sessionCtx.context()).thenReturn(context);
        when(agentContextManager.get(sessionId)).thenReturn(sessionCtx);
        when(toolCallQueueManager.peek(sessionId)).thenReturn(null);

        assertDoesNotThrow(() -> toolExecutionService.executeTool(sessionId));
    }
}
