package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.enums.RequestType;
import com.ghost616.agentbase.service.agent.invoker.ChatChunkHookData;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.HookResult;
import com.ghost616.agentbase.service.agent.invoker.HistoryQuerySystemTool;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookData;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookResult;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceResponsesTest {

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

    private static class TestHarness {
        final AgentExecutionContext context;
        final AgentExecutionContext.AgentContextMutator mutator;

        TestHarness(String systemPrompt, List<ToolConfigDTO> tools,
                    List<SkillConfigDTO> skills, Map<String, String> sessionVariables,
                    List<AgentExecutionContext.HistoryEntry> history) {
            this.mutator = new AgentExecutionContext.AgentContextMutator();
            this.context = new AgentExecutionContext(
                    "1", "1", systemPrompt, "1", null,
                    history != null ? new ArrayList<>(history) : new ArrayList<>(),
                    tools != null ? new ArrayList<>(tools) : new ArrayList<>(),
                    skills, mutator,
                    sessionVariables != null ? sessionVariables : new HashMap<>(),
                    new HashMap<>(), null, "", null, null);
        }

        TestHarness(String systemPrompt, List<ToolConfigDTO> tools,
                    List<SkillConfigDTO> skills, Map<String, String> sessionVariables,
                    Map<String, String> conversationVariables, Integer recentMessageCount,
                    List<AgentExecutionContext.HistoryEntry> history) {
            this.mutator = new AgentExecutionContext.AgentContextMutator();
            this.context = new AgentExecutionContext(
                    "1", "1", systemPrompt, "1", recentMessageCount,
                    history != null ? new ArrayList<>(history) : new ArrayList<>(),
                    tools != null ? new ArrayList<>(tools) : new ArrayList<>(),
                    skills, mutator,
                    sessionVariables != null ? sessionVariables : new HashMap<>(),
                    conversationVariables != null ? conversationVariables : new HashMap<>(),
                    null, "", null, null);
        }
    }

    private com.ghost616.agentbase.dto.model.ChatRequest executeChat(
            ChatRequest apiRequest, TestHarness harness, String requestType, Flux<ChatChunk> stream) {
        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(
                new AgentContextManager.AgentSessionContext(
                        harness.context, harness.mutator, new AtomicBoolean(false)));

        lenient().when(chatDataProvider.getModelConfig(any())).thenReturn(
                new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", requestType));
        lenient().when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        lenient().when(modelInvoker.invokeStream(any())).thenReturn(stream);
        lenient().when(modelInvoker.toToolDefinition(any())).thenReturn(
                ToolDefinition.builder().name("ctx_tool").build());

        chatService.chat(apiRequest).subscribe();
        ArgumentCaptor<com.ghost616.agentbase.dto.model.ChatRequest> captor =
                ArgumentCaptor.forClass(com.ghost616.agentbase.dto.model.ChatRequest.class);
        verify(modelInvoker).invokeStream(captor.capture());
        return captor.getValue();
    }

    private List<Message> getSystemMessages(com.ghost616.agentbase.dto.model.ChatRequest captured) {
        return captured.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .toList();
    }

    @Test
    @DisplayName("requestType=responses 时，input 不应包含 system 角色消息，仅含 user/assistant")
    void responses_input应排除system角色消息() {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "system", "历史遗留系统消息", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "你好", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, null));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertTrue(getSystemMessages(captured).isEmpty(), "input 中不应包含 system 角色消息");
        assertTrue(captured.getMessages().stream().anyMatch(m -> "user".equals(m.getRole())),
                "input 应包含 user 消息");
    }

    @Test
    @DisplayName("requestType=responses 时，instructions 包含 systemPrompt 且不再包含已加载技能提示词（已迁移至 AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK）")
    void responses_instructions应包含systemPrompt与已加载技能提示词() {
        SkillConfigDTO loadedSkill = SkillConfigDTO.builder()
                .name("loaded_skill")
                .sessionAuth(null)
                .prompt("SKILL_PROMPT_TEXT")
                .build();

        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"loaded_skill\"]");

        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(),
                List.of(loadedSkill), sessionVars, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertNotNull(captured.getInstructions());
        assertTrue(captured.getInstructions().contains("SYSTEM_PROMPT_TEXT"),
                "instructions 应包含 systemPrompt");
        assertFalse(captured.getInstructions().contains("SKILL_PROMPT_TEXT"),
                "移除已加载技能提示词生成后，instructions 不应包含已加载技能提示词");
    }

    @Test
    @DisplayName("requestType=responses 时，instructions 不再包含可用技能列表（已迁移至 AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK）")
    void responses_instructions应包含可用技能列表() {
        SkillConfigDTO availableSkill = SkillConfigDTO.builder()
                .name("available_skill")
                .sessionAuth(null)
                .description("desc")
                .build();

        TestHarness harness = new TestHarness("sys_prompt", List.of(),
                List.of(availableSkill), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertFalse(captured.getInstructions().contains("可用的技能"),
                "移除可用技能列表生成后，instructions 不应包含技能列表提示");
    }

    @Test
    @DisplayName("requestType=responses 时，previousResponseId 应从 API 请求透传到模型请求")
    void responses_previousResponseId透传() {
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content("hello")
                .conversationId("conv-1")
                .previousResponseId("resp_123")
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertEquals("resp_123", captured.getPreviousResponseId());
    }

    @Test
    @DisplayName("requestType=responses 时，工具列表合并上下文工具与系统工具")
    void responses_工具列表合并上下文与系统工具() {
        ToolConfigDTO ctxTool = ToolConfigDTO.builder().name("ctx_tool").build();
        TestHarness harness = new TestHarness("sys_prompt", List.of(ctxTool), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name("sys_tool_a").build(),
                        ToolDefinition.builder().name("sys_tool_b").build()));

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        List<String> toolNames = captured.getTools() != null
                ? captured.getTools().stream().map(ToolDefinition::getName).toList() : List.of();
        assertTrue(toolNames.contains("ctx_tool"));
        assertTrue(toolNames.contains("sys_tool_a"));
        assertTrue(toolNames.contains("sys_tool_b"));
    }

    @Test
    @DisplayName("requestType=responses 时，SSE 流 BEFORE_MESSAGE_SEND 与 AFTER_MESSAGE_RECEIVE 钩子正常触发")
    void responses_SSE钩子正常触发() {
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatChunk chunk = ChatChunk.builder().delta("hi").finishReason(FinishReason.STOP).build();

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.just(chunk));

        verify(hookManager).triggerSessionHooks(sessionId, HookPhase.BEFORE_MESSAGE_SEND,
                harness.context, new ChatChunkHookData(chunk));
        verify(hookManager).triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, harness.context, new ChatChunkHookData(chunk));

        ChatChunk completeChunk = ChatChunk.builder().hasToolCalls(false).build();
        verify(hookManager).triggerSessionHooks(sessionId, HookPhase.AFTER_MESSAGE_RECEIVE,
                harness.context, new ChatChunkHookData(completeChunk));
        verify(hookManager).triggerHooks(HookPhase.AFTER_MESSAGE_RECEIVE, harness.context,
                new ChatChunkHookData(completeChunk));
    }

    @Test
    @DisplayName("requestType=responses 时，有状态分支 input 仅从最后一个 user 条目到末尾")
    void responses_有状态input仅从最后一个user到末尾() {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q2", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a2", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "tool", "r2", null, new ToolInfo("tc1", "testTool"), java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content(ChatService.TOOL_CONTINUE_MARKER)
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        List<String> roles = captured.getMessages().stream().map(Message::getRole).toList();
        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertEquals(List.of("q2", "a2", "r2"), contents,
                "input 应从最后一个 user(q2) 到末尾，排除此前 q1/a1");
        assertFalse(roles.contains("system"));
    }

    @Test
    @DisplayName("requestType=responses 时，会话 lastResponseId 优先于 API 请求 previousResponseId")
    void responses_lastResponseId优先于API请求() {
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        harness.mutator.setLastResponseId("ctx_resp");
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content("hello")
                .conversationId("conv-1")
                .previousResponseId("api_resp")
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertEquals("ctx_resp", captured.getPreviousResponseId(),
                "lastResponseId 非空时应优先使用会话上下文值");
    }

    @Test
    @DisplayName("requestType=responses 时，流式 chunk.responseId 应写入会话 lastResponseId")
    void responses_responseId捕获写入上下文() {
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatChunk chunk = ChatChunk.builder().delta("hi").responseId("r1").build();

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.just(chunk));

        assertEquals("r1", harness.context.getLastResponseId(),
                "流式过程中应捕获 responseId 写入会话上下文");
    }

    @Test
    @DisplayName("requestType=responses_stateless 时，input 为全量历史且 previousResponseId 为空")
    void responsesStateless_全量input无previousResponseId() {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "system", "历史遗留系统消息", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content("hello")
                .conversationId("conv-1")
                .previousResponseId("api_resp")
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES_STATELESS.getCode(), Flux.empty());

        assertNull(captured.getPreviousResponseId(),
                "无状态分支不应传 previousResponseId");
        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertEquals(List.of("q1", "a1", "hello"), contents,
                "无状态分支 input 应包含全量历史（不含 system），且含本轮 user");
        assertTrue(getSystemMessages(captured).isEmpty(), "input 不应包含 system 角色消息");
    }

    @Test
    @DisplayName("requestType=openai 时，走 chat completions 分支：messages 含 system 消息，无 instructions")
    void openai_走chatCompletions含system消息() {
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content("hello")
                .conversationId("conv-1")
                .previousResponseId("resp_123")
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, "openai", Flux.empty());

        assertEquals(1, getSystemMessages(captured).size(), "chat completions 分支应含 system 消息");
        assertNull(captured.getInstructions());
        assertNull(captured.getPreviousResponseId());
    }

    @Test
    @DisplayName("requestType=completions 时，走 chat completions 分支：messages 含 system 消息，无 instructions")
    void completions_走chatCompletions含system消息() {
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content("hello")
                .conversationId("conv-1")
                .previousResponseId("resp_123")
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.COMPLETIONS.getCode(), Flux.empty());

        assertEquals(1, getSystemMessages(captured).size(), "chat completions 分支应含 system 消息");
        assertNull(captured.getInstructions());
        assertNull(captured.getPreviousResponseId());
    }

    @Test
    @DisplayName("requestType=openai 时，已加载技能消息不再由 ChatService 生成（迁移至 AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK）")
    void openai_已加载技能消息插入到最后一个user之前() {
        SkillConfigDTO loadedSkill = SkillConfigDTO.builder()
                .name("loaded_skill")
                .sessionAuth(null)
                .prompt("SKILL_PROMPT")
                .build();

        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"loaded_skill\"]");

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(loadedSkill), sessionVars, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, "openai", Flux.empty());

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("以下技能已加载")),
                "移除已加载技能提示词生成后，messages 不应包含已加载技能消息");
        assertTrue(contents.contains("hello"), "user 消息应保留");
    }

    @Test
    @DisplayName("requestType=openai 时，tool_continue 请求不新增 user 消息且不生成已加载技能消息")
    void openai_无user消息时已加载技能消息追加到末尾() {
        SkillConfigDTO loadedSkill = SkillConfigDTO.builder()
                .name("loaded_skill")
                .sessionAuth(null)
                .prompt("SKILL_PROMPT")
                .build();

        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"loaded_skill\"]");

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "assistant", "a1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));
        history.add(new AgentExecutionContext.HistoryEntry(
                "tool", "r1", null, new ToolInfo("tc1", "testTool"), java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(loadedSkill), sessionVars, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content(ChatService.TOOL_CONTINUE_MARKER)
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, "openai", Flux.empty());

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("hello")),
                "tool_continue 请求不应新增 user 消息");
        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("以下技能已加载")),
                "移除已加载技能提示词生成后，messages 不应包含已加载技能消息");
    }

    @Test
    @DisplayName("requestType=openai 时，已加载技能消息不再由 ChatService 生成（无系统前缀消息）")
    void openai_已加载技能消息不进入系统前缀() {
        SkillConfigDTO loadedSkill = SkillConfigDTO.builder()
                .name("loaded_skill")
                .sessionAuth(null)
                .prompt("SKILL_PROMPT")
                .build();

        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"loaded_skill\"]");

        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "q1", null, null, java.time.LocalDateTime.now(), List.of(), null, null, null, null, null));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(loadedSkill), sessionVars, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, "openai", Flux.empty());

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();
        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("以下技能已加载")),
                "移除已加载技能提示词生成后，系统前缀区与历史区均不应包含已加载技能消息");
        assertTrue(contents.contains("q1") && contents.contains("hello"), "历史与当前 user 消息应保留");
    }

    @Test
    @DisplayName("requestType=responses 时，模型请求 builtinTools 应从 toolManager 加载并设置")
    void responses_builtinTools从toolManager加载() {
        List<Map<String, Object>> builtinTools = List.of(Map.of("type", "web_search", "name", "web_search"));
        when(toolManager.getBuiltinTools("1")).thenReturn(builtinTools);

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertNotNull(captured.getBuiltinTools(), "builtinTools 不应为 null");
        assertEquals(1, captured.getBuiltinTools().size());
        assertEquals("web_search", captured.getBuiltinTools().get(0).get("name"));
        verify(toolManager).getBuiltinTools("1");
    }

    @Test
    @DisplayName("requestType=responses_stateless 时，模型请求 builtinTools 应从 toolManager 加载并设置")
    void responsesStateless_builtinTools从toolManager加载() {
        List<Map<String, Object>> builtinTools = List.of(Map.of("type", "web_search"));
        when(toolManager.getBuiltinTools("1")).thenReturn(builtinTools);

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES_STATELESS.getCode(), Flux.empty());

        assertNotNull(captured.getBuiltinTools(), "builtinTools 不应为 null");
        assertEquals(1, captured.getBuiltinTools().size());
        verify(toolManager).getBuiltinTools("1");
    }

    private List<AgentExecutionContext.HistoryEntry> buildHistoryGroups(int fullGroups) {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        for (int g = 0; g < fullGroups; g++) {
            history.add(new AgentExecutionContext.HistoryEntry(
                    "user", "q" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, true));
            history.add(new AgentExecutionContext.HistoryEntry(
                    "assistant", "a" + g, null, null, java.time.LocalDateTime.now(),
                    List.of(), null, null, null, null, null));
        }
        return history;
    }

    private int indexOfContent(List<String> contents, String keyword) {
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) != null && contents.get(i).contains(keyword)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("requestType=openai 时，批量折叠 + 锚点展开（已加载技能提示词不再由 ChatService 生成）")
    void openai_批量折叠锚点展开与loadedSkills顺序() {
        SkillConfigDTO loadedSkill = SkillConfigDTO.builder()
                .name("loaded_skill")
                .sessionAuth(null)
                .prompt("SKILL_PROMPT")
                .build();

        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"loaded_skill\"]");

        Map<String, String> convVars = new HashMap<>();
        convVars.put(HistoryQuerySystemTool.VAR_NAME, "[2]");

        List<AgentExecutionContext.HistoryEntry> history = buildHistoryGroups(12);
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, true));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(loadedSkill),
                sessionVars, convVars, 3, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content(ChatService.TOOL_CONTINUE_MARKER)
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, "openai", Flux.empty());

        List<String> contents = captured.getMessages().stream().map(Message::getContent).toList();

        long placeholderCount = contents.stream()
                .filter(c -> c != null && c.contains("此为历史消息索引为"))
                .count();
        assertEquals(10, placeholderCount, "应批量折叠 10 组");

        assertFalse(contents.stream().anyMatch(c -> c != null && c.contains("以下技能已加载")),
                "移除已加载技能提示词生成后，messages 不应包含已加载技能消息");

        String anchor = contents.stream()
                .filter(c -> c != null && c.startsWith("【历史消息组2】"))
                .findFirst().orElse(null);
        assertNotNull(anchor, "应包含组2锚点展开消息");
        assertTrue(anchor.contains("\"role\":\"user\"") && anchor.contains("\"content\":\"q2\""),
                "锚点应包含 user 内容");
        assertTrue(anchor.contains("\"role\":\"assistant\"") && anchor.contains("\"content\":\"a2\""),
                "锚点应包含 assistant 内容");

        int anchorIdx = indexOfContent(contents, "【历史消息组2】");
        int lastUserIdx = contents.lastIndexOf("hello");
        assertTrue(anchorIdx < lastUserIdx,
                "锚点展开应位于最后一条 user 消息之前");
    }

    @Test
    @DisplayName("requestType=responses_stateless 时，instructions 应包含锚点展开内容")
    void responsesStateless_instructions应包含锚点展开内容() {
        Map<String, String> convVars = new HashMap<>();
        convVars.put(HistoryQuerySystemTool.VAR_NAME, "[2]");

        List<AgentExecutionContext.HistoryEntry> history = buildHistoryGroups(12);
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, true));

        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(), null, convVars, 3, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content(ChatService.TOOL_CONTINUE_MARKER)
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES_STATELESS.getCode(), Flux.empty());

        assertNotNull(captured.getInstructions());
        assertTrue(captured.getInstructions().contains("【历史消息组2】"),
                "instructions 应包含锚点展开消息");
        assertTrue(captured.getInstructions().contains("\"role\":\"user\"")
                        && captured.getInstructions().contains("\"content\":\"q2\""),
                "instructions 应包含锚点组内容");
    }

    @Test
    @DisplayName("requestType=responses 时，前置提示词拼入 systemPrompt 之后、后置提示词拼入 instructions 末尾")
    void responses_前置提示词在systemPrompt之后后置提示词在末尾() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("PRE_PROMPT");
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("POST_PROMPT");

        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertNotNull(captured.getInstructions());
        String instructions = captured.getInstructions();
        assertTrue(instructions.contains("SYSTEM_PROMPT_TEXT"), "instructions 应包含 systemPrompt");
        assertTrue(instructions.contains("PRE_PROMPT"), "instructions 应包含前置提示词");
        assertTrue(instructions.contains("POST_PROMPT"), "instructions 应包含后置提示词");
        assertTrue(instructions.indexOf("PRE_PROMPT") > instructions.indexOf("SYSTEM_PROMPT_TEXT"),
                "前置提示词应拼接在 systemPrompt 之后");
        assertTrue(instructions.indexOf("POST_PROMPT") > instructions.indexOf("PRE_PROMPT"),
                "后置提示词应拼接在前置提示词之后");
        assertTrue(instructions.endsWith("POST_PROMPT"), "后置提示词应位于 instructions 末尾");
    }

    @Test
    @DisplayName("requestType=responses_stateless 时，后置提示词应拼入锚点内容之后（末尾）且不再包含已加载技能提示词")
    void responsesStateless_后置提示词位于技能与锚点内容之后() {
        SkillConfigDTO loadedSkill = SkillConfigDTO.builder()
                .name("loaded_skill")
                .sessionAuth(null)
                .prompt("SKILL_PROMPT_TEXT")
                .build();
        Map<String, String> sessionVars = new HashMap<>();
        sessionVars.put(LoadSkillsSystemTool.SESSION_KEY, "[\"loaded_skill\"]");
        Map<String, String> convVars = new HashMap<>();
        convVars.put(HistoryQuerySystemTool.VAR_NAME, "[2]");

        List<AgentExecutionContext.HistoryEntry> history = buildHistoryGroups(12);
        history.add(new AgentExecutionContext.HistoryEntry(
                "user", "hello", null, null, java.time.LocalDateTime.now(),
                List.of(), null, null, null, null, true));

        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("POST_PROMPT");
        TestHarness harness = new TestHarness("sys_prompt", List.of(), List.of(loadedSkill), sessionVars, convVars, 3, history);
        when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));

        ChatRequest apiRequest = ChatRequest.builder()
                .sessionId(sessionId)
                .content(ChatService.TOOL_CONTINUE_MARKER)
                .build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES_STATELESS.getCode(), Flux.empty());

        String instructions = captured.getInstructions();
        assertFalse(instructions.contains("SKILL_PROMPT_TEXT"), "移除已加载技能提示词生成后，instructions 不应包含已加载技能提示词");
        assertTrue(instructions.contains("【历史消息组2】"), "instructions 应包含锚点展开内容");
        assertTrue(instructions.indexOf("POST_PROMPT") > instructions.indexOf("【历史消息组2】"),
                "后置提示词应拼接在锚点内容之后");
        assertTrue(instructions.endsWith("POST_PROMPT"), "后置提示词应位于 instructions 末尾");
    }

    @Test
    @DisplayName("requestType=responses_stateless 时，前置提示词拼入 systemPrompt 之后、后置提示词拼入 instructions 末尾")
    void responsesStateless_前置后置提示词注入() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("PRE_PROMPT");
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("POST_PROMPT");

        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES_STATELESS.getCode(), Flux.empty());

        assertNotNull(captured.getInstructions());
        String instructions = captured.getInstructions();
        assertTrue(instructions.indexOf("PRE_PROMPT") > instructions.indexOf("SYSTEM_PROMPT_TEXT"),
                "前置提示词应拼接在 systemPrompt 之后");
        assertTrue(instructions.endsWith("POST_PROMPT"), "后置提示词应位于 instructions 末尾");
    }

    @Test
    @DisplayName("requestType=responses 时，前置/后置提示词为空白时跳过注入")
    void responses_前置后置提示词空白时跳过注入() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn("   ");
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn("");

        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        String instructions = captured.getInstructions();
        assertFalse(instructions.contains("   "), "空白前置提示词不应注入");
        assertEquals("SYSTEM_PROMPT_TEXT", instructions, "无注入时 instructions 应仅为 systemPrompt");
    }

    @Test
    @DisplayName("requestType=responses 时，前置/后置提示词为 null 时跳过注入")
    void responses_前置后置提示词为null时跳过注入() {
        when(chatDataProvider.getPreSystemPrompt(sessionId)).thenReturn(null);
        when(chatDataProvider.getPostSystemPrompt(sessionId)).thenReturn(null);

        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        assertEquals("SYSTEM_PROMPT_TEXT", captured.getInstructions(),
                "null 提示词不注入时 instructions 应仅为 systemPrompt");
    }

    @Test
    @DisplayName("requestType=responses 时，AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK 返回的提示词拼入 instructions（systemPrompt 之后）")
    void responses_AFTER_PRE_SYSTEM_PROMPT_BUILD结果拼入instructions() {
        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());
        lenient().when(hookManager.triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(harness.context), any(SystemPromptHookData.class)))
                .thenReturn(List.of(new SystemPromptHookResult("HOOK_PROMPT")));
        when(hookManager.castHookResult(any(HookResult.class), eq(SystemPromptHookResult.class)))
                .thenAnswer(inv -> {
                    HookResult result = inv.getArgument(0);
                    return result instanceof SystemPromptHookResult ? (SystemPromptHookResult) result : null;
                });

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES.getCode(), Flux.empty());

        verify(hookManager).triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(harness.context), any(SystemPromptHookData.class));
        String instructions = captured.getInstructions();
        assertTrue(instructions.contains("HOOK_PROMPT"), "instructions 应包含 HOOK 提示词");
        assertTrue(instructions.indexOf("HOOK_PROMPT") > instructions.indexOf("SYSTEM_PROMPT_TEXT"),
                "HOOK 提示词应拼接在 systemPrompt 之后");
    }

    @Test
    @DisplayName("requestType=responses_stateless 时，AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK 返回的提示词拼入 instructions")
    void responsesStateless_AFTER_PRE_SYSTEM_PROMPT_BUILD结果拼入instructions() {
        TestHarness harness = new TestHarness("SYSTEM_PROMPT_TEXT", List.of(), List.of(), null, List.of());
        when(systemToolManager.getToolDefinitions()).thenReturn(List.of());
        lenient().when(hookManager.triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(harness.context), any(SystemPromptHookData.class)))
                .thenReturn(List.of(new SystemPromptHookResult("HOOK_PROMPT")));
        when(hookManager.castHookResult(any(HookResult.class), eq(SystemPromptHookResult.class)))
                .thenAnswer(inv -> {
                    HookResult result = inv.getArgument(0);
                    return result instanceof SystemPromptHookResult ? (SystemPromptHookResult) result : null;
                });

        ChatRequest apiRequest = ChatRequest.builder().sessionId(sessionId).content("hello").conversationId("conv-1").build();
        com.ghost616.agentbase.dto.model.ChatRequest captured =
                executeChat(apiRequest, harness, RequestType.RESPONSES_STATELESS.getCode(), Flux.empty());

        verify(hookManager).triggerHooks(eq(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD), eq(harness.context), any(SystemPromptHookData.class));
        String instructions = captured.getInstructions();
        assertTrue(instructions.contains("HOOK_PROMPT"), "instructions 应包含 HOOK 提示词");
        assertTrue(instructions.indexOf("HOOK_PROMPT") > instructions.indexOf("SYSTEM_PROMPT_TEXT"),
                "HOOK 提示词应拼接在 systemPrompt 之后");
    }
}
