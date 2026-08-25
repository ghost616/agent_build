package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.McpExpandedToolDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.enums.ToolType;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.sendmessage.ChildCreateSession;
import com.ghost616.agentbase.sendmessage.ConversationIdMessage;
import com.ghost616.agentbase.sendmessage.MessageSender;
import com.ghost616.agentbase.sendmessage.SendUserMessage;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentContextManagerTest {

    @Mock
    private ContextDataProvider dataProvider;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private MessageSender messageSender;

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
        agentContextManager = new AgentContextManager(registry);
    }

    private void stubBasicContext() {
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(sessionManager.getMessages(sessionId)).thenReturn(List.of());
        when(toolManager.getSessionTools(eq(sessionId), anyBoolean())).thenReturn(List.of());
    }

    @Test
    void 正向_MCP工具被展开_非MCP工具保持不变() {
        stubBasicContext();

        ToolConfigDTO mcpTool = ToolConfigDTO.builder()
                .name("mcp_tool")
                .toolType(ToolType.MCP_HTTP)
                .implPath("http://localhost/mcp")
                .authConfig("{}")
                .build();

        ToolConfigDTO javaTool = ToolConfigDTO.builder()
                .name("java_tool")
                .toolType(ToolType.JAVA)
                .implPath("com.example.MyTool")
                .build();

        McpExpandedToolDTO expanded1 = McpExpandedToolDTO.builder()
                .name("mcp_tool_func1")
                .toolType(ToolType.MCP_HTTP)
                .remoteToolName("func1")
                .build();
        McpExpandedToolDTO expanded2 = McpExpandedToolDTO.builder()
                .name("mcp_tool_func2")
                .toolType(ToolType.MCP_HTTP)
                .remoteToolName("func2")
                .build();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("test_skill")
                .skillTools(List.of(mcpTool, javaTool))
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));
        when(toolManager.expandMcpTools(mcpTool)).thenReturn(List.of(expanded1, expanded2));

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();
        List<SkillConfigDTO> resultSkills = context.getSkills();

        assertEquals(1, resultSkills.size());
        SkillConfigDTO resultSkill = resultSkills.get(0);
        List<ToolConfigDTO> resultTools = resultSkill.getSkillTools();

        assertEquals(3, resultTools.size());
        assertEquals("mcp_tool_func1", resultTools.get(0).getName());
        assertEquals(ToolType.MCP_HTTP, resultTools.get(0).getToolType());
        assertInstanceOf(McpExpandedToolDTO.class, resultTools.get(0));

        assertEquals("mcp_tool_func2", resultTools.get(1).getName());
        assertEquals(ToolType.MCP_HTTP, resultTools.get(1).getToolType());
        assertInstanceOf(McpExpandedToolDTO.class, resultTools.get(1));

        assertSame(javaTool, resultTools.get(2));
        assertEquals(ToolType.JAVA, resultTools.get(2).getToolType());
    }

    @Test
    void 反向_skillTools为null时不抛出异常() {
        stubBasicContext();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("null_tools_skill")
                .skillTools(null)
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();
        List<SkillConfigDTO> resultSkills = context.getSkills();

        assertEquals(1, resultSkills.size());
        assertNull(resultSkills.get(0).getSkillTools());
    }

    @Test
    void 反向_skillTools为空列表时不处理() {
        stubBasicContext();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("empty_tools_skill")
                .skillTools(List.of())
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();
        List<SkillConfigDTO> resultSkills = context.getSkills();

        assertEquals(1, resultSkills.size());
        assertTrue(resultSkills.get(0).getSkillTools().isEmpty());
    }

    @Test
    void 边界_技能中全部为MCP工具时全部展开() {
        stubBasicContext();

        ToolConfigDTO mcp1 = ToolConfigDTO.builder().name("mcp1").toolType(ToolType.MCP_HTTP).implPath("http://a").authConfig("{}").build();
        ToolConfigDTO mcp2 = ToolConfigDTO.builder().name("mcp2").toolType(ToolType.MCP_HTTP).implPath("http://b").authConfig("{}").build();

        McpExpandedToolDTO e1 = McpExpandedToolDTO.builder().name("mcp1_f1").toolType(ToolType.MCP_HTTP).remoteToolName("f1").build();
        McpExpandedToolDTO e2 = McpExpandedToolDTO.builder().name("mcp2_f1").toolType(ToolType.MCP_HTTP).remoteToolName("f1").build();

        SkillConfigDTO skill = SkillConfigDTO.builder().name("all_mcp").skillTools(List.of(mcp1, mcp2)).build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));
        when(toolManager.expandMcpTools(mcp1)).thenReturn(List.of(e1));
        when(toolManager.expandMcpTools(mcp2)).thenReturn(List.of(e2));

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(2, resultTools.size());
        assertEquals("mcp1_f1", resultTools.get(0).getName());
        assertEquals("mcp2_f1", resultTools.get(1).getName());
    }

    @Test
    void 边界_技能中无MCP工具时所有工具保持不变() {
        stubBasicContext();

        ToolConfigDTO javaTool = ToolConfigDTO.builder().name("jt").toolType(ToolType.JAVA).build();
        ToolConfigDTO tsTool = ToolConfigDTO.builder().name("ts").toolType(ToolType.TYPESCRIPT).build();

        SkillConfigDTO skill = SkillConfigDTO.builder().name("no_mcp").skillTools(List.of(javaTool, tsTool)).build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(2, resultTools.size());
        assertSame(javaTool, resultTools.get(0));
        assertSame(tsTool, resultTools.get(1));
    }

    @Test
    void 反向_已展开的McpExpandedToolDTO不会被二次展开() {
        stubBasicContext();

        ToolConfigDTO mcpTool = ToolConfigDTO.builder()
                .name("mcp_tool")
                .toolType(ToolType.MCP_HTTP)
                .implPath("http://localhost/mcp")
                .authConfig("{}")
                .build();

        McpExpandedToolDTO alreadyExpanded = McpExpandedToolDTO.builder()
                .name("mcp_tool_func1")
                .toolType(ToolType.MCP_HTTP)
                .remoteToolName("func1")
                .build();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("mixed_skill")
                .skillTools(List.of(mcpTool, alreadyExpanded))
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));
        when(toolManager.expandMcpTools(mcpTool)).thenReturn(List.of(
                McpExpandedToolDTO.builder().name("mcp_tool_funcA").toolType(ToolType.MCP_HTTP).remoteToolName("funcA").build()
        ));

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(2, resultTools.size(), "展开后应该是 2 个工具: 新展开的1个 + 已有的1个");
        assertEquals("mcp_tool_funcA", resultTools.get(0).getName());
        assertSame(alreadyExpanded, resultTools.get(1), "已展开的工具应原样保留，不被二次展开");
        verify(toolManager, times(1)).expandMcpTools(any());
    }

    @Test
    void 边界_skills为空列表时不处理任何技能() {
        stubBasicContext();

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
        AgentExecutionContext context = sessionContext.context();

        assertTrue(context.getSkills().isEmpty());
        verify(toolManager, never()).expandMcpTools(any());
    }

    @Test
    void 正向_agentId为null时build正常返回() {
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(null, "test prompt", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(sessionManager.getMessages(sessionId)).thenReturn(List.of());
        when(toolManager.getSessionTools(eq(sessionId), anyBoolean())).thenReturn(List.of());

        AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();

        assertNotNull(sessionContext);
        assertNull(sessionContext.context().getAgentId());
    }

    @Test
    void 反向_ctxData为null时抛出AgentException() {
        when(dataProvider.loadAgentContext(sessionId)).thenReturn(null);

        assertThrows(AgentException.class, () -> agentContextManager.build(sessionId).build());
    }

    @Test
    void 正向_MCP展开后所有工具的sessionAuth被设置为PARENT() {
        ToolConfigDTO mcpTool = ToolConfigDTO.builder()
                .name("mcp_tool")
                .toolType(ToolType.MCP_HTTP)
                .implPath("http://localhost/mcp")
                .authConfig("{}")
                .sessionAuth(SessionAuthType.ALL)
                .build();

        McpExpandedToolDTO expanded1 = McpExpandedToolDTO.builder()
                .name("mcp_tool_func1").toolType(ToolType.MCP_HTTP).remoteToolName("func1").build();
        McpExpandedToolDTO expanded2 = McpExpandedToolDTO.builder()
                .name("mcp_tool_func2").toolType(ToolType.MCP_HTTP).remoteToolName("func2").build();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("mcp_skill")
                .skillTools(List.of(mcpTool))
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));
        when(toolManager.expandMcpTools(mcpTool)).thenReturn(List.of(expanded1, expanded2));

        AgentExecutionContext context = agentContextManager.build(sessionId).build().context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(2, resultTools.size());
        resultTools.forEach(t -> assertEquals(SessionAuthType.PARENT, t.getSessionAuth(),
                "展开后的每个工具 sessionAuth 应为 PARENT"));
    }

    @Test
    void 正向_非MCP工具的sessionAuth被设置为PARENT() {
        ToolConfigDTO javaTool = ToolConfigDTO.builder()
                .name("java_tool").toolType(ToolType.JAVA).implPath("com.example.MyTool").build();
        ToolConfigDTO tsTool = ToolConfigDTO.builder()
                .name("ts_tool").toolType(ToolType.TYPESCRIPT).implPath("test.ts").build();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("non_mcp_skill")
                .skillTools(List.of(javaTool, tsTool))
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));

        AgentExecutionContext context = agentContextManager.build(sessionId).build().context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(2, resultTools.size());
        resultTools.forEach(t -> assertEquals(SessionAuthType.PARENT, t.getSessionAuth()));
    }

    @Test
    void 正向_CHILD分支下MCP工具不展开直接设置sessionAuth为PARENT() {
        ToolConfigDTO childMcpTool = ToolConfigDTO.builder()
                .name("child_mcp")
                .toolType(ToolType.MCP_HTTP)
                .implPath("http://localhost/child")
                .authConfig("{}")
                .sessionAuth(SessionAuthType.CHILD)
                .build();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("child_skill")
                .skillTools(List.of(childMcpTool))
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));

        AgentExecutionContext context = agentContextManager.build(sessionId).build().context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(1, resultTools.size());
        assertSame(childMcpTool, resultTools.get(0), "CHILD 分支下工具不应被展开，应直接加入");
        assertEquals(SessionAuthType.PARENT, resultTools.get(0).getSessionAuth(), "sessionAuth 应被设为 PARENT");
        verify(toolManager, never()).expandMcpTools(any());
    }

    @Test
    void 正向_子会话模式下MCP工具正常展开并设置sessionAuth为PARENT() {
        String childSessionId = "99";
        String parentId = "1";
        ToolConfigDTO mcpTool = ToolConfigDTO.builder()
                .name("mcp_tool")
                .toolType(ToolType.MCP_HTTP)
                .implPath("http://localhost/mcp")
                .authConfig("{}")
                .sessionAuth(SessionAuthType.CHILD)
                .build();

        McpExpandedToolDTO expanded = McpExpandedToolDTO.builder()
                .name("mcp_func").toolType(ToolType.MCP_HTTP).remoteToolName("func").build();

        SkillConfigDTO childSkill = SkillConfigDTO.builder()
                .name("sub_skill")
                .skillTools(List.of(mcpTool))
                .build();

        when(dataProvider.loadAgentContext(parentId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "parent", "200", 10, List.of(), Map.of(), null, null, null, null));
        when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "sub prompt", "200", 10, List.of(childSkill), Map.of(), parentId, null, null, null));
        when(sessionManager.getMessages(anyString())).thenReturn(List.of());
        when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());
        when(toolManager.expandMcpTools(mcpTool)).thenReturn(List.of(expanded));

        AgentExecutionContext context = agentContextManager.build(childSessionId).build().context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(1, resultTools.size());
        assertEquals(SessionAuthType.PARENT, resultTools.get(0).getSessionAuth());
    }

    @Test
    void 正向_McpExpandedToolDTO直接设置sessionAuth为PARENT且保留() {
        McpExpandedToolDTO alreadyExpanded = McpExpandedToolDTO.builder()
                .name("pre_expanded_func")
                .toolType(ToolType.MCP_HTTP)
                .remoteToolName("func")
                .sessionAuth(SessionAuthType.ALL)
                .build();

        SkillConfigDTO skill = SkillConfigDTO.builder()
                .name("pre_expanded_skill")
                .skillTools(List.of(alreadyExpanded))
                .build();

        when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                new ContextDataProvider.AgentContextData(agentId, "test prompt", "200", 10, List.of(skill), Map.of(), null, null, null, null));

        AgentExecutionContext context = agentContextManager.build(sessionId).build().context();
        List<ToolConfigDTO> resultTools = context.getSkills().get(0).getSkillTools();

        assertEquals(1, resultTools.size());
        assertSame(alreadyExpanded, resultTools.get(0));
        assertEquals(SessionAuthType.PARENT, resultTools.get(0).getSessionAuth());
    }

    @Nested
    class RefreshMethodTest {

        @BeforeEach
        void setUp() {
            stubBasicContext();
            // 懒构建：build 仅轻量构建，需访问 context() 触发完整构建，保证 setUp 中的 getMessages/getSessionTools stub 生效
            agentContextManager.build(sessionId).build().context();
        }

        @Test
        void 正向_refreshHistory后历史为最新数据() {
            var msg1 = new MessageDataProvider.MessageDTO("1", sessionId, "user", "hello", null, null,
                    LocalDateTime.now(), null, null, null, null, null, null, null, null, null);
            var msg2 = new MessageDataProvider.MessageDTO("2", sessionId, "assistant", "hi", null, null,
                    LocalDateTime.now(), null, null, null, null, null, null, null, null, null);
            when(dataProvider.getLatestMessages(sessionId)).thenReturn(List.of(msg1, msg2));

            agentContextManager.refreshHistory(sessionId);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals(2, context.getHistory().size());
            assertEquals("hello", context.getHistory().get(0).content());
            assertEquals("hi", context.getHistory().get(1).content());
        }

        @Test
        void 正向_refreshSessionVariables后变量为最新数据() {
            Map<String, String> newVars = Map.of("k1", "v1", "k2", "v2");
            when(dataProvider.getLatestSessionVariables(sessionId)).thenReturn(newVars);

            agentContextManager.refreshSessionVariables(sessionId);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals("v1", context.getSessionVariable("k1"));
            assertEquals("v2", context.getSessionVariable("k2"));
        }

        @Test
        void 正向_refreshConversationVariables后变量为最新数据() {
            Map<String, String> newVars = Map.of("ck1", "cv1");
            when(dataProvider.getLatestConversationVariables(sessionId)).thenReturn(newVars);

            agentContextManager.refreshConversationVariables(sessionId);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals("cv1", context.getConversationVariable("ck1"));
        }

        @Test
        void 正向_refreshChildSessions后子会话为最新数据() {
            var child = new AgentExecutionContext.ChildSession("10", "child", "desc", "300");
            when(dataProvider.getLatestChildSessions(sessionId)).thenReturn(List.of(child));

            agentContextManager.refreshChildSessions(sessionId);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals(1, context.getChildSessions().size());
            assertEquals("10", context.getChildSessions().get(0).sessionId());
        }

        @Test
        void 边界_缓存中无上下文时刷新方法不抛异常() {
            String nonExistentSession = "999";

            assertDoesNotThrow(() -> agentContextManager.refreshHistory(nonExistentSession));
            assertDoesNotThrow(() -> agentContextManager.refreshSessionVariables(nonExistentSession));
            assertDoesNotThrow(() -> agentContextManager.refreshConversationVariables(nonExistentSession));
            assertDoesNotThrow(() -> agentContextManager.refreshChildSessions(nonExistentSession));
        }

        @Test
        void 正向_refreshHistory后getHistory返回新数据而非旧数据() {
            agentContextManager.remove(sessionId);

            var oldMsg = new MessageDataProvider.MessageDTO("1", sessionId, "user", "old", null, null,
                    LocalDateTime.now(), null, null, null, null, null, null, null, null, null);
            when(sessionManager.getMessages(sessionId)).thenReturn(List.of(oldMsg));

            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();
            assertEquals(1, ctx.context().getHistory().size());
            assertEquals("old", ctx.context().getHistory().get(0).content());

            var newMsg = new MessageDataProvider.MessageDTO("2", sessionId, "user", "new", null, null,
                    LocalDateTime.now(), null, null, null, null, null, null, null, null, null);
            when(dataProvider.getLatestMessages(sessionId)).thenReturn(List.of(newMsg));

            agentContextManager.refreshHistory(sessionId);

            assertEquals(1, ctx.context().getHistory().size());
            assertEquals("new", ctx.context().getHistory().get(0).content());
        }
    }

    @Nested
    class ParentChildSessionTest {

        private final String parentSessionId = "1";
        private final String childSessionId = "2";

        @BeforeEach
        void setUpParent() {
            when(dataProvider.loadAgentContext(parentSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "parent prompt", "200", 10, List.of(), new HashMap<>(), null, null, null, null));
            when(sessionManager.getMessages(parentSessionId)).thenReturn(List.of());
            when(toolManager.getSessionTools(eq(parentSessionId), anyBoolean())).thenReturn(List.of());

            // 懒构建：build 仅轻量构建，访问 context() 触发完整构建（等价于旧 doBuild 在 setUp 中完成的完整构建）
            agentContextManager.build(parentSessionId).build().context();
        }

        private void stubChildSession(String childId, String parentId) {
            when(dataProvider.loadAgentContext(childId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), new HashMap<>(), parentId, null, null, null));
            when(sessionManager.getMessages(childId)).thenReturn(List.of());
            when(toolManager.getSessionTools(eq(childId), anyBoolean())).thenReturn(List.of());
        }

        @Test
        void 正向_构建时传入parentSessionId能正常构建上下文() {
            stubChildSession(childSessionId, parentSessionId);

            AgentExecutionContext childContext = agentContextManager.build(childSessionId).build().context();

            assertNotNull(childContext);
            assertEquals(parentSessionId, childContext.getParentSessionId());
        }

        @Test
        void 正向_构建时传入childSessions在context中可见() {
            String freshSessionId = "100";
            var childSession = new AgentExecutionContext.ChildSession("10", "sub-agent", "test sub", "300");
            when(dataProvider.loadAgentContext(freshSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "parent prompt", "200", 10, List.of(), new HashMap<>(), null, List.of(childSession), null, null));
            when(sessionManager.getMessages(freshSessionId)).thenReturn(List.of());
            when(toolManager.getSessionTools(eq(freshSessionId), anyBoolean())).thenReturn(List.of());

            AgentExecutionContext parentContext = agentContextManager.build(freshSessionId).build().context();

            assertEquals(1, parentContext.getChildSessions().size());
            assertEquals("10", parentContext.getChildSessions().get(0).sessionId());
            assertEquals("sub-agent", parentContext.getChildSessions().get(0).sessionName());
        }

        @Test
        void 正向_子会话getParentSessionId返回父会话ID() {
            stubChildSession(childSessionId, parentSessionId);

            AgentExecutionContext childContext = agentContextManager.build(childSessionId).build().context();

            assertEquals(parentSessionId, childContext.getParentSessionId());
        }

        @Test
        void 正向_子会话putSessionVariable写入父context的sessionVariables() {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();
            AgentContextManager.AgentSessionContext parentCtx = agentContextManager.get(parentSessionId);

            childCtx.context().putSessionVariable("childKey", "childValue");

            assertEquals("childValue", parentCtx.context().getSessionVariable("childKey"));
        }

        @Test
        void 正向_子会话removeSessionVariable删除父context的sessionVariables() {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();
            AgentContextManager.AgentSessionContext parentCtx = agentContextManager.get(parentSessionId);

            parentCtx.context().putSessionVariable("keyToRemove", "toBeRemoved");
            assertTrue(parentCtx.context().getSessionVariableKeys().contains("keyToRemove"));

            childCtx.context().removeSessionVariable("keyToRemove");

            assertNull(parentCtx.context().getSessionVariable("keyToRemove"));
        }

        @Test
        void 正向_子会话build时父上下文被自动构建并缓存() {
            String autoParentId = "100";
            String autoChildId = "101";
            when(dataProvider.loadAgentContext(autoParentId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "auto parent", "200", 10, List.of(), new HashMap<>(), null, null, null, null));
            when(sessionManager.getMessages(autoParentId)).thenReturn(List.of());
            when(toolManager.getSessionTools(eq(autoParentId), anyBoolean())).thenReturn(List.of());

            stubChildSession(autoChildId, autoParentId);

            // 新行为：get 缓存未命中时执行轻量构建（加载 AgentContextData + 防御性拷贝）并放入缓存，不再返回 null
            AgentContextManager.AgentSessionContext preCtx = agentContextManager.get(autoParentId);
            assertNotNull(preCtx, "get 未命中时轻量构建并返回");
            assertNotNull(preCtx.agentContextData(), "轻量构建应包含 AgentContextData");
            assertSame(preCtx, agentContextManager.get(autoParentId), "轻量构建结果已放入缓存");

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(autoChildId).build();

            assertNotNull(childCtx);
            AgentContextManager.AgentSessionContext autoParentCtx = agentContextManager.get(autoParentId);
            assertNotNull(autoParentCtx, "父上下文被自动构建并缓存");
            assertEquals(autoParentId, autoParentCtx.context().getSessionId());

            // 子上下文懒构建可正常触发，父上下文已缓存可直接复用
            assertNotNull(childCtx.context(), "子上下文懒构建可正常触发");
        }

        @Test
        void 反向_parentSessionId对应父session不存在时子会话构建抛异常() {
            String nonExistentParent = "999";
            String orphanSessionId = "3";
            stubChildSession(orphanSessionId, nonExistentParent);

            // build 为轻量构建不抛异常，懒构建（context()）解析父会话时才抛出
            assertThrows(AgentException.class,
                    () -> agentContextManager.build(orphanSessionId).build().context());
        }

        @Test
        void 正向_子会话getSessionVariable读取父context的值() {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext pCtx = agentContextManager.get(parentSessionId);
            pCtx.context().putSessionVariable("parentShared", "sharedVal");

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            assertEquals("sharedVal", childCtx.context().getSessionVariable("parentShared"));
        }

        @Test
        void 正向_子会话getSessionVariableKeys返回父context的keys() {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext pCtx = agentContextManager.get(parentSessionId);
            pCtx.context().putSessionVariable("pk1", "pv1");
            pCtx.context().putSessionVariable("pk2", "pv2");

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            Set<String> keys = childCtx.context().getSessionVariableKeys();
            assertTrue(keys.contains("pk1"));
            assertTrue(keys.contains("pk2"));
        }

        @Test
        void 正向_子会话getConversationVariable委托父context() {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext pCtx = agentContextManager.get(parentSessionId);
            pCtx.context().putConversationVariable("convKey", "convVal");

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            assertEquals("convVal", childCtx.context().getConversationVariable("convKey"));
        }

        @Test
        void 正向_子会话getConversationVariableKeys委托父context() {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext pCtx = agentContextManager.get(parentSessionId);
            pCtx.context().putConversationVariable("ck1", "cv1");

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            assertTrue(childCtx.context().getConversationVariableKeys().contains("ck1"));
        }

        @Test
        void 反向_子会话putSessionVariable不影响本地sessionVariables() throws Exception {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            childCtx.context().putSessionVariable("childPut", "childVal");

            java.lang.reflect.Field localVars = AgentExecutionContext.class.getDeclaredField("sessionVariables");
            localVars.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> childLocal = (Map<String, String>) localVars.get(childCtx.context());
            assertFalse(childLocal.containsKey("childPut"), "子会话的本地sessionVariables不应包含通过put写入的key");
        }

        @Test
        void 反向_子会话putConversationVariable不影响本地conversationVariables() throws Exception {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            childCtx.context().putConversationVariable("childConv", "convVal");

            java.lang.reflect.Field localVars = AgentExecutionContext.class.getDeclaredField("conversationVariables");
            localVars.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> childLocal = (Map<String, String>) localVars.get(childCtx.context());
            assertFalse(childLocal.containsKey("childConv"), "子会话的本地conversationVariables不应包含通过put写入的key");
        }

        @Test
        void 反向_子会话removeSessionVariable不影响本地sessionVariables() throws Exception {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            AgentContextManager.AgentSessionContext pCtx = agentContextManager.get(parentSessionId);
            pCtx.context().putSessionVariable("toBeRemoved", "val");

            childCtx.context().removeSessionVariable("toBeRemoved");

            java.lang.reflect.Field localVars = AgentExecutionContext.class.getDeclaredField("sessionVariables");
            localVars.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> childLocal = (Map<String, String>) localVars.get(childCtx.context());
            assertFalse(childLocal.containsKey("toBeRemoved"), "子会话的本地sessionVariables不应包含已删除的key");
        }

        @Test
        void 反向_子会话removeConversationVariable不影响本地conversationVariables() throws Exception {
            stubChildSession(childSessionId, parentSessionId);

            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            AgentContextManager.AgentSessionContext pCtx = agentContextManager.get(parentSessionId);
            pCtx.context().putConversationVariable("toBeRemoved", "val");

            childCtx.context().removeConversationVariable("toBeRemoved");

            java.lang.reflect.Field localVars = AgentExecutionContext.class.getDeclaredField("conversationVariables");
            localVars.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, String> childLocal = (Map<String, String>) localVars.get(childCtx.context());
            assertFalse(childLocal.containsKey("toBeRemoved"), "子会话的本地conversationVariables不应包含已删除的key");
        }
    }

    @Nested
    class ConversationIdTest {

        @Test
        void 正向_mutator_setConversationId更新context的conversationId() {
            stubBasicContext();
            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();

            assertNull(ctx.context().getConversationId());

            ctx.mutator().setConversationId("conv-100");

            assertEquals("conv-100", ctx.context().getConversationId());
        }

        @Test
        void 正向_messageSender非null时setConversationId发送ConversationIdMessage() {
            stubBasicContext();
            registry.setMessageSender(messageSender);
            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();

            ctx.mutator().setConversationId("conv-200");

            verify(messageSender).send(argThat(msg -> msg instanceof ConversationIdMessage
                    && "conv-200".equals(((ConversationIdMessage) msg).getConversationId())
                    && sessionId.equals(((ConversationIdMessage) msg).getSessionId())));
        }

        @Test
        void 反向_messageSender为null时setConversationId静默不抛异常() {
            stubBasicContext();
            registry.setMessageSender(null);
            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();

            assertDoesNotThrow(() -> ctx.mutator().setConversationId("conv-300"));

            assertEquals("conv-300", ctx.context().getConversationId());
        }

        @Test
        void 正向_handleConversationIdMessage更新缓存中对应上下文的conversationId() {
            stubBasicContext();
            registry.setMessageSender(null);
            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();

            agentContextManager.handleConversationIdMessage(new ConversationIdMessage(sessionId, "conv-400"));

            assertEquals("conv-400", ctx.context().getConversationId());
        }

        @Test
        void 正向_refreshConversationId更新context的conversationId不发送消息() {
            stubBasicContext();
            registry.setMessageSender(messageSender);
            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();

            ctx.mutator().refreshConversationId("conv-refresh");

            assertEquals("conv-refresh", ctx.context().getConversationId());
            verify(messageSender, never()).send(any());
        }

        @Test
        void 边界_缓存中无该sessionId时handleConversationIdMessage静默不抛异常() {
            String nonExistentSession = "999";

            assertDoesNotThrow(() -> agentContextManager.handleConversationIdMessage(
                    new ConversationIdMessage(nonExistentSession, "conv-500")));
        }
    }

    @Nested
    class SendUserMessageSendTest {

        @Test
        void 正向_messageSender非空时sendUserMessage发送SendUserMessage() {
            stubBasicContext();
            registry.setMessageSender(messageSender);
            SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
            when(sessionManager.messageSave()).thenReturn(msgBuilder);
            when(dataProvider.loadAgentContext("child-1")).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child", "200", 10, List.of(), Map.of(), sessionId, null, null, null));

            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();
            ctx.context().sendUserMessage("child-1", "hello", "300", true);

            verify(messageSender).send(argThat(msg -> msg instanceof SendUserMessage
                    && "child-1".equals(((SendUserMessage) msg).getSessionId())
                    && "hello".equals(((SendUserMessage) msg).getContent())
                    && List.of(sessionId).equals(((SendUserMessage) msg).getParentSessionIds())));
        }

        @Test
        void 正向_parentSessionIds构建完整父链从直接父到主会话() {
            String grandchild = "grandchild";
            String level2 = "level2";
            String level1 = "level1";
            String main = "main";
            when(dataProvider.loadAgentContext(grandchild)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "g", "200", 10, List.of(), Map.of(), level2, null, null, null));
            when(dataProvider.loadAgentContext(level2)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "l2", "200", 10, List.of(), Map.of(), level1, null, null, null));
            when(dataProvider.loadAgentContext(level1)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "l1", "200", 10, List.of(), Map.of(), main, null, null, null));
            when(dataProvider.loadAgentContext(main)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "m", "200", 10, List.of(), Map.of(), null, null, null, null));
            when(sessionManager.getMessages(anyString())).thenReturn(List.of());
            when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());

            registry.setMessageSender(messageSender);
            SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
            when(sessionManager.messageSave()).thenReturn(msgBuilder);

            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(level2).build();
            ctx.context().sendUserMessage(grandchild, "hello", "300", true);

            verify(messageSender).send(argThat(msg -> msg instanceof SendUserMessage
                    && grandchild.equals(((SendUserMessage) msg).getSessionId())
                    && List.of(level2, level1, main).equals(((SendUserMessage) msg).getParentSessionIds())));
        }

        @Test
        void 反向_messageSender为null时不发送且保存逻辑不受影响() {
            stubBasicContext();
            registry.setMessageSender(null);
            SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
            when(sessionManager.messageSave()).thenReturn(msgBuilder);

            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();
            ctx.context().sendUserMessage("child-1", "hello", "300", true);

            verify(msgBuilder).sessionId("child-1");
            verify(msgBuilder).role("user");
            verify(msgBuilder).content("hello");
            verify(msgBuilder).userInput(false);
            verify(msgBuilder).save();
            verify(messageSender, never()).send(any());
        }

        @Test
        void 反向_SendUserMessage发送异常不影响原有消息保存逻辑() {
            stubBasicContext();
            registry.setMessageSender(messageSender);
            doThrow(new RuntimeException("send failed"))
                    .when(messageSender).send(argThat(msg -> msg instanceof SendUserMessage));
            SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
            when(sessionManager.messageSave()).thenReturn(msgBuilder);

            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();

            assertDoesNotThrow(() -> ctx.context().sendUserMessage("child-1", "hello", "300", true));

            verify(msgBuilder).sessionId("child-1");
            verify(msgBuilder).role("user");
            verify(msgBuilder).content("hello");
            verify(msgBuilder).userInput(false);
            verify(msgBuilder).save();
        }
    }

    @Nested
    class SendParentMessageSendTest {

        private final String parentSessionId = "1";
        private final String childSessionId = "2";

        private AgentContextManager.AgentSessionContext buildParentChild() {
            when(dataProvider.loadAgentContext(parentSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "parent prompt", "200", 10, List.of(), Map.of(), null, null, null, null));
            when(sessionManager.getMessages(anyString())).thenReturn(List.of());
            when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());
            agentContextManager.build(parentSessionId).build();
            when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), parentSessionId, null, null, null));
            return agentContextManager.build(childSessionId).build();
        }

        @Test
        void 正向_sendParentMessage保存到父会话并发送SendUserMessage() {
            registry.setMessageSender(messageSender);
            SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
            when(sessionManager.messageSave()).thenReturn(msgBuilder);

            AgentContextManager.AgentSessionContext childCtx = buildParentChild();
            childCtx.context().sendParentMessage("hello from child");

            verify(msgBuilder).sessionId(parentSessionId);
            verify(msgBuilder).role("user");
            verify(msgBuilder).content("hello from child");
            verify(msgBuilder).userInput(false);
            verify(msgBuilder).conversationId(null);
            verify(msgBuilder).save();

            verify(messageSender).send(argThat(msg -> msg instanceof SendUserMessage
                    && parentSessionId.equals(((SendUserMessage) msg).getSessionId())
                    && "hello from child".equals(((SendUserMessage) msg).getContent())));
        }

        @Test
        void 正向_conversationId调用时动态获取父会话最新值() {
            registry.setMessageSender(null);
            SessionManager.MessageSaveBuilder msgBuilder = mock(SessionManager.MessageSaveBuilder.class, RETURNS_SELF);
            when(sessionManager.messageSave()).thenReturn(msgBuilder);

            when(dataProvider.loadAgentContext(parentSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "parent prompt", "200", 10, List.of(), Map.of(), null, null, null, null));
            when(sessionManager.getMessages(anyString())).thenReturn(List.of());
            when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());

            AgentContextManager.AgentSessionContext parentCtx = agentContextManager.build(parentSessionId).build();
            when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), parentSessionId, null, null, null));
            AgentContextManager.AgentSessionContext childCtx = agentContextManager.build(childSessionId).build();

            parentCtx.mutator().setConversationId("conv-100");
            childCtx.context().sendParentMessage("first");
            verify(msgBuilder).conversationId("conv-100");

            parentCtx.mutator().setConversationId("conv-200");
            childCtx.context().sendParentMessage("second");
            verify(msgBuilder).conversationId("conv-200");
        }

        @Test
        void 反向_主会话sendParentMessage静默忽略() {
            stubBasicContext();
            registry.setMessageSender(messageSender);

            AgentContextManager.AgentSessionContext ctx = agentContextManager.build(sessionId).build();
            ctx.context().sendParentMessage("hello");

            verify(messageSender, never()).send(any());
            verify(sessionManager, never()).messageSave();
        }

        @Test
        void 反向_sendParentMessageCallback为null时静默忽略() throws Exception {
            registry.setMessageSender(messageSender);

            AgentContextManager.AgentSessionContext childCtx = buildParentChild();
            java.lang.reflect.Field f =
                    AgentExecutionContext.AgentContextMutator.class.getDeclaredField("sendParentMessageCallback");
            f.setAccessible(true);
            f.set(childCtx.mutator(), null);

            assertDoesNotThrow(() -> childCtx.context().sendParentMessage("hello"));

            verify(messageSender, never()).send(any());
            verify(sessionManager, never()).messageSave();
        }
    }

    @Nested
    class HandleChildCreateSessionTest {

        private final AgentExecutionContext.ChildSession child =
                new AgentExecutionContext.ChildSession("10", "sub-agent", "desc", "300");

        @BeforeEach
        void setUp() {
            stubBasicContext();
            // 懒构建：build 仅轻量构建，需访问 context() 触发完整构建，保证 setUp 中的 getMessages/getSessionTools stub 生效
            agentContextManager.build(sessionId).build().context();
        }

        @Test
        void 正向_从父会话链首元素解析父会话ID并更新其子会话列表() {
            when(dataProvider.loadAgentContext("level2")).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "level2", "200", 10, List.of(), Map.of(), "level1", null, null, null));
            when(dataProvider.loadAgentContext("level1")).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "level1", "200", 10, List.of(), Map.of(), null, null, null, null));
            when(sessionManager.getMessages(anyString())).thenReturn(List.of());
            when(toolManager.getSessionTools(anyString(), anyBoolean())).thenReturn(List.of());
            agentContextManager.build("level2").build();

            ChildCreateSession message = new ChildCreateSession("level2", child);
            message.setParentSessionIds(List.of("level2", "level1", "main"));

            agentContextManager.handleChildCreateSession(message);

            AgentExecutionContext context = agentContextManager.get("level2").context();
            assertEquals(1, context.getChildSessions().size());
            assertEquals("10", context.getChildSessions().get(0).sessionId());
            assertEquals("sub-agent", context.getChildSessions().get(0).sessionName());
        }

        @Test
        void 正向_parentSessionIds为null时回退使用sessionId解析() {
            ChildCreateSession message = new ChildCreateSession(sessionId, child);
            message.setParentSessionIds(null);

            agentContextManager.handleChildCreateSession(message);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals(1, context.getChildSessions().size());
            assertEquals("10", context.getChildSessions().get(0).sessionId());
        }

        @Test
        void 正向_parentSessionIds为空列表时回退使用sessionId解析() {
            ChildCreateSession message = new ChildCreateSession(sessionId, child);
            message.setParentSessionIds(List.of());

            agentContextManager.handleChildCreateSession(message);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals(1, context.getChildSessions().size());
            assertEquals("10", context.getChildSessions().get(0).sessionId());
        }

        @Test
        void 反向_父会话链首元素对应上下文不在缓存中时不抛异常() {
            ChildCreateSession message = new ChildCreateSession("not-cached", child);
            message.setParentSessionIds(List.of("not-cached", "main"));

            assertDoesNotThrow(() -> agentContextManager.handleChildCreateSession(message));
        }

        @Test
        void 正向_多次handle追加到子会话列表() {
            ChildCreateSession msg1 = new ChildCreateSession(sessionId, child);
            ChildCreateSession msg2 = new ChildCreateSession(sessionId,
                    new AgentExecutionContext.ChildSession("11", "sub2", "desc2", "301"));

            agentContextManager.handleChildCreateSession(msg1);
            agentContextManager.handleChildCreateSession(msg2);

            AgentExecutionContext context = agentContextManager.get(sessionId).context();
            assertEquals(2, context.getChildSessions().size());
            assertEquals("10", context.getChildSessions().get(0).sessionId());
            assertEquals("11", context.getChildSessions().get(1).sessionId());
        }
    }

    @Nested
    class RemoveChildSessionTest {

        private final String parentSessionId = "1";
        private final String childSessionId = "2";

        /**
         * 构建并缓存父会话上下文（懒构建触发完整构建），childSessions 初始包含 childSessionId("2") 与 "3" 两个子会话。
         */
        private AgentContextManager.AgentSessionContext buildParentWithChildren() {
            when(dataProvider.loadAgentContext(parentSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "parent prompt", "200", 10, List.of(), Map.of(), null,
                            List.of(new AgentExecutionContext.ChildSession(childSessionId, "child1", "desc1", "300"),
                                    new AgentExecutionContext.ChildSession("3", "child2", "desc2", "300")), null, null));
            when(sessionManager.getMessages(parentSessionId)).thenReturn(List.of());
            when(toolManager.getSessionTools(eq(parentSessionId), anyBoolean())).thenReturn(List.of());
            AgentContextManager.AgentSessionContext parentCtx = agentContextManager.build(parentSessionId).build();
            // 触发懒构建，使 childSessions 共享引用进入 context
            parentCtx.context();
            return parentCtx;
        }

        private void stubChild(String childId, String parentId) {
            when(dataProvider.loadAgentContext(childId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), parentId, null, null, null));
        }

        @Test
        void 正向_软删子会话移除后父会话childSessions过滤掉该子会话() {
            AgentContextManager.AgentSessionContext parentCtx = buildParentWithChildren();
            // 第一次调用返回活跃数据（子会话入缓存），第二次返回 null（模拟软删后 loadAgentContext 查不到）
            when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), parentSessionId, null, null, null),
                    null);
            agentContextManager.get(childSessionId); // 子会话入缓存
            assertEquals(2, parentCtx.context().getChildSessions().size());

            agentContextManager.remove(childSessionId);

            List<AgentExecutionContext.ChildSession> children = parentCtx.context().getChildSessions();
            assertEquals(1, children.size());
            assertEquals("3", children.get(0).sessionId());
        }

        @Test
        void 反向_活跃子会话remove不剪除父会话childSessions() {
            AgentContextManager.AgentSessionContext parentCtx = buildParentWithChildren();
            stubChild(childSessionId, parentSessionId);
            agentContextManager.get(childSessionId); // 子会话入缓存
            assertEquals(2, parentCtx.context().getChildSessions().size());

            agentContextManager.remove(childSessionId);

            assertEquals(2, parentCtx.context().getChildSessions().size(),
                    "活跃子会话（rollback/普通驱逐）不应被从父缓存列表剪除");
        }

        @Test
        void 正向_软删子会话移除后父会话为轻量条目不触发懒构建() {
            // 父会话仅轻量构建（不访问 context()），childSessions 存于 agentContextData
            when(dataProvider.loadAgentContext(parentSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "parent prompt", "200", 10, List.of(), Map.of(), null,
                            List.of(new AgentExecutionContext.ChildSession(childSessionId, "child1", "desc1", "300"),
                                    new AgentExecutionContext.ChildSession("3", "child2", "desc2", "300")), null, null));
            agentContextManager.build(parentSessionId).build();
            when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), parentSessionId, null, null, null),
                    null); // 第一次入缓存，第二次模拟软删
            agentContextManager.get(childSessionId);

            agentContextManager.remove(childSessionId);

            // 剪枝直接操作 agentContextData 共享引用，未触发父上下文懒构建
            verify(sessionManager, never()).getMessages(parentSessionId);
            verify(toolManager, never()).getSessionTools(eq(parentSessionId), anyBoolean());
            ContextDataProvider.AgentContextData parentData = agentContextManager.get(parentSessionId).agentContextData();
            assertEquals(1, parentData.childSessions().size());
            assertEquals("3", parentData.childSessions().get(0).sessionId());
        }

        @Test
        void 正向_移除后子会话缓存条目被清除() {
            buildParentWithChildren();
            stubChild(childSessionId, parentSessionId);
            AgentContextManager.AgentSessionContext childCtx = agentContextManager.get(childSessionId);
            assertNotNull(childCtx, "get 未命中时应轻量构建并缓存子会话");

            agentContextManager.remove(childSessionId);

            assertNotSame(childCtx, agentContextManager.get(childSessionId),
                    "移除后再次 get 应重建新条目而非复用旧缓存");
        }

        @Test
        void 边界_父会话不在缓存中时移除软删子会话不抛异常() {
            when(dataProvider.loadAgentContext(childSessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), "not-cached-parent", null, null, null),
                    null); // 第一次入缓存，第二次模拟软删
            agentContextManager.get(childSessionId);

            assertDoesNotThrow(() -> agentContextManager.remove(childSessionId));
        }

        @Test
        void 边界_软删子会话不在父会话列表中时列表保持不变() {
            AgentContextManager.AgentSessionContext parentCtx = buildParentWithChildren();
            when(dataProvider.loadAgentContext("99")).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "child prompt", "200", 10, List.of(), Map.of(), parentSessionId, null, null, null),
                    null); // 第一次入缓存，第二次模拟软删
            agentContextManager.get("99");

            agentContextManager.remove("99");

            List<AgentExecutionContext.ChildSession> children = parentCtx.context().getChildSessions();
            assertEquals(2, children.size(), "不在列表中的子会话移除后列表保持不变");
            assertEquals(childSessionId, children.get(0).sessionId());
            assertEquals("3", children.get(1).sessionId());
        }

        @Test
        void 边界_移除缓存命中的主会话不触发DB查询且正常清除缓存() {
            AgentContextManager.AgentSessionContext parentCtxBefore = buildParentWithChildren();

            agentContextManager.remove(parentSessionId);

            // 主会话缓存命中且 parentSessionId 为 null：remove 不应再触发 loadAgentContext（build 时仅 1 次）
            verify(dataProvider, times(1)).loadAgentContext(parentSessionId);
            assertNotSame(parentCtxBefore, agentContextManager.get(parentSessionId),
                    "主会话移除后缓存条目应被清除");
        }

        @Test
        void 边界_缓存未命中的会话remove不触发DB查询() {
            String neverCached = "never-cached-999";

            agentContextManager.remove(neverCached);

            // 缓存未命中时无法剪枝（拿不到父 ID），不应触发 loadAgentContext 查询
            verify(dataProvider, never()).loadAgentContext(neverCached);
        }
    }

    @Nested
    class LightweightAndLazyBuildTest {

        @Test
        void 防御性拷贝_构建后集合为独立可变副本() {
            SkillConfigDTO skill = SkillConfigDTO.builder().name("s1").build();
            var child = new AgentExecutionContext.ChildSession("10", "c", "d", "300");
            when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "p", "200", 10,
                            List.of(skill), Map.of("k1", "v1"), null, List.of(child), null, null));

            AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();

            ContextDataProvider.AgentContextData copied = sessionContext.agentContextData();
            assertNotNull(copied);
            assertNotSame(Map.of("k1", "v1"), copied.sessionVariables(), "sessionVariables 应为独立副本");
            // sessionVariables 为可变副本
            copied.sessionVariables().put("k2", "v2");
            assertEquals("v2", copied.sessionVariables().get("k2"));
            // skills 为可变副本（数据源 List.of 不可变）
            copied.skills().add(SkillConfigDTO.builder().name("s2").build());
            assertEquals(2, copied.skills().size());
            // childSessions 为可变副本
            copied.childSessions().add(new AgentExecutionContext.ChildSession("11", "c2", "d2", "301"));
            assertEquals(2, copied.childSessions().size());
        }

        @Test
        void 懒构建_context首次访问才构建且仅构建一次() {
            stubBasicContext();
            AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();

            // build（轻量构建）后未访问 context：不触发懒构建，getMessages/getSessionTools 均不应被调用
            verify(sessionManager, never()).getMessages(anyString());
            verify(toolManager, never()).getSessionTools(anyString(), anyBoolean());

            AgentExecutionContext first = sessionContext.context();
            assertNotNull(first);
            verify(sessionManager, times(1)).getMessages(sessionId);
            verify(toolManager, times(1)).getSessionTools(eq(sessionId), anyBoolean());

            // 第二次访问返回同一实例，不再重复构建
            AgentExecutionContext second = sessionContext.context();
            assertSame(first, second);
            verify(sessionManager, times(1)).getMessages(sessionId);
            verify(toolManager, times(1)).getSessionTools(eq(sessionId), anyBoolean());
        }

        @Test
        void get未命中轻量加载_不构建context也能拿到AgentContextData() {
            stubBasicContext();

            AgentContextManager.AgentSessionContext ctx = agentContextManager.get(sessionId);

            assertNotNull(ctx, "get 缓存未命中时应轻量构建并返回，不再返回 null");
            assertNotNull(ctx.agentContextData(), "不构建 context 也能拿到 AgentContextData");
            assertEquals(agentId, ctx.agentContextData().agentId());
            // 未触发懒构建：getMessages/getSessionTools 不应被调用
            verify(sessionManager, never()).getMessages(anyString());
            verify(toolManager, never()).getSessionTools(anyString(), anyBoolean());

            // 首次 context() 访问时才懒构建
            assertNotNull(ctx.context());
            verify(sessionManager, times(1)).getMessages(sessionId);
        }

        @Test
        void 共享引用一致性_mutator更新后AgentContextData与context同步() {
            Map<String, String> sourceVars = new HashMap<>();
            sourceVars.put("k1", "v1");
            var child = new AgentExecutionContext.ChildSession("10", "c", "d", "300");
            when(dataProvider.loadAgentContext(sessionId)).thenReturn(
                    new ContextDataProvider.AgentContextData(agentId, "p", "200", 10,
                            List.of(), sourceVars, null, List.of(child), null, null));
            when(sessionManager.getMessages(sessionId)).thenReturn(List.of());
            when(toolManager.getSessionTools(eq(sessionId), anyBoolean())).thenReturn(List.of());

            AgentContextManager.AgentSessionContext sessionContext = agentContextManager.build(sessionId).build();
            ContextDataProvider.AgentContextData data = sessionContext.agentContextData();
            AgentExecutionContext context = sessionContext.context();

            // skills 共享同一列表引用（不再重复拷贝）
            assertSame(data.skills(), context.getSkills(), "skills 应共享 AgentContextData 中的引用");

            // sessionVariables 共享引用：context.putSessionVariable 后 agentContextData 同步
            context.putSessionVariable("k2", "v2");
            assertEquals("v2", data.sessionVariables().get("k2"));

            // refreshSessionVariables 原地更新后 agentContextData 同步
            Map<String, String> latest = new HashMap<>();
            latest.put("n1", "nv1");
            sessionContext.mutator().refreshSessionVariables(latest);
            assertEquals("nv1", data.sessionVariables().get("n1"));
            assertFalse(data.sessionVariables().containsKey("k1"), "refresh 原地更新应清空旧值");

            // childSessions 共享引用：mutator.refreshChildSessions 后 agentContextData 同步
            var child2 = new AgentExecutionContext.ChildSession("11", "c2", "d2", "301");
            sessionContext.mutator().refreshChildSessions(List.of(child, child2));
            assertEquals(2, data.childSessions().size());
            assertEquals("11", data.childSessions().get(1).sessionId());
        }

        @Test
        void build带modelIdOverride_命中get轻量缓存条目时重建并生效() {
            stubBasicContext();
            // get() 缓存未命中先写入无 override 的轻量条目（模拟 AgentContextController/ToolManager 先访问）
            AgentContextManager.AgentSessionContext light = agentContextManager.get(sessionId);
            assertNotNull(light, "get 应轻量构建并缓存");

            // build 带 override：命中无 override 条目时应重建，使请求指定的 modelId 生效
            AgentContextManager.AgentSessionContext ctx =
                    agentContextManager.build(sessionId).modelIdOverride("override-300").build();

            assertNotSame(light, ctx, "override 不一致时应重建而非复用");
            assertEquals("override-300", ctx.context().getModelId(), "请求指定的 modelId 应生效而非回退默认模型");
            assertSame(ctx, agentContextManager.get(sessionId), "缓存应已被带 override 的条目替换");
        }

        @Test
        void build带modelIdOverride_缓存一致时复用不重建() {
            stubBasicContext();
            AgentContextManager.AgentSessionContext first =
                    agentContextManager.build(sessionId).modelIdOverride("m1").build();
            assertEquals("m1", first.context().getModelId());

            AgentContextManager.AgentSessionContext second =
                    agentContextManager.build(sessionId).modelIdOverride("m1").build();

            assertSame(first, second, "override 一致时应复用缓存");
            verify(sessionManager, times(1)).getMessages(sessionId);
        }

        @Test
        void build不带override_命中带override缓存条目时复用不重建() {
            stubBasicContext();
            AgentContextManager.AgentSessionContext first =
                    agentContextManager.build(sessionId).modelIdOverride("m1").build();
            assertEquals("m1", first.context().getModelId());
            // 模拟主会话首轮设置对话 ID（工具续接等未显式指定模型的请求必须沿用该对话 ID）
            first.mutator().setConversationId("conv-100");

            AgentContextManager.AgentSessionContext second = agentContextManager.build(sessionId).build();

            assertSame(first, second, "未显式指定模型（modelIdOverride 为 null）时应直接复用缓存条目，不触发重建");
            assertEquals("m1", second.context().getModelId(), "复用后沿用缓存条目的 modelId，不回退会话默认模型");
            assertEquals("conv-100", second.context().getConversationId(), "conversationId 应保持不变（未重建上下文）");
            verify(sessionManager, times(1)).getMessages(sessionId);
        }
    }
}
