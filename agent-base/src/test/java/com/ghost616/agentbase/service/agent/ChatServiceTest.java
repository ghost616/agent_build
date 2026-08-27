package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.invoker.ChatChunkHookData;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.HookResult;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookData;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookResult;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.ToolDefinitionsHookData;
import com.ghost616.agentbase.service.agent.invoker.ToolDefinitionsHookResult;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private AgentContextManager agentContextManager;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ModelInvokerManager modelInvokerManager;
    @Mock
    private SystemToolManager systemToolManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private ChatDataProvider chatDataProvider;
    @Mock
    private HookManager hookManager;
    @Mock
    private ModelInvoker modelInvoker;
    @Mock
    private AgentContextManager.Builder builder;
    @Mock
    private AgentExecutionContext context;
    @Mock
    private AgentExecutionContext.AgentContextMutator mutator;

    private AgentComponentRegistry registry;
    private ChatService chatService;

    private final String sessionId = "1";

    @BeforeEach
    void setUp() {
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

    @Test
    void constructor_shouldInjectAllDependencies() {
        assertNotNull(chatService);
    }

    @Test
    void chat_SESSION_START阶段triggerSessionHooks优先于triggerHooks调用() {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        chatService.chat(request);

        verify(hookManager).triggerSessionHooks(sessionId, HookPhase.SESSION_START, context, new ChatChunkHookData((com.ghost616.agentbase.dto.model.ChatChunk) null));
        verify(hookManager).triggerHooks(HookPhase.SESSION_START, context, new ChatChunkHookData((com.ghost616.agentbase.dto.model.ChatChunk) null));
    }

    @Test
    void chat_SEND_USER_MESSAGE_MARKER不保存用户消息不加入历史且直接触发模型执行() {
        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .content(ChatService.SEND_USER_MESSAGE_MARKER)
                .build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        chatService.chat(request);

        verify(sessionManager, never()).messageSave();
        verify(mutator, never()).addHistoryEntry(any());
        verify(mutator, never()).setConversationId(any());
        verify(modelInvoker).invokeStream(any());
    }

    @Test
    void chat_BEFORE_MESSAGE_SEND阶段triggerSessionHooks在doOnNext中调用() {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        com.ghost616.agentbase.dto.model.ChatChunk chunk = com.ghost616.agentbase.dto.model.ChatChunk.builder().delta("hi").build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk));

        chatService.chat(request).subscribe();

        verify(hookManager).triggerSessionHooks(sessionId, HookPhase.BEFORE_MESSAGE_SEND, context, new ChatChunkHookData(chunk));
        verify(hookManager).triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, context, new ChatChunkHookData(chunk));
    }

    @Test
    void chat_AFTER_MESSAGE_RECEIVE阶段triggerSessionHooks在doOnComplete中调用() {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        com.ghost616.agentbase.dto.model.ChatChunk chunk = com.ghost616.agentbase.dto.model.ChatChunk.builder().delta("hi").finishReason(FinishReason.STOP).build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk));

        chatService.chat(request).subscribe();

        com.ghost616.agentbase.dto.model.ChatChunk completeChunk = com.ghost616.agentbase.dto.model.ChatChunk.builder().hasToolCalls(false).build();
        verify(hookManager).triggerSessionHooks(sessionId, HookPhase.AFTER_MESSAGE_RECEIVE, context, new ChatChunkHookData(completeChunk));
        verify(hookManager).triggerHooks(HookPhase.AFTER_MESSAGE_RECEIVE, context, new ChatChunkHookData(completeChunk));
    }

    private com.ghost616.agentbase.dto.model.ChatRequest executeFoldChat(
            List<AgentExecutionContext.HistoryEntry> history, Integer recentCount, String expandedIndicesJson) {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("base_prompt");
        when(context.getHistory()).thenReturn(history);
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(context.getRecentMessageCount()).thenReturn(recentCount);
        lenient().when(context.getConversationVariable(any())).thenReturn(expandedIndicesJson);
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        chatService.chat(request);

        ArgumentCaptor<com.ghost616.agentbase.dto.model.ChatRequest> captor =
                ArgumentCaptor.forClass(com.ghost616.agentbase.dto.model.ChatRequest.class);
        verify(modelInvoker).invokeStream(captor.capture());
        return captor.getValue();
    }

    private List<AgentExecutionContext.HistoryEntry> buildFoldHistory(int groupCount) {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        for (int g = 0; g < groupCount - 1; g++) {
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "q" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, true));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "a" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, null));
        }
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, true));
        return history;
    }

    @Test
    void fold_groupsWithinRecentCount_shouldNotFold() {
        List<AgentExecutionContext.HistoryEntry> history = buildFoldHistory(3);
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("此为历史消息索引为")),
                "组数不超过 recentCount 时不应折叠");
        assertTrue(contents.contains("q0") && contents.contains("a1"),
                "应保留全部历史消息内容");
    }

    @Test
    void fold_excessLessThanInterval_shouldNotFold() {
        List<AgentExecutionContext.HistoryEntry> history = buildFoldHistory(12);
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("此为历史消息索引为")),
                "超出 recentCount 但不足一个 interval 时不应折叠");
        assertTrue(contents.contains("a10"), "应保留全部历史消息内容");
    }

    @Test
    void fold_excessEqualToInterval_shouldFoldBatch() {
        List<AgentExecutionContext.HistoryEntry> history = buildFoldHistory(13);
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        long placeholderCount = contents.stream()
                .filter(c -> c != null && c.contains("此为历史消息索引为"))
                .count();
        assertEquals(10, placeholderCount, "超出恰好一个 interval 时应批量折叠 10 组");
        assertTrue(contents.contains("a10"), "近端区应完整保留");
        assertFalse(contents.contains("a9"), "折叠区应隐藏 assistant 内容");
    }

    @Test
    void fold_excessMultipleOfInterval_shouldFoldBatch() {
        List<AgentExecutionContext.HistoryEntry> history = buildFoldHistory(30);
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 5, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        long placeholderCount = contents.stream()
                .filter(c -> c != null && c.contains("此为历史消息索引为"))
                .count();
        assertEquals(20, placeholderCount, "超出多个 interval 时应批量折叠 20 组");
        assertTrue(contents.contains("a25"), "近端区应完整保留");
        assertFalse(contents.contains("a15"), "折叠区应隐藏 assistant 内容");
    }

    @Test
    void fold_anchorExpansionInsertedBeforeLastUser() {
        List<AgentExecutionContext.HistoryEntry> history = buildFoldHistory(13);
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, "[2]");

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        String anchor = contents.stream()
                .filter(c -> c != null && c.startsWith("【历史消息组2】"))
                .findFirst().orElse(null);
        assertNotNull(anchor, "应生成组2的锚点展开消息");
        assertTrue(anchor.contains("\"role\":\"user\"") && anchor.contains("\"content\":\"q2\""),
                "锚点应包含 user 内容");
        assertTrue(anchor.contains("\"role\":\"assistant\"") && anchor.contains("\"content\":\"a2\""),
                "锚点应包含 assistant 内容");

        int anchorIdx = -1;
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) != null && contents.get(i).startsWith("【历史消息组2】")) {
                anchorIdx = i;
                break;
            }
        }
        int lastUserIdx = contents.lastIndexOf("hello");
        assertTrue(anchorIdx >= 0 && anchorIdx < lastUserIdx,
                "锚点展开消息应插入在最后一条 user 消息之前");
    }

    @Test
    void fold_anchorExpansion包含工具调用推理与结果() {
        ToolCall toolCall = ToolCall.builder()
                .id("tc1")
                .name("get_weather")
                .arguments("{\"city\":\"sh\"}")
                .build();
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q0", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a0", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q2", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "调用工具", "reasoning_text", null, java.time.LocalDateTime.now(),
                List.of(toolCall), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "tool", "{\"temp\":25}", null, new ToolInfo("tc1", "get_weather"), java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, null));
        for (int g = 3; g < 13; g++) {
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "q" + g, null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "a" + g, null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        }
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));

        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, "[2]");

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        String anchor = contents.stream()
                .filter(c -> c != null && c.startsWith("【历史消息组2】"))
                .findFirst().orElse(null);
        assertNotNull(anchor, "应生成组2锚点展开消息");
        assertTrue(anchor.contains("\"reasoning\":\"reasoning_text\""), "锚点应包含 assistant 推理内容");
        assertTrue(anchor.contains("\"tool_calls\"") && anchor.contains("\"name\":\"get_weather\"")
                && anchor.contains("city"), "锚点应包含工具名与参数");
        assertTrue(anchor.contains("\"tool_info\"") && anchor.contains("\"id\":\"tc1\"")
                && anchor.contains("temp"), "锚点应包含工具调用信息与结果");
    }

    @Test
    void chatCompletions_前置提示词插入主systemPrompt之后且后置提示词在最后user之前() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("PRE_PROMPT");
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("POST_PROMPT");

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertEquals("base_prompt", contents.get(0), "主 systemPrompt 应位于首位");
        assertEquals("PRE_PROMPT", contents.get(1), "前置提示词应作为 system 消息插入到主 systemPrompt 之后(index 1)");
        assertEquals("system", captured.getMessages().get(1).getRole(), "前置提示词消息角色应为 system");
        int postIdx = contents.indexOf("POST_PROMPT");
        int lastUserIdx = contents.lastIndexOf("hello");
        assertTrue(postIdx >= 0, "应包含后置提示词");
        assertTrue(postIdx < lastUserIdx, "后置提示词应插入到最后一条 user 消息之前");
    }

    @Test
    void chatCompletions_无user消息时后置提示词追加到末尾() {
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("POST_PROMPT");

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertEquals("POST_PROMPT", contents.get(contents.size() - 1),
                "无 user 消息时后置提示词应追加到消息列表末尾");
    }

    @Test
    void chatCompletions_前置后置提示词为空白时跳过注入() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("   ");
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("");

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.contains("   "), "空白前置提示词不应注入");
        assertFalse(contents.contains(""), "空后置提示词不应注入");
        assertEquals("base_prompt", contents.get(0), "主 systemPrompt 仍应位于首位");
    }

    @Test
    void chatCompletions_前置后置提示词为null时跳过注入() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn(null);
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn(null);

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertEquals("base_prompt", contents.get(0), "主 systemPrompt 应位于首位");
        assertEquals(1, contents.indexOf("hello"), "未注入前置提示词时历史消息从 index 1 开始");
        assertEquals(contents.size() - 1, contents.lastIndexOf("hello"),
                "未注入后置提示词时最后一条 user 消息仍位于末尾");
    }

    @Test
    void chat_配置ThreadVariableHandler时doOnNext与doOnComplete回调中apply恢复且finally清理() {
        ThreadVariableHandler handler = mock(ThreadVariableHandler.class);
        ThreadVariableWrapper wrapper = mock(ThreadVariableWrapper.class);
        when(handler.wrap()).thenReturn(wrapper);
        registry.setThreadVariableHandler(handler);

        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        com.ghost616.agentbase.dto.model.ChatChunk chunk = com.ghost616.agentbase.dto.model.ChatChunk.builder().delta("hi").finishReason(FinishReason.STOP).build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk));

        chatService.chat(request).subscribe();

        verify(handler).wrap();
        verify(wrapper, times(2)).apply();
        verify(wrapper, times(2)).clear();
    }

    @Test
    void chat_未配置ThreadVariableHandler时不抛异常且HOOK正常触发() {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        com.ghost616.agentbase.dto.model.ChatChunk chunk = com.ghost616.agentbase.dto.model.ChatChunk.builder().delta("hi").finishReason(FinishReason.STOP).build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk));

        assertDoesNotThrow(() -> chatService.chat(request).subscribe());

        verify(hookManager).triggerSessionHooks(sessionId, HookPhase.BEFORE_MESSAGE_SEND, context, new ChatChunkHookData(chunk));
        verify(hookManager).triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, context, new ChatChunkHookData(chunk));
    }

    @Test
    void chat_请求级images附加到消息保存与HistoryEntry() {
        List<ImageContent> images = List.of(
                ImageContent.builder().imgId("img-1").imgText("data:image/png;base64,AAA").build());
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("看图").conversationId("conv-1")
                .images(images).build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
        when(sessionManager.messageSave()).thenReturn(msgBuilder);
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        chatService.chat(request);

        verify(msgBuilder).images(images);
        ArgumentCaptor<AgentExecutionContext.HistoryEntry> entryCaptor =
                ArgumentCaptor.forClass(AgentExecutionContext.HistoryEntry.class);
        verify(mutator).addHistoryEntry(entryCaptor.capture());
        assertEquals(images, entryCaptor.getValue().images());
    }

    @Test
    void chat_请求无images时保持现有行为不调用images() {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
        when(sessionManager.messageSave()).thenReturn(msgBuilder);
        when(context.getSystemPrompt()).thenReturn("");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        chatService.chat(request);

        ArgumentCaptor<AgentExecutionContext.HistoryEntry> entryCaptor =
                ArgumentCaptor.forClass(AgentExecutionContext.HistoryEntry.class);
        verify(mutator).addHistoryEntry(entryCaptor.capture());
        assertNull(entryCaptor.getValue().images());
    }

    @Test
    void chat_buildMessageFromEntry透传images至模型消息() {
        List<ImageContent> images = List.of(
                ImageContent.builder().imgId("img-1").imgText("data:image/png;base64,AAA").build());
        AgentExecutionContext.HistoryEntry userEntry = new AgentExecutionContext.HistoryEntry(
                "user", "看图", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, images, true);
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("看图").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("base_prompt");
        when(context.getHistory()).thenReturn(List.of(userEntry));
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());

        chatService.chat(request);

        ArgumentCaptor<com.ghost616.agentbase.dto.model.ChatRequest> captor =
                ArgumentCaptor.forClass(com.ghost616.agentbase.dto.model.ChatRequest.class);
        verify(modelInvoker).invokeStream(captor.capture());
        Message userMsg = captor.getValue().getMessages().stream()
                .filter(m -> "user".equals(m.getRole()))
                .findFirst().orElse(null);
        assertNotNull(userMsg, "模型请求应包含 user 消息");
        assertEquals(images, userMsg.getImages());
    }

    @Test
    void fold_折叠区首条user消息仅保留content忽略图片() {
        List<ImageContent> images = List.of(
                ImageContent.builder().imgId("img-1").imgText("data:image/png;base64,AAA").build());
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        for (int g = 0; g < 12; g++) {
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "q" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, images, true));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "a" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, null));
        }
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, true));

        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<Message> messages = captured.getMessages();
        for (Message m : messages) {
            if ("user".equals(m.getRole()) && m.getContent() != null && m.getContent().startsWith("q")
                    && Integer.parseInt(m.getContent().substring(1)) < 10) {
                assertNull(m.getImages(), "折叠区 user 消息应忽略图片，仅保留 content 文本");
            }
        }
        Message q10 = messages.stream()
                .filter(m -> "user".equals(m.getRole()) && "q10".equals(m.getContent()))
                .findFirst().orElse(null);
        assertNotNull(q10, "近端区应保留 q10 完整消息");
        assertEquals(images, q10.getImages(), "近端区 user 消息应保留图片");
    }

    @Test
    void fold_userInput为false的user消息不产生新组归入相邻组() {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        for (int g = 0; g < 12; g++) {
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "u" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, true));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "a" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, null));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "child-" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, false));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "ca" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, null));
        }
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, true));

        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        long placeholderCount = contents.stream()
                .filter(c -> c != null && c.contains("此为历史消息索引为"))
                .count();
        // 12 组真实用户输入（u0..u11）+ hello = 13 组，recentCount=3 → foldedCount=10
        assertEquals(10, placeholderCount, "user_input=false 的 user 消息不应产生新组");
        // 折叠区（组 0..9）中 user_input=false 的 child-* 消息应随组折叠，不单独出现
        // （近端区组 10/11 的 child-10/child-11 属预期保留，不在此检查范围）
        java.util.Set<String> foldedChildren = java.util.stream.IntStream.range(0, 10)
                .mapToObj(g -> "child-" + g)
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(contents.stream().filter(java.util.Objects::nonNull).anyMatch(foldedChildren::contains),
                "折叠区 user_input=false 消息应归入相邻组并被折叠");
        // 近端区（组 10..12）完整保留，含 user_input=false 消息
        assertTrue(contents.contains("u10"), "近端区应保留真实用户输入消息");
        assertTrue(contents.contains("child-10"), "近端区应保留 user_input=false 的 user 消息");
        assertTrue(contents.contains("hello"), "近端区应保留最后一条用户输入");
    }

    /**
     * 以指定 sessionCtx 执行一次 chat 请求（复用同一会话上下文，用于验证 preSystemPrompt 缓存）。
     */
    private void executeChatWithSessionCtx(AgentContextManager.AgentSessionContext sessionCtx) {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("base_prompt");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());
        chatService.chat(request);
    }

    @Test
    void chatCompletions_preSystemPrompt首次获取后缓存_二次请求不再调用provider() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("PRE_PROMPT");

        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        executeChatWithSessionCtx(sessionCtx);
        assertEquals("PRE_PROMPT", sessionCtx.preSystemPrompt(), "首次请求后应缓存前置提示词到会话上下文");
        executeChatWithSessionCtx(sessionCtx);

        verify(chatDataProvider, times(1)).getPreSystemPrompt(sessionId);
    }

    @Test
    void chatCompletions_provider返回null时不缓存_每次请求均调用provider() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn(null);

        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        executeChatWithSessionCtx(sessionCtx);
        assertNull(sessionCtx.preSystemPrompt(), "provider 返回 null 时缓存值应为 null");
        executeChatWithSessionCtx(sessionCtx);

        verify(chatDataProvider, times(2)).getPreSystemPrompt(sessionId);
    }

    @Test
    void chatCompletions_AFTER_PRE_SYSTEM_PROMPT_BUILD触发且结果注入到preSystemPrompt之后history之前() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("PRE_PROMPT");
        lenient().when(hookManager.triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(context), any(SystemPromptHookData.class)))
                .thenReturn(List.of(new SystemPromptHookResult("HOOK_PROMPT")));
        when(hookManager.castHookResult(any(HookResult.class), eq(SystemPromptHookResult.class)))
                .thenAnswer(inv -> {
                    HookResult result = inv.getArgument(0);
                    return result instanceof SystemPromptHookResult ? (SystemPromptHookResult) result : null;
                });

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        verify(hookManager).triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(context), any(SystemPromptHookData.class));
        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertEquals("PRE_PROMPT", contents.get(1), "HOOK 提示词应位于 preSystemPrompt 之后");
        int hookIdx = contents.indexOf("HOOK_PROMPT");
        int preIdx = contents.indexOf("PRE_PROMPT");
        int userIdx = contents.indexOf("hello");
        assertTrue(hookIdx > preIdx && hookIdx < userIdx,
                "HOOK 提示词应注入在 preSystemPrompt 消息之后、history 之前");
        assertEquals("system", captured.getMessages().get(hookIdx).getRole(), "HOOK 提示词消息角色应为 system");
    }

    @Test
    void chatCompletions_HOOK返回null或空白提示词时跳过注入() {
        lenient().when(hookManager.triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(context), any(SystemPromptHookData.class)))
                .thenReturn(List.of(new SystemPromptHookResult(null), new SystemPromptHookResult("   ")));
        when(hookManager.castHookResult(any(HookResult.class), eq(SystemPromptHookResult.class)))
                .thenAnswer(inv -> {
                    HookResult result = inv.getArgument(0);
                    return result instanceof SystemPromptHookResult ? (SystemPromptHookResult) result : null;
                });

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, null);

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.contains("   "), "空白 HOOK 提示词不应注入");
        assertEquals("hello", contents.get(contents.size() - 1), "最后一条消息仍为 user 消息");
    }

    @Test
    void chatCompletions_HOOK触发异常不中断主流程() {
        lenient().when(hookManager.triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(context), any(SystemPromptHookData.class)))
                .thenThrow(new RuntimeException("hook failure"));

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, true));
        assertDoesNotThrow(() -> executeFoldChat(history, 3, null));
        verify(modelInvoker).invokeStream(any());
    }

    /**
     * 以指定工具配置/系统工具列表执行一次 chat 请求（chatViaChatCompletions），
     * 用于验证 buildToolDefinitions 的工具来源逻辑。
     */
    private com.ghost616.agentbase.dto.model.ChatRequest executeChatWithTools(
            List<ToolConfigDTO> tools, List<ToolDefinition> systemTools) {
        ChatRequest request = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        AgentContextManager.AgentSessionContext sessionCtx =
                new AgentContextManager.AgentSessionContext(context, mutator, new java.util.concurrent.atomic.AtomicBoolean(false));

        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(sessionCtx);
        when(sessionManager.messageSave()).thenReturn(mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF));
        when(context.getSystemPrompt()).thenReturn("base_prompt");
        when(context.getHistory()).thenReturn(java.util.Collections.emptyList());
        when(context.getTools()).thenReturn(tools);
        when(context.getRecentMessageCount()).thenReturn(3);
        lenient().when(context.getConversationVariable(any())).thenReturn(null);
        when(systemToolManager.getToolDefinitions()).thenReturn(systemTools);
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());
        lenient().when(modelInvoker.toToolDefinition(any())).thenAnswer(inv -> {
            ToolConfigDTO t = inv.getArgument(0);
            return ToolDefinition.builder().name(t.getName()).build();
        });

        chatService.chat(request);

        ArgumentCaptor<com.ghost616.agentbase.dto.model.ChatRequest> captor =
                ArgumentCaptor.forClass(com.ghost616.agentbase.dto.model.ChatRequest.class);
        verify(modelInvoker).invokeStream(captor.capture());
        return captor.getValue();
    }

    private List<String> toolNamesOf(com.ghost616.agentbase.dto.model.ChatRequest captured) {
        return captured.getTools() != null
                ? captured.getTools().stream().map(ToolDefinition::getName).toList() : List.of();
    }

    @Test
    void buildToolDefinitions_无HOOK时回退contextTools并与系统工具合并() {
        List<ToolConfigDTO> tools = List.of(ToolConfigDTO.builder().name("ctx_tool").build());
        List<ToolDefinition> systemTools = List.of(ToolDefinition.builder().name("sys_tool").build());

        com.ghost616.agentbase.dto.model.ChatRequest captured = executeChatWithTools(tools, systemTools);

        List<String> toolNames = toolNamesOf(captured);
        assertTrue(toolNames.contains("ctx_tool"), "无 HOOK 时应回退使用 context.getTools()");
        assertTrue(toolNames.contains("sys_tool"), "系统工具应始终附加");
        verify(hookManager, never()).triggerHooks(eq(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD), eq(context), any(ToolDefinitionsHookData.class));
    }

    @Test
    void buildToolDefinitions_有HOOK且返回非空结果时使用HOOK工具与系统工具() {
        when(hookManager.hasHooks(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD)).thenReturn(true);
        lenient().when(hookManager.triggerHooks(eq(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD), eq(context), any(ToolDefinitionsHookData.class)))
                .thenReturn(List.of(new ToolDefinitionsHookResult(
                        List.of(ToolConfigDTO.builder().name("hook_tool").build()))));
        when(hookManager.castHookResult(any(HookResult.class), eq(ToolDefinitionsHookResult.class)))
                .thenAnswer(inv -> {
                    HookResult result = inv.getArgument(0);
                    return result instanceof ToolDefinitionsHookResult ? (ToolDefinitionsHookResult) result : null;
                });

        // context 工具（ctx_tool）应被 HOOK 结果替换，不出现
        List<ToolConfigDTO> tools = List.of(ToolConfigDTO.builder().name("ctx_tool").build());
        List<ToolDefinition> systemTools = List.of(ToolDefinition.builder().name("sys_tool").build());
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeChatWithTools(tools, systemTools);

        List<String> toolNames = toolNamesOf(captured);
        assertTrue(toolNames.contains("hook_tool"), "有 HOOK 时应使用 HOOK 提供的工具");
        assertTrue(toolNames.contains("sys_tool"), "系统工具应始终附加");
        assertFalse(toolNames.contains("ctx_tool"), "有 HOOK 时 context.getTools() 不应直接进入工具列表");
        verify(hookManager).triggerHooks(eq(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD), eq(context), any(ToolDefinitionsHookData.class));
    }

    @Test
    void buildToolDefinitions_有HOOK但结果为空时仅系统工具() {
        when(hookManager.hasHooks(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD)).thenReturn(true);
        lenient().when(hookManager.triggerHooks(eq(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD), eq(context), any(ToolDefinitionsHookData.class)))
                .thenReturn(List.of());

        List<ToolConfigDTO> tools = List.of(ToolConfigDTO.builder().name("ctx_tool").build());
        List<ToolDefinition> systemTools = List.of(ToolDefinition.builder().name("sys_tool").build());
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeChatWithTools(tools, systemTools);

        assertEquals(List.of("sys_tool"), toolNamesOf(captured),
                "HOOK 合并结果为空时工具列表应仅含系统工具");
    }
}
