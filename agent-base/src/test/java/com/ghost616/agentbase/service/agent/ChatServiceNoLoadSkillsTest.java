package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
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
class ChatServiceNoLoadSkillsTest {

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
    private ModelInvoker modelInvoker;
    @Mock
    private SessionManager.MessageSaveBuilder msgBuilder;

    @Mock
    private HookManager hookManager;

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
                ToolDefinition.builder().name("test_tool").build());
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
    @DisplayName("Topic 1: _sys_load_skills 不存在时，技能消息应跳过")
    class NoLoadSkillsToolTest {

        @BeforeEach
        void setUpNoLoadSkills() {
            when(systemToolManager.getToolDefinitions()).thenReturn(
                    List.of(ToolDefinition.builder().name("some_other_tool").build()));
        }

        @Test
        @DisplayName("有技能时，不应生成可用技能列表消息")
        void shouldNotBuildAvailableSkillsMessage() {
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("my_skill").sessionAuth(null).description("my desc").build();

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(skill), null, null);
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "不应有可用技能列表消息");
        }

        @Test
        @DisplayName("有已加载技能时，不应生成已加载技能提示消息")
        void shouldNotBuildLoadedSkillsPrompt() {
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("loaded_skill").sessionAuth(null).prompt("my prompt").build();

            Map<String, String> sessionVars = new HashMap<>();
            sessionVars.put("_sys_loading_SKILLS", "[\"loaded_skill\"]");
            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(skill), sessionVars, null);
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "不应有已加载技能提示消息");
        }

        @Test
        @DisplayName("技能工具不注册到工具列表（filteredLoadedSkills 为空）")
        void skillToolsShouldNotBeRegistered() {
            ToolConfigDTO skillTool = ToolConfigDTO.builder()
                    .name("skill_tool").build();
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("my_skill").sessionAuth(null)
                    .skillTools(List.of(skillTool)).build();

            Map<String, String> sessionVars = new HashMap<>();
            sessionVars.put("_sys_loading_SKILLS", "[\"my_skill\"]");

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(skill), sessionVars, null);
            var captured = executeChat("1", harness);

            int toolCount = captured.getTools() != null ? captured.getTools().size() : 0;
            assertEquals(1, toolCount, "工具列表应只包含系统工具（some_other_tool），无技能工具");
        }

        @Test
        @DisplayName("parseLoadedSkills 被跳过：无已加载技能消息")
        void parseLoadedSkillsSkipped() {
            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(), null, null);
            var captured = executeChat("1", harness);

            long systemMsgCount = getSystemMessages(captured).stream()
                    .filter(m -> m.getContent() != null && m.getContent().contains("技能"))
                    .count();
            assertEquals(0, systemMsgCount, "不应有任何包含'技能'的系统消息");
        }
    }

    @Nested
    @DisplayName("Topic 2: systemToolManager.getToolDefinitions() 返回空列表时，行为同上")
    class EmptyToolDefinitionsTest {

        @BeforeEach
        void setUpEmptyDefinitions() {
            when(systemToolManager.getToolDefinitions()).thenReturn(List.of());
        }

        @Test
        @DisplayName("空列表时，不应生成可用技能列表消息")
        void emptyList_shouldNotBuildAvailableSkillsMessage() {
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("my_skill").sessionAuth(null).description("my desc").build();

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(skill), null, null);
            var captured = executeChat("1", harness);

            String skillsContent = findMessageByContent(getSystemMessages(captured), "可用的技能");
            assertNull(skillsContent, "不应有可用技能列表消息");
        }

        @Test
        @DisplayName("空列表时，不应生成已加载技能提示消息")
        void emptyList_shouldNotBuildLoadedSkillsPrompt() {
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("loaded_skill").sessionAuth(null).prompt("my prompt").build();

            Map<String, String> sessionVars = new HashMap<>();
            sessionVars.put("_sys_loading_SKILLS", "[\"loaded_skill\"]");
            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(skill), sessionVars, null);
            var captured = executeChat("1", harness);

            String loadedContent = findMessageByContent(getSystemMessages(captured), "以下技能已加载");
            assertNull(loadedContent, "不应有已加载技能提示消息");
        }

        @Test
        @DisplayName("空列表时，filteredLoadedSkills 保持空列表")
        void emptyList_filteredLoadedSkillsRemainsEmpty() {
            ToolConfigDTO skillTool = ToolConfigDTO.builder()
                    .name("skill_tool").build();
            SkillConfigDTO skill = SkillConfigDTO.builder()
                    .name("my_skill").sessionAuth(null)
                    .skillTools(List.of(skillTool)).build();

            Map<String, String> sessionVars = new HashMap<>();
            sessionVars.put("_sys_loading_SKILLS", "[\"my_skill\"]");

            var harness = new TestHarness("1", "sys_prompt", List.of(),
                    List.of(skill), sessionVars, null);
            var captured = executeChat("1", harness);

            int toolCount = captured.getTools() != null ? captured.getTools().size() : 0;
            assertEquals(0, toolCount, "工具列表应为空，无技能工具");
        }
    }
}
