package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceFilteringTest {

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

    private final AtomicBoolean toolInvoking = new AtomicBoolean(false);

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

        TestHarness(String sessionId, String systemPrompt, List<ToolConfigDTO> tools,
                    List<SkillConfigDTO> skills, Map<String, String> sessionVariables,
                    String parentSessionId) {
            this.mutator = new AgentExecutionContext.AgentContextMutator();
            this.context = new AgentExecutionContext(
                    sessionId, "1", systemPrompt, "1", null,
                    new ArrayList<>(), tools != null ? new ArrayList<>(tools) : new ArrayList<>(),
                    skills != null ? new ArrayList<>(skills) : null, mutator,
                    sessionVariables != null ? sessionVariables : new HashMap<>(),
                    new HashMap<>(), parentSessionId, "", null, null);
        }
    }

    private void mockChatInfrastructure() {
        lenient().when(chatDataProvider.getModelConfig(any())).thenReturn(
                new ModelConfigData("1", "key", "url", "model", 0.7, 1000, "test", null));
        lenient().when(modelInvokerManager.getInvoker(any())).thenReturn(modelInvoker);
        lenient().when(modelInvoker.invokeStream(any())).thenReturn(Flux.empty());
        lenient().when(modelInvoker.toToolDefinition(any())).thenReturn(
                com.ghost616.agentbase.dto.model.ToolDefinition.builder().name("test_tool").build());
        lenient().when(systemToolManager.getToolDefinitions()).thenReturn(
                List.of(ToolDefinition.builder().name("_sys_load_skills").build()));
    }

    private com.ghost616.agentbase.dto.model.ChatRequest executeChat(String sessionId, TestHarness harness) {
        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build(sessionId)).thenReturn(builder);
        when(builder.modelIdOverride(any())).thenReturn(builder);
        when(builder.build()).thenReturn(
                new AgentContextManager.AgentSessionContext(
                        harness.context, harness.mutator, toolInvoking));

        mockChatInfrastructure();

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .content("hello")
                .conversationId("conv-1")
                .build();

        chatService.chat(request).subscribe();

        ArgumentCaptor<com.ghost616.agentbase.dto.model.ChatRequest> captor =
                ArgumentCaptor.forClass(com.ghost616.agentbase.dto.model.ChatRequest.class);
        verify(modelInvoker).invokeStream(captor.capture());
        return captor.getValue();
    }

    private List<com.ghost616.agentbase.dto.model.Message> getSystemMessages(
            com.ghost616.agentbase.dto.model.ChatRequest captured) {
        return captured.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .toList();
    }

    private String findMessageByContent(List<com.ghost616.agentbase.dto.model.Message> msgs, String keyword) {
        return msgs.stream()
                .map(com.ghost616.agentbase.dto.model.Message::getContent)
                .filter(c -> c.contains(keyword))
                .findFirst()
                .orElse(null);
    }

    @Nested
    @DisplayName("Section 1: 可用技能列表已 HOOK 化（不再由 ChatService 生成）")
    class SkillsFilteringTest {

        @Test
        @DisplayName("存在 CHILD/PARENT/ALL/null 技能时，ChatService 不再生成可用技能列表消息（迁移至 AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK）")
        void mainSession_shouldFilterChildSkills() {
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_only").sessionAuth(SessionAuthType.CHILD).description("child desc").build();
            SkillConfigDTO parentSkill = SkillConfigDTO.builder()
                    .name("parent_only").sessionAuth(SessionAuthType.PARENT).description("parent desc").build();
            SkillConfigDTO allSkill = SkillConfigDTO.builder()
                    .name("all_skill").sessionAuth(SessionAuthType.ALL).description("all desc").build();
            SkillConfigDTO nullAuthSkill = SkillConfigDTO.builder()
                    .name("null_auth").description("null desc").build();

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(childSkill, parentSkill, allSkill, nullAuthSkill), null, null);
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "移除可用技能列表生成后，ChatService 不应再生成技能列表消息");
        }

        @Test
        @DisplayName("存在 PARENT/null 技能时，ChatService 不再生成可用技能列表消息")
        void mainSession_shouldIncludeParentAllAndNullSkills() {
            SkillConfigDTO parentSkill = SkillConfigDTO.builder()
                    .name("parent_skill").sessionAuth(SessionAuthType.PARENT).build();
            SkillConfigDTO nullSkill = SkillConfigDTO.builder()
                    .name("null_skill").build();

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(parentSkill, nullSkill), null, null);
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "移除可用技能列表生成后，ChatService 不应再生成技能列表消息");
        }

        @Test
        @DisplayName("非主会话（子会话）时，ChatService 不再生成可用技能列表消息")
        void childSession_shouldNotFilterSkills() {
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_skill").sessionAuth(SessionAuthType.CHILD).build();
            SkillConfigDTO parentSkill = SkillConfigDTO.builder()
                    .name("parent_skill").sessionAuth(SessionAuthType.PARENT).build();

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(childSkill, parentSkill), null, "99");
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "移除可用技能列表生成后，ChatService 不应再生成技能列表消息");
        }

        @Test
        @DisplayName("技能列表为空时，不生成可用技能消息")
        void emptySkills_shouldNotAddSkillMessage() {
            var harness = new TestHarness("1", "sys_prompt", List.of(), List.of(), null, null);
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "不应有技能列表消息");
        }

        @Test
        @DisplayName("skills 为 null 时，不生成可用技能消息")
        void nullSkills_shouldNotAddSkillMessage() {
            var harness = new TestHarness("1", "sys_prompt", List.of(), null, null, null);
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "不应有技能列表消息");
        }
    }

    @Nested
    @DisplayName("Section 2: 已加载技能提示词已 HOOK 化（不再由 ChatService 生成）")
    class LoadedSkillsPromptFilteringTest {

        private TestHarness createHarnessWithLoadedSkills(List<SkillConfigDTO> skills,
                                                           List<String> loadedSkillNames,
                                                            String parentSessionId) {
            Map<String, String> sessionVars = new HashMap<>();
            sessionVars.put("_sys_loading_SKILLS",
                    "[\"" + String.join("\",\"", loadedSkillNames) + "\"]");
            return new TestHarness("1", "sys_prompt", List.of(), skills, sessionVars, parentSessionId);
        }

        @Test
        @DisplayName("存在 CHILD/ALL 已加载技能时，ChatService 不再生成已加载技能提示词消息（迁移至 AFTER_PRE_SYSTEM_PROMPT_BUILD HOOK）")
        void mainSession_shouldSkipChildLoadedSkills() {
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_loaded").sessionAuth(SessionAuthType.CHILD).prompt("child prompt").build();
            SkillConfigDTO allSkill = SkillConfigDTO.builder()
                    .name("all_loaded").sessionAuth(SessionAuthType.ALL).prompt("all prompt").build();

            var harness = createHarnessWithLoadedSkills(
                    List.of(childSkill, allSkill), List.of("child_loaded", "all_loaded"), null);
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "移除已加载技能提示词生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("存在 PARENT/null 已加载技能时，ChatService 不再生成已加载技能提示词消息")
        void mainSession_shouldIncludeNonChildLoadedSkills() {
            SkillConfigDTO parentSkill = SkillConfigDTO.builder()
                    .name("parent_loaded").sessionAuth(SessionAuthType.PARENT).prompt("parent prompt").build();
            SkillConfigDTO nullSkill = SkillConfigDTO.builder()
                    .name("null_loaded").prompt("null prompt").build();

            var harness = createHarnessWithLoadedSkills(
                    List.of(parentSkill, nullSkill), List.of("parent_loaded", "null_loaded"), null);
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "移除已加载技能提示词生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("子会话存在 CHILD 已加载技能时，ChatService 不再生成已加载技能提示词消息")
        void childSession_shouldNotFilterChildLoadedSkills() {
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_loaded").sessionAuth(SessionAuthType.CHILD).prompt("child prompt").build();

            var harness = createHarnessWithLoadedSkills(
                    List.of(childSkill), List.of("child_loaded"), "99");
            // 子会话的 getSessionVariable 通过 mutator 回调委托给父会话，需设置回调
            harness.mutator.getSessionVarCallback = key -> {
                if ("_sys_loading_SKILLS".equals(key)) {
                    return "[\"child_loaded\"]";
                }
                return null;
            };
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "移除已加载技能提示词生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("没有任何已加载技能时，不生成提示词消息")
        void noLoadedSkills_shouldNotAddPromptMessage() {
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("not_loaded").sessionAuth(SessionAuthType.ALL).prompt("prompt").build();

            Map<String, String> sessionVars = new HashMap<>();
            sessionVars.put("_sys_loading_SKILLS", "[]");
            var harness = new TestHarness("1", "sys_prompt", List.of(), List.of(skill), sessionVars, null);
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "不应有已加载技能提示词消息");
        }

        @Test
        @DisplayName("_sys_loading_SKILLS 为 null 时，不生成提示词消息")
        void nullLoadingSkills_shouldNotAddPromptMessage() {
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("skill_a").sessionAuth(SessionAuthType.ALL).prompt("prompt").build();

            var harness = new TestHarness("1", "sys_prompt", List.of(), List.of(skill), null, null);
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "不应有已加载技能提示词消息");
        }
    }

    @Nested
    @DisplayName("Section 3: 子会话权限说明已迁移（不再由 ChatService 生成）")
    class ChildSessionCapabilitiesMergeTest {

        private static final String CAP_SEARCH = "以下为子会话相关的工具/技能权限说明";

        @Test
        @DisplayName("存在 CHILD/ALL 工具时，ChatService 不再生成子会话权限说明（已迁移至 platform-app getPreSystemPrompt）")
        void onlyTools_shouldNotGenerateCapabilitiesMessage() {
            ToolConfigDTO childTool = ToolConfigDTO.builder()
                    .name("child_tool").sessionAuth(SessionAuthType.CHILD).description("for child").build();
            ToolConfigDTO allTool = ToolConfigDTO.builder()
                    .name("all_tool").sessionAuth(SessionAuthType.ALL).build();
            ToolConfigDTO nullTool = ToolConfigDTO.builder()
                    .name("null_tool").build();

            var harness = new TestHarness("1", "sys_prompt",
                    List.of(childTool, allTool, nullTool), null, null, null);
            var captured = executeChat("1", harness);

            String capsContent = findMessageByContent(getSystemMessages(captured), CAP_SEARCH);
            assertNull(capsContent, "移除子会话权限说明生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("存在 CHILD/ALL 技能时，ChatService 不再生成子会话权限说明")
        void onlySkills_shouldNotGenerateCapabilitiesMessage() {
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_skill").sessionAuth(SessionAuthType.CHILD).description("for child").build();
            SkillConfigDTO allSkill = SkillConfigDTO.builder()
                    .name("all_skill").sessionAuth(SessionAuthType.ALL).build();
            SkillConfigDTO nullSkill = SkillConfigDTO.builder()
                    .name("null_skill").build();

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(childSkill, allSkill, nullSkill), null, null);
            var captured = executeChat("1", harness);

            String capsContent = findMessageByContent(getSystemMessages(captured), CAP_SEARCH);
            assertNull(capsContent, "移除子会话权限说明生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("同时存在工具和技能时，ChatService 不再生成子会话权限说明")
        void bothToolsAndSkills_shouldNotGenerateCapabilitiesMessage() {
            ToolConfigDTO childTool = ToolConfigDTO.builder()
                    .name("child_tool").sessionAuth(SessionAuthType.CHILD).build();
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_skill").sessionAuth(SessionAuthType.CHILD).build();

            var harness = new TestHarness("1", "sys_prompt",
                    List.of(childTool), List.of(childSkill), null, null);
            var captured = executeChat("1", harness);

            String capsContent = findMessageByContent(getSystemMessages(captured), CAP_SEARCH);
            assertNull(capsContent, "移除子会话权限说明生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("工具和技能均为空时：不生成子会话能力消息")
        void bothEmpty_shouldNotGenerateMessage() {
            var harness = new TestHarness("1", "sys_prompt", List.of(), List.of(), null, null);
            var captured = executeChat("1", harness);

            String capsContent = findMessageByContent(getSystemMessages(captured), CAP_SEARCH);
            assertNull(capsContent, "不应有子会话能力消息");
        }

        @Test
        @DisplayName("存在 CHILD 技能时，ChatService 不再生成权限说明消息")
        void childSessionSkills_shouldNotGenerateCapabilitiesMessage() {
            ToolConfigDTO tool = ToolConfigDTO.builder()
                    .name("child_tool").sessionAuth(SessionAuthType.CHILD).build();
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_skill").sessionAuth(SessionAuthType.CHILD).build();
            SkillConfigDTO allSkill = SkillConfigDTO.builder()
                    .name("all_skill").sessionAuth(SessionAuthType.ALL).build();
            SkillConfigDTO nullSkill = SkillConfigDTO.builder()
                    .name("null_skill").build();
            SkillConfigDTO parentSkill = SkillConfigDTO.builder()
                    .name("parent_skill").sessionAuth(SessionAuthType.PARENT).build();

            var harness = new TestHarness("1", "sys_prompt", List.of(tool),
                    List.of(childSkill, allSkill, nullSkill, parentSkill), null, null);
            var captured = executeChat("1", harness);

            String capsContent = findMessageByContent(getSystemMessages(captured), CAP_SEARCH);
            assertNull(capsContent, "移除子会话权限说明生成后，ChatService 不应再生成该消息");
        }

        @Test
        @DisplayName("子会话（非主会话）时不生成子会话能力提示")
        void childSession_shouldNotGenerateCapabilitiesMessage() {
            ToolConfigDTO childTool = ToolConfigDTO.builder()
                    .name("child_tool").sessionAuth(SessionAuthType.CHILD).build();
            SkillConfigDTO childSkill = SkillConfigDTO.builder()
                    .name("child_skill").sessionAuth(SessionAuthType.CHILD).build();

            var harness = new TestHarness("1", "sys_prompt",
                    List.of(childTool), List.of(childSkill), null, "99");
            var captured = executeChat("1", harness);

            String capsContent = findMessageByContent(getSystemMessages(captured), CAP_SEARCH);
            assertNull(capsContent, "子会话自身不应生成子会话能力消息");
        }
    }
}
