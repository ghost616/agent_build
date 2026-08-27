package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.model.ToolCallDelta;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.enums.LogType;
import com.ghost616.agentbase.service.agent.invoker.HistoryQuerySystemTool;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.HistoryExpandLogData;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.ModelCallLogData;
import com.ghost616.agentbase.service.agent.log.RequestEntryLogData;
import com.ghost616.agentbase.service.agent.log.RouteLogData;
import com.ghost616.agentbase.service.agent.log.SkillLoadLogData;
import com.ghost616.agentbase.service.agent.log.StreamEventLogData;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceLogTest {

    @Mock
    private AgentContextManager agentContextManager;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ModelInvokerManager modelInvokerManager;
    @Mock
    private ChatDataProvider chatDataProvider;
    @Mock
    private SystemToolManager systemToolManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private HookManager hookManager;
    @Mock
    private ModelInvoker modelInvoker;
    @Mock
    private SessionManager.MessageSaveBuilder msgBuilder;
    @Mock
    private AgentLog agentLog;

    private AgentComponentRegistry registry;
    private ChatService chatService;

    private final String sessionId = "1";

    @BeforeEach
    void setUp() {
        lenient().when(msgBuilder.sessionId(any())).thenReturn(msgBuilder);
        lenient().when(msgBuilder.role(any())).thenReturn(msgBuilder);
        lenient().when(msgBuilder.content(any())).thenReturn(msgBuilder);
        lenient().when(msgBuilder.images(any())).thenReturn(msgBuilder);
        lenient().when(msgBuilder.userInput(any())).thenReturn(msgBuilder);
        lenient().when(msgBuilder.conversationId(any())).thenReturn(msgBuilder);
        lenient().when(sessionManager.messageSave()).thenReturn(msgBuilder);

        registry = new AgentComponentRegistry();
        registry.setAgentContextManager(agentContextManager);
        registry.setSessionManager(sessionManager);
        registry.setModelInvokerManager(modelInvokerManager);
        registry.setSystemToolManager(systemToolManager);
        registry.setToolManager(toolManager);
        registry.setChatDataProvider(chatDataProvider);
        registry.setHookManager(hookManager);
        chatService = new ChatService(registry);
    }

    private static class Harness {
        final AgentExecutionContext context;
        final AgentExecutionContext.AgentContextMutator mutator;

        Harness() {
            this.mutator = new AgentExecutionContext.AgentContextMutator();
            this.context = new AgentExecutionContext(
                    "1", "agent-1", "sys_prompt", "model-1", null,
                    new ArrayList<>(), new ArrayList<>(), null, mutator,
                    new HashMap<>(), new HashMap<>(), null, "", null, null);
        }
    }

    private void setupChatPipeline(Harness harness, Flux<ChatChunk> stream) {
        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(
                new AgentContextManager.AgentSessionContext(harness.context, harness.mutator,
                        new AtomicBoolean(false)));
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());
        lenient().when(chatDataProvider.getModelConfig(any())).thenReturn(
                new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        lenient().when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        lenient().when(modelInvoker.invokeStream(any())).thenReturn(stream);
    }

    @Test
    @DisplayName("agentLog 未注入(null)时，chat() 不应抛异常，addLog 应静默跳过")
    void agentLog为null时chat不抛异常() {
        Harness harness = new Harness();
        setupChatPipeline(harness, Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content("hello").conversationId("conv-1").build();
        assertDoesNotThrow(() -> chatService.chat(request));
        verify(agentLog, never()).addLog(any());
    }

    @Test
    @DisplayName("注入 agentLog 后，chat() 应记录一条包含全部字段的 RequestEntryLogData")
    void chat应记录请求入口日志() {
        registry.setAgentLog(agentLog);
        Harness harness = new Harness();
        setupChatPipeline(harness, Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content("hello").conversationId("conv-1").build();
        chatService.chat(request);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, times(3)).addLog(captor.capture());
        List<LogData> captured = captor.getAllValues();

        assertEquals(3, captured.size());
        assertEquals(LogType.REQUEST_ENTRY, captured.get(0).logType());
        RequestEntryLogData logData = (RequestEntryLogData) captured.get(0);
        assertEquals(LogLevel.INFO, logData.getLogLevel());
        assertSame(harness.context, logData.getContext());
        assertEquals(sessionId, logData.getContext().getSessionId());
        assertEquals("model-1", logData.getModelId());
        assertEquals("conv-1", logData.getContext().getConversationId());
        assertEquals("hello", logData.getContent());
        assertFalse(logData.getIsToolContinue());
    }

    @Test
    @DisplayName("chat() 应记录路由日志 RouteLogData 与模型调用日志 ModelCallLogData")
    void chat应记录路由与模型调用日志() {
        registry.setAgentLog(agentLog);
        Harness harness = new Harness();
        setupChatPipeline(harness, Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content("hello").conversationId("conv-1").build();
        chatService.chat(request);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, times(3)).addLog(captor.capture());
        List<LogData> captured = captor.getAllValues();

        RouteLogData route = captured.stream()
                .filter(l -> l.logType() == LogType.ROUTE)
                .map(l -> (RouteLogData) l)
                .findFirst().orElse(null);
        assertNotNull(route);
        assertSame(harness.context, route.getContext());
        assertNull(route.getRequestType());

        ModelCallLogData modelCall = captured.stream()
                .filter(l -> l.logType() == LogType.MODEL_CALL)
                .map(l -> (ModelCallLogData) l)
                .findFirst().orElse(null);
        assertNotNull(modelCall);
        assertSame(harness.context, modelCall.getContext());
        assertEquals(2, modelCall.getMessageCount());
        assertEquals(0, modelCall.getToolCount());
        assertNotNull(modelCall.getToolNames());
        assertTrue(modelCall.getToolNames().isEmpty());
        assertNull(modelCall.getThinking());
    }

    @Test
    @DisplayName("content 为工具继续标记时，日志 isToolContinue 应为 true")
    void toolContinue时isToolContinue为true() {
        registry.setAgentLog(agentLog);
        Harness harness = new Harness();
        setupChatPipeline(harness, Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content(ChatService.TOOL_CONTINUE_MARKER).build();
        chatService.chat(request);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, times(3)).addLog(captor.capture());
        List<LogData> captured = captor.getAllValues();

        assertEquals(3, captured.size());
        assertEquals(LogType.REQUEST_ENTRY, captured.get(0).logType());
        RequestEntryLogData logData = (RequestEntryLogData) captured.get(0);
        assertEquals(ChatService.TOOL_CONTINUE_MARKER, logData.getContent());
        assertEquals(sessionId, logData.getContext().getSessionId());
        assertTrue(logData.getIsToolContinue());
    }

    @Test
    @DisplayName("agentLog 抛出 RuntimeException 时，chat() 不抛异常且主流程正常完成")
    void agentLog抛异常时chat不中断() {
        registry.setAgentLog(agentLog);
        doThrow(new RuntimeException("log failure")).when(agentLog).addLog(any());

        Harness harness = new Harness();
        setupChatPipeline(harness, Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content("hello").conversationId("conv-1").build();
        assertDoesNotThrow(() -> chatService.chat(request));

        verify(agentLog, times(3)).addLog(any());
        verify(modelInvoker).invokeStream(any());
    }

    @Test
    @DisplayName("流式响应订阅时应记录 ToolCallDetected 与 StreamComplete 事件")
    void 流式响应应记录工具检测与完成事件() {
        registry.setAgentLog(agentLog);
        Harness harness = new Harness();
        ChatChunk toolChunk = ChatChunk.builder()
                .toolCalls(List.of(ToolCallDelta.builder().id("tc1").name("get_weather").build()))
                .build();
        ChatChunk finishChunk = ChatChunk.builder().finishReason(FinishReason.STOP).build();
        setupChatPipeline(harness, Flux.just(toolChunk, finishChunk));

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content("hello").conversationId("conv-1").build();
        chatService.chat(request).subscribe();

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeast(4)).addLog(captor.capture());
        List<StreamEventLogData> streamEvents = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.STREAM_EVENT)
                .map(l -> (StreamEventLogData) l)
                .collect(Collectors.toList());

        assertEquals(2, streamEvents.size());
        assertEquals("ToolCallDetected", streamEvents.get(0).getEventType());
        assertSame(harness.context, streamEvents.get(0).getContext());
        assertTrue(streamEvents.get(0).getHasToolCalls());
        assertEquals("StreamComplete", streamEvents.get(1).getEventType());
        assertTrue(streamEvents.get(1).getHasToolCalls());
    }

    @Test
    @DisplayName("历史折叠时应记录 HistoryExpandLogData，expandedMessages 为展开的锚点消息内容")
    void 历史折叠时应记录展开锚点消息内容() {
        registry.setAgentLog(agentLog);

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        for (int g = 0; g < 12; g++) {
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "q" + g, null, null, LocalDateTime.now(),
                    List.of(), null, null, null, null, true));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "a" + g, null, null, LocalDateTime.now(),
                    List.of(), null, null, null, null, null));
        }
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, LocalDateTime.now(),
                List.of(), null, null, null, null, true));

        Map<String, String> convVars = new HashMap<>();
        convVars.put(HistoryQuerySystemTool.VAR_NAME, "[2]");
        AgentExecutionContext.AgentContextMutator mutator = new AgentExecutionContext.AgentContextMutator();
        AgentExecutionContext context = new AgentExecutionContext(
                "1", "agent-1", "sys_prompt", "model-1", 3,
                history, new ArrayList<>(), null, mutator,
                new HashMap<>(), convVars, null, "", null, null);

        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(
                new AgentContextManager.AgentSessionContext(context, mutator, new AtomicBoolean(false)));
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());
        lenient().when(chatDataProvider.getModelConfig(any())).thenReturn(
                new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        lenient().when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        lenient().when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content(ChatService.TOOL_CONTINUE_MARKER).build();
        chatService.chat(request);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeast(4)).addLog(captor.capture());
        HistoryExpandLogData historyExpand = captor.getAllValues().stream()
                .filter(l -> l.logType() == LogType.HISTORY_EXPAND)
                .map(l -> (HistoryExpandLogData) l)
                .findFirst().orElse(null);

        assertNotNull(historyExpand);
        assertSame(context, historyExpand.getContext());
        assertEquals(10, historyExpand.getFoldedCount());
        assertNotNull(historyExpand.getExpandedMessages());
        assertEquals(1, historyExpand.getExpandedMessages().size());
        assertTrue(historyExpand.getExpandedMessages().get(0).startsWith("【历史消息组2】"));
    }

    /**
     * 以指定会话变量执行一次 chat 请求（chatCompletions 路径），返回捕获的日志列表。
     */
    private List<LogData> executeChatWithSessionVars(Map<String, String> sessionVars) {
        registry.setAgentLog(agentLog);
        AgentExecutionContext.AgentContextMutator mutator = new AgentExecutionContext.AgentContextMutator();
        AgentExecutionContext context = new AgentExecutionContext(
                "1", "agent-1", "sys_prompt", "model-1", null,
                new ArrayList<>(), new ArrayList<>(), null, mutator,
                sessionVars, new HashMap<>(), null, "", null, null);

        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(
                new AgentContextManager.AgentSessionContext(context, mutator, new AtomicBoolean(false)));
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());
        lenient().when(chatDataProvider.getModelConfig(any())).thenReturn(
                new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        lenient().when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        lenient().when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId).content("hello").conversationId("conv-1").build();
        chatService.chat(request);

        ArgumentCaptor<LogData> captor = ArgumentCaptor.forClass(LogData.class);
        verify(agentLog, atLeast(1)).addLog(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("会话变量 _sys_loading_SKILLS 非空时，chat() 应记录 SkillLoadLogData（已加载技能日志保留）")
    void 已加载技能会话变量非空时记录SkillLoadLogData() {
        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"skill-a\",\"skill-b\"]");

        List<LogData> logs = executeChatWithSessionVars(sessionVars);

        SkillLoadLogData skillLoad = logs.stream()
                .filter(l -> l.logType() == LogType.SKILL_LOAD)
                .map(l -> (SkillLoadLogData) l)
                .findFirst().orElse(null);
        assertNotNull(skillLoad, "应记录 SkillLoadLogData");
        assertEquals(List.of("skill-a", "skill-b"), skillLoad.getSkillNames(), "skillNames 应来自会话变量解析结果");
        assertEquals(2, skillLoad.getSkillCount(), "skillCount 应为技能名数量");
        assertEquals(LogLevel.INFO, skillLoad.getLogLevel(), "日志级别应为 INFO");
    }

    @Test
    @DisplayName("会话变量 _sys_loading_SKILLS 缺失/空列表时，不记录 SkillLoadLogData")
    void 已加载技能会话变量缺失或空列表时不记录SkillLoadLogData() {
        List<LogData> missingLogs = executeChatWithSessionVars(new HashMap<>());
        assertTrue(missingLogs.stream().noneMatch(l -> l.logType() == LogType.SKILL_LOAD),
                "会话变量缺失时不应记录 SkillLoadLogData");

        Map<String, String> emptyVars = new HashMap<>();
        emptyVars.put(LoadSkillsSystemTool.SESSION_KEY, "[]");
        List<LogData> emptyLogs = executeChatWithSessionVars(emptyVars);
        assertTrue(emptyLogs.stream().noneMatch(l -> l.logType() == LogType.SKILL_LOAD),
                "会话变量为空列表时不应记录 SkillLoadLogData");
    }

    @Test
    @DisplayName("会话变量 _sys_loading_SKILLS 为空白或非法 JSON 时，不记录 SkillLoadLogData 且不中断主流程")
    void 已加载技能会话变量空白或解析失败时不记录SkillLoadLogData() {
        Map<String, String> blankVars = new HashMap<>();
        blankVars.put(LoadSkillsSystemTool.SESSION_KEY, "   ");
        List<LogData> blankLogs = executeChatWithSessionVars(blankVars);
        assertTrue(blankLogs.stream().noneMatch(l -> l.logType() == LogType.SKILL_LOAD),
                "会话变量为空白时不应记录 SkillLoadLogData");

        Map<String, String> invalidVars = new HashMap<>();
        invalidVars.put(LoadSkillsSystemTool.SESSION_KEY, "not-a-json");
        List<LogData> invalidLogs = executeChatWithSessionVars(invalidVars);
        assertTrue(invalidLogs.stream().noneMatch(l -> l.logType() == LogType.SKILL_LOAD),
                "会话变量解析失败时不应记录 SkillLoadLogData");
    }
}
