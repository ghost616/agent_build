package com.ghost616.agentbase.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceHistoryGroupJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private ChatService chatService;

    private final String sessionId = "1";

    @BeforeEach
    void setUp() {
        AgentComponentRegistry registry = new AgentComponentRegistry();
        registry.setAgentContextManager(agentContextManager);
        registry.setSessionManager(sessionManager);
        registry.setModelInvokerManager(modelInvokerManager);
        registry.setSystemToolManager(systemToolManager);
        registry.setToolManager(toolManager);
        registry.setChatDataProvider(chatDataProvider);
        registry.setHookManager(hookManager);
        chatService = new ChatService(registry);
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
        when(context.getSkills()).thenReturn(null);
        when(context.getTools()).thenReturn(java.util.Collections.emptyList());
        when(context.isMainSession()).thenReturn(false);
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

    private String findAnchorContent(com.ghost616.agentbase.dto.model.ChatRequest captured) {
        return captured.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(Message::getContent)
                .filter(c -> c != null && c.startsWith("【历史消息组"))
                .findFirst().orElse(null);
    }

    @Test
    void buildHistoryGroupMessage_每条消息为一行独立JSON() throws Exception {
        List<AgentExecutionContext.HistoryEntry> history = buildFoldHistory(13);
        com.ghost616.agentbase.dto.model.ChatRequest captured = executeFoldChat(history, 3, "[2]");

        String anchor = findAnchorContent(captured);
        assertNotNull(anchor, "应生成锚点展开消息");

        String[] lines = anchor.split("\n");
        assertTrue(lines.length >= 3, "首行为提示行，其余行应为 JSON，实际行数=" + lines.length);
        assertTrue(lines[0].startsWith("【历史消息组2】"), "首行应保留 HISTORY_GROUP_PREFIX 提示");

        JsonNode userNode = MAPPER.readTree(lines[1]);
        assertEquals("user", userNode.get("role").asText(), "首条 JSON 行应为 user 消息");
        assertEquals("q2", userNode.get("content").asText(), "user 消息 content 应为 q2");

        JsonNode assistantNode = MAPPER.readTree(lines[2]);
        assertEquals("assistant", assistantNode.get("role").asText(), "第二条 JSON 行应为 assistant 消息");
        assertEquals("a2", assistantNode.get("content").asText(), "assistant 消息 content 应为 a2");
    }

    @Test
    void buildHistoryGroupMessage_推理与工具调用输出reasoning与tool_calls() throws Exception {
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

        String anchor = findAnchorContent(captured);
        assertNotNull(anchor);

        String[] lines = anchor.split("\n");
        JsonNode assistantNode = null;
        JsonNode toolNode = null;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            JsonNode node = MAPPER.readTree(lines[i]);
            if ("assistant".equals(node.get("role").asText())
                    && node.has("tool_calls")) {
                assistantNode = node;
            }
            if ("tool".equals(node.get("role").asText())) {
                toolNode = node;
            }
        }

        assertNotNull(assistantNode, "应存在含 tool_calls 的 assistant JSON 行");
        assertEquals("reasoning_text", assistantNode.get("reasoning").asText(),
                "assistant 含推理时应输出 reasoning 字段");
        assertTrue(assistantNode.get("tool_calls").isArray(), "tool_calls 应为数组");
        JsonNode call = assistantNode.get("tool_calls").get(0);
        assertEquals("get_weather", call.get("name").asText(), "tool_calls 应含工具名");
        assertEquals("tc1", call.get("id").asText(), "tool_calls 应含工具调用 id，供关联工具结果");

        assertNotNull(toolNode, "应存在 tool 角色的 JSON 行");
        assertTrue(toolNode.has("tool_info"), "tool 消息应输出 tool_info 字段");
        assertEquals("get_weather", toolNode.get("tool_info").get("name").asText(),
                "tool_info 应含 name");
        assertEquals("tc1", toolNode.get("tool_info").get("id").asText(),
                "tool_info 应含 id");
    }
}
