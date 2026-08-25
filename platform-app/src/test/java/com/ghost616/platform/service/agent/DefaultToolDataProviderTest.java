package com.ghost616.platform.service.agent;

import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.RequestType;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.enums.ToolType;
import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.agentbase.service.agent.invoker.CustomToolInvoker;
import com.ghost616.agentbase.service.agent.ToolDataProvider.SessionToolInfo;
import com.ghost616.agentinteg.history.HistoryMessageQueryProvider;
import com.ghost616.agentinteg.knowledge.KnowledgeBaseQueryProvider;
import com.ghost616.agentinteg.knowledge.SearchType;
import com.ghost616.agentinteg.knowledge.TextChunkWithFile;
import com.ghost616.agentinteg.model.PlatformType;
import com.ghost616.agentinteg.memory.MemoryQueryProvider;
import com.ghost616.agentinteg.tool.BrowserToolInvoker;
import com.ghost616.agentinteg.tool.BrowserToolProvider;
import com.ghost616.agentinteg.tool.HistoryQueryTool;
import com.ghost616.agentinteg.tool.KnowledgeBaseInfoTool;
import com.ghost616.agentinteg.tool.KnowledgeFileChunkTool;
import com.ghost616.agentinteg.tool.KnowledgeFileInfoTool;
import com.ghost616.agentinteg.tool.KnowledgeSearchTool;
import com.ghost616.agentinteg.tool.MemoryQueryTool;
import com.ghost616.agentinteg.tool.SendResultToParentTool;
import com.ghost616.agentinteg.tool.SubSessionCallbackTool;
import com.ghost616.platform.dto.tool.ToolDetailDTO;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.AgentKnowledgeBase;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.enums.SubToolType;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.AgentKnowledgeBaseMapper;
import com.ghost616.platform.repository.AgentSkillMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.repository.SkillToolMapper;
import com.ghost616.platform.service.tool.ToolConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultToolDataProviderTest {

    @Mock private SessionToolMapper sessionToolMapper;
    @Mock private SessionMapper sessionMapper;
    @Mock private AgentSkillMapper agentSkillMapper;
    @Mock private SkillToolMapper skillToolMapper;
    @Mock private SessionSkillMapper sessionSkillMapper;
    @Mock private ToolConfigService toolConfigService;
    @Mock private BrowserToolProvider browserToolCallback;
    @Mock private ModelConfigMapper modelConfigMapper;
    @Mock private KnowledgeBaseQueryProvider knowledgeBaseQueryProvider;
    @Mock private ObjectProvider<KnowledgeBaseQueryProvider> knowledgeBaseQueryProviderProvider;
    @Mock private AgentKnowledgeBaseMapper agentKnowledgeBaseMapper;
    @Mock private AgentConfigMapper agentConfigMapper;
    @Mock private MemoryQueryProvider memoryQueryProvider;
    @Mock private ObjectProvider<MemoryQueryProvider> memoryQueryProviderProvider;
    @Mock private HistoryMessageQueryProvider historyMessageQueryProvider;
    @Mock private ObjectProvider<HistoryMessageQueryProvider> historyMessageQueryProviderProvider;
    @Mock private DefaultSubSessionCallback defaultSubSessionCallback;
    @Mock private SubSessionWebSocketModeResolver subSessionWebSocketModeResolver;
    @Mock private AgentContextManager agentContextManager;
    @Mock private ObjectProvider<AgentContextManager> agentContextManagerProvider;

    private DefaultToolDataProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultToolDataProvider(sessionToolMapper, sessionMapper,
                agentSkillMapper, skillToolMapper, sessionSkillMapper, toolConfigService,
                browserToolCallback, modelConfigMapper, knowledgeBaseQueryProviderProvider,
                agentKnowledgeBaseMapper, agentConfigMapper, memoryQueryProviderProvider,
                historyMessageQueryProviderProvider, defaultSubSessionCallback,
                subSessionWebSocketModeResolver, agentContextManagerProvider);
    }

    private SessionTool createSessionTool(Long toolId, SessionAuthType auth) {
        SessionTool st = new SessionTool();
        st.setToolId(toolId);
        st.setSessionAuth(auth);
        return st;
    }

    private ModelConfig createModelConfig(PlatformType platformType, String requestType) {
        ModelConfig mc = new ModelConfig();
        mc.setPlatformType(platformType);
        mc.setRequestType(requestType);
        return mc;
    }

    private Session createSession(Long agentId) {
        Session s = new Session();
        s.setAgentId(agentId);
        return s;
    }

    private AgentKnowledgeBase createBinding(Long agentId) {
        AgentKnowledgeBase b = new AgentKnowledgeBase();
        b.setAgentId(agentId);
        return b;
    }

    private AgentConfig createAgentConfig(Boolean memoryEnabled) {
        AgentConfig c = new AgentConfig();
        c.setMemoryEnabled(memoryEnabled);
        return c;
    }

    @Nested
    @DisplayName("getSessionToolIds")
    class GetSessionToolIdsTest {

        @Test
        @DisplayName("返回 List<SessionToolInfo>，包含 toolId 和 sessionAuth")
        void shouldReturnSessionToolInfoList() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of(
                    createSessionTool(100L, SessionAuthType.CHILD),
                    createSessionTool(101L, SessionAuthType.PARENT)));

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(3, result.size());
            assertEquals("100", result.get(0).toolId());
            assertEquals(SessionAuthType.CHILD, result.get(0).sessionAuth());
            assertEquals("101", result.get(1).toolId());
            assertEquals(SessionAuthType.PARENT, result.get(1).sessionAuth());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.get(2).toolId());
            assertEquals(SessionAuthType.ALL, result.get(2).sessionAuth());
        }

        @Test
        @DisplayName("sessionAuth 为 null 时默认 ALL")
        void nullAuth_shouldDefaultToAll() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of(
                    createSessionTool(200L, null)));

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(2, result.size());
            assertEquals("200", result.get(0).toolId());
            assertEquals(SessionAuthType.ALL, result.get(0).sessionAuth());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.get(1).toolId());
            assertEquals(SessionAuthType.ALL, result.get(1).sessionAuth());
        }

        @Test
        @DisplayName("无关联工具时仅返回子会话回调工具")
        void emptyTools_shouldOnlyContainSubSessionTool() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(1, result.size());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.get(0).toolId());
            assertEquals(SessionAuthType.ALL, result.get(0).sessionAuth());
        }

        @Test
        @DisplayName("session 有知识库绑定时返回含 4 个知识库工具的列表")
        void sessionWithKnowledgeBinding_shouldIncludeKnowledgeTools() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(sessionMapper.selectById(1L)).thenReturn(createSession(10L));
            when(agentKnowledgeBaseMapper.selectList(any()))
                    .thenReturn(List.of(createBinding(10L)));

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(5, result.size());
            assertTrue(result.stream().anyMatch(i -> "default_tool_rag_info".equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> "default_tool_rag_file_info".equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> "default_tool_rag_search".equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> "default_tool_rag_file_chunk".equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> SubSessionCallbackTool.TOOL_NAME.equals(i.toolId())));
            assertTrue(result.stream().allMatch(i -> SessionAuthType.ALL == i.sessionAuth()));
        }

        @Test
        @DisplayName("session 无知识库绑定时列表中不含知识库工具")
        void sessionWithoutKnowledgeBinding_shouldNotIncludeKnowledgeTools() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(sessionMapper.selectById(1L)).thenReturn(createSession(10L));
            when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(1, result.size());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.get(0).toolId());
            assertFalse(result.stream().anyMatch(i -> i.toolId().startsWith("default_tool_rag")));
        }

        @Test
        @DisplayName("session 对应 agent 的 memoryEnabled=true 时注入 default_tool_memory_search 与 default_tool_history_query 工具")
        void sessionWithMemoryEnabled_shouldIncludeMemorySearchTool() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(sessionMapper.selectById(1L)).thenReturn(createSession(10L));
            when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
            when(agentConfigMapper.selectById(10L)).thenReturn(createAgentConfig(true));

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(3, result.size());
            assertTrue(result.stream().anyMatch(i -> MemoryQueryTool.TOOL_NAME.equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> HistoryQueryTool.TOOL_NAME.equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> SubSessionCallbackTool.TOOL_NAME.equals(i.toolId())));
            assertTrue(result.stream().allMatch(i -> SessionAuthType.ALL == i.sessionAuth()));
        }

        @Test
        @DisplayName("session 对应 agent 的 memoryEnabled=false 时不注入记忆搜索与历史查询工具")
        void sessionWithMemoryDisabled_shouldNotIncludeMemorySearchTool() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(sessionMapper.selectById(1L)).thenReturn(createSession(10L));
            when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
            when(agentConfigMapper.selectById(10L)).thenReturn(createAgentConfig(false));

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(1, result.size());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.get(0).toolId());
            assertFalse(result.stream().anyMatch(i -> MemoryQueryTool.TOOL_NAME.equals(i.toolId())));
            assertFalse(result.stream().anyMatch(i -> HistoryQueryTool.TOOL_NAME.equals(i.toolId())));
        }

        @Test
        @DisplayName("session 有知识库绑定且 memoryEnabled=true 时同时注入知识库工具、记忆搜索与历史查询工具")
        void sessionWithKnowledgeAndMemory_shouldIncludeBothToolKinds() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(sessionMapper.selectById(1L)).thenReturn(createSession(10L));
            when(agentKnowledgeBaseMapper.selectList(any()))
                    .thenReturn(List.of(createBinding(10L)));
            when(agentConfigMapper.selectById(10L)).thenReturn(createAgentConfig(true));

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(7, result.size());
            assertTrue(result.stream().anyMatch(i -> MemoryQueryTool.TOOL_NAME.equals(i.toolId())));
            assertTrue(result.stream().anyMatch(i -> HistoryQueryTool.TOOL_NAME.equals(i.toolId())));
            assertTrue(result.stream().allMatch(i -> SessionAuthType.ALL == i.sessionAuth()));
        }

        @Test
        @DisplayName("WEBSOCKET 子会话时无条件注入 send_result_to_parent 工具（SessionAuthType.ALL）")
        void websocketSubSession_shouldIncludeSendResultToParentTool() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(subSessionWebSocketModeResolver.isWebSocketSubSession("1")).thenReturn(true);

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(2, result.size());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.get(0).toolId());
            assertEquals(SendResultToParentTool.TOOL_NAME, result.get(1).toolId());
            assertEquals(SessionAuthType.ALL, result.get(1).sessionAuth());
        }

        @Test
        @DisplayName("非 WEBSOCKET 子会话或主会话时不注入 send_result_to_parent 工具")
        void nonWebsocketSession_shouldNotIncludeSendResultToParentTool() {
            when(sessionToolMapper.selectList(any())).thenReturn(List.of());
            when(subSessionWebSocketModeResolver.isWebSocketSubSession("1")).thenReturn(false);

            List<SessionToolInfo> result = provider.getSessionToolIds("1");

            assertEquals(1, result.size());
            assertFalse(result.stream().anyMatch(i -> SendResultToParentTool.TOOL_NAME.equals(i.toolId())));
        }
    }

    @Nested
    @DisplayName("getToolById")
    class GetToolByIdTest {

        @Test
        @DisplayName("传入知识库工具名返回工具配置（id=工具名，toolType=CUSTOM，含完整 name/description/parameterSchema）")
        void knowledgeToolName_shouldReturnKnowledgeToolConfig() {
            String toolName = KnowledgeSearchTool.TOOL_NAME;
            ToolConfigDTO result = provider.getToolById(toolName);

            assertNotNull(result);
            assertEquals(toolName, result.getId());
            assertEquals(KnowledgeSearchTool.TOOL_NAME, result.getName());
            assertEquals(ToolType.CUSTOM, result.getToolType());
            assertNotNull(result.getDescription());
            assertFalse(result.getDescription().isBlank());
            assertNotNull(result.getParameterSchema());
            assertFalse(result.getParameterSchema().isBlank());
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("传入记忆搜索工具名返回 MemoryQueryTool 工具配置（id=工具名，toolType=CUSTOM）")
        void memoryToolName_shouldReturnMemoryToolConfig() {
            ToolConfigDTO result = provider.getToolById(MemoryQueryTool.TOOL_NAME);

            assertNotNull(result);
            assertEquals(MemoryQueryTool.TOOL_NAME, result.getId());
            assertEquals(MemoryQueryTool.TOOL_NAME, result.getName());
            assertEquals(ToolType.CUSTOM, result.getToolType());
            assertNotNull(result.getDescription());
            assertFalse(result.getDescription().isBlank());
            assertNotNull(result.getParameterSchema());
            assertFalse(result.getParameterSchema().isBlank());
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("传入历史查询工具名返回 HistoryQueryTool 工具配置（id=工具名，toolType=CUSTOM）")
        void historyToolName_shouldReturnHistoryToolConfig() {
            ToolConfigDTO result = provider.getToolById(HistoryQueryTool.TOOL_NAME);

            assertNotNull(result);
            assertEquals(HistoryQueryTool.TOOL_NAME, result.getId());
            assertEquals(HistoryQueryTool.TOOL_NAME, result.getName());
            assertEquals(ToolType.CUSTOM, result.getToolType());
            assertNotNull(result.getDescription());
            assertFalse(result.getDescription().isBlank());
            assertNotNull(result.getParameterSchema());
            assertFalse(result.getParameterSchema().isBlank());
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("传入子会话回调工具名返回 SubSessionCallbackTool 工具配置（id=工具名，toolType=CUSTOM）")
        void subSessionToolName_shouldReturnSubSessionCallbackToolConfig() {
            ToolConfigDTO result = provider.getToolById(SubSessionCallbackTool.TOOL_NAME);

            assertNotNull(result);
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.getId());
            assertEquals(SubSessionCallbackTool.TOOL_NAME, result.getName());
            assertEquals(ToolType.CUSTOM, result.getToolType());
            assertNotNull(result.getDescription());
            assertFalse(result.getDescription().isBlank());
            assertNotNull(result.getParameterSchema());
            assertFalse(result.getParameterSchema().isBlank());
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("传入 send_result_to_parent 工具名返回 SendResultToParentTool 工具配置（id=工具名，toolType=CUSTOM）")
        void sendResultToParentToolName_shouldReturnSendResultToParentToolConfig() {
            ToolConfigDTO result = provider.getToolById(SendResultToParentTool.TOOL_NAME);

            assertNotNull(result);
            assertEquals(SendResultToParentTool.TOOL_NAME, result.getId());
            assertEquals(SendResultToParentTool.TOOL_NAME, result.getName());
            assertEquals(ToolType.CUSTOM, result.getToolType());
            assertNotNull(result.getDescription());
            assertFalse(result.getDescription().isBlank());
            assertNotNull(result.getParameterSchema());
            assertFalse(result.getParameterSchema().isBlank());
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("普通工具 ID 走原逻辑查询")
        void normalToolId_shouldQueryByService() {
            when(toolConfigService.getById(200L)).thenReturn(ToolDetailDTO.builder()
                    .id("200")
                    .toolType(ToolType.JAVA)
                    .build());

            ToolConfigDTO result = provider.getToolById("200");

            assertEquals("200", result.getId());
            verify(toolConfigService).getById(200L);
        }
    }

    @Nested
    @DisplayName("getCustomInvoker")
    class GetCustomInvokerTest {

        private final Long toolId = 100L;

        private ToolConfigDTO createToolConfig(ToolType toolType) {
            return ToolConfigDTO.builder()
                    .id(String.valueOf(toolId))
                    .toolType(toolType)
                    .build();
        }

        private ToolConfigDTO createToolConfigWithName(ToolType toolType, String name) {
            return ToolConfigDTO.builder()
                    .id(String.valueOf(toolId))
                    .toolType(toolType)
                    .name(name)
                    .build();
        }

        private ToolDetailDTO createToolDetail(SubToolType subToolType) {
            return ToolDetailDTO.builder()
                    .id(String.valueOf(toolId))
                    .subToolType(subToolType)
                    .build();
        }

        @Test
        @DisplayName("CUSTOM + BROWSER 返回 BrowserToolInvoker")
        void customBrowser_shouldReturnBrowserToolInvoker() {
            ToolConfigDTO config = createToolConfig(ToolType.CUSTOM);
            ToolDetailDTO detail = createToolDetail(SubToolType.BROWSER);
            when(toolConfigService.getById(toolId)).thenReturn(detail);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(BrowserToolInvoker.class, result);
        }

        @Test
        @DisplayName("CUSTOM + BROWSER 返回的 Invoker 包含正确 toolConfig")
        void customBrowser_invokerShouldContainCorrectConfig() {
            ToolConfigDTO config = createToolConfig(ToolType.CUSTOM);
            ToolDetailDTO detail = createToolDetail(SubToolType.BROWSER);
            when(toolConfigService.getById(toolId)).thenReturn(detail);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertNotNull(result);
        }

        @Test
        @DisplayName("非 CUSTOM 类型抛出 UnsupportedOperationException")
        void nonCustom_shouldThrow() {
            ToolConfigDTO config = createToolConfig(ToolType.JAVA);
            ToolDetailDTO detail = createToolDetail(SubToolType.BROWSER);
            when(toolConfigService.getById(toolId)).thenReturn(detail);

            assertThrows(UnsupportedOperationException.class,
                    () -> provider.getCustomInvoker(config));
        }

        @Test
        @DisplayName("CUSTOM + 非 BROWSER 子类型抛出 UnsupportedOperationException")
        void customNonBrowser_shouldThrow() {
            ToolConfigDTO config = createToolConfig(ToolType.CUSTOM);
            ToolDetailDTO detail = createToolDetail(null);
            when(toolConfigService.getById(toolId)).thenReturn(detail);

            assertThrows(UnsupportedOperationException.class,
                    () -> provider.getCustomInvoker(config));
        }

        @Test
        @DisplayName("CUSTOM + RAG_KNOWLEDGE + default_tool_rag_info 返回 KnowledgeBaseInfoTool")
        void ragKnowledgeInfo_shouldReturnKnowledgeBaseInfoTool() {
            ToolConfigDTO config = createToolConfigWithName(ToolType.CUSTOM, KnowledgeBaseInfoTool.TOOL_NAME);
            ToolDetailDTO detail = createToolDetail(SubToolType.RAG_KNOWLEDGE);
            when(toolConfigService.getById(toolId)).thenReturn(detail);
            when(knowledgeBaseQueryProviderProvider.getObject()).thenReturn(knowledgeBaseQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(KnowledgeBaseInfoTool.class, result);
        }

        @Test
        @DisplayName("CUSTOM + RAG_KNOWLEDGE + default_tool_rag_file_info 返回 KnowledgeFileInfoTool")
        void ragKnowledgeFileInfo_shouldReturnKnowledgeFileInfoTool() {
            ToolConfigDTO config = createToolConfigWithName(ToolType.CUSTOM, KnowledgeFileInfoTool.TOOL_NAME);
            ToolDetailDTO detail = createToolDetail(SubToolType.RAG_KNOWLEDGE);
            when(toolConfigService.getById(toolId)).thenReturn(detail);
            when(knowledgeBaseQueryProviderProvider.getObject()).thenReturn(knowledgeBaseQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(KnowledgeFileInfoTool.class, result);
        }

        @Test
        @DisplayName("CUSTOM + RAG_KNOWLEDGE + default_tool_rag_search 返回 KnowledgeSearchTool")
        void ragKnowledgeSearch_shouldReturnKnowledgeSearchTool() {
            ToolConfigDTO config = createToolConfigWithName(ToolType.CUSTOM, KnowledgeSearchTool.TOOL_NAME);
            ToolDetailDTO detail = createToolDetail(SubToolType.RAG_KNOWLEDGE);
            when(toolConfigService.getById(toolId)).thenReturn(detail);
            when(knowledgeBaseQueryProviderProvider.getObject()).thenReturn(knowledgeBaseQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(KnowledgeSearchTool.class, result);
        }

        @Test
        @DisplayName("CUSTOM + RAG_KNOWLEDGE + default_tool_rag_file_chunk 返回 KnowledgeFileChunkTool")
        void ragKnowledgeFileChunk_shouldReturnKnowledgeFileChunkTool() {
            ToolConfigDTO config = createToolConfigWithName(ToolType.CUSTOM, KnowledgeFileChunkTool.TOOL_NAME);
            ToolDetailDTO detail = createToolDetail(SubToolType.RAG_KNOWLEDGE);
            when(toolConfigService.getById(toolId)).thenReturn(detail);
            when(knowledgeBaseQueryProviderProvider.getObject()).thenReturn(knowledgeBaseQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(KnowledgeFileChunkTool.class, result);
        }

        @Test
        @DisplayName("CUSTOM + RAG_KNOWLEDGE + 未支持的工具名抛出 UnsupportedOperationException")
        void ragKnowledgeUnsupportedName_shouldThrow() {
            ToolConfigDTO config = createToolConfigWithName(ToolType.CUSTOM, "unknown_rag_tool");
            ToolDetailDTO detail = createToolDetail(SubToolType.RAG_KNOWLEDGE);
            when(toolConfigService.getById(toolId)).thenReturn(detail);

            assertThrows(UnsupportedOperationException.class,
                    () -> provider.getCustomInvoker(config));
        }

        @Test
        @DisplayName("id 为知识库工具名且 toolType 为 CUSTOM 的配置直接创建知识库工具 invoker")
        void knowledgeToolId_shouldReturnKnowledgeToolInvokerWithoutGetById() {
            ToolConfigDTO config = ToolConfigDTO.builder()
                    .id(KnowledgeBaseInfoTool.TOOL_NAME)
                    .name(KnowledgeBaseInfoTool.TOOL_NAME)
                    .toolType(ToolType.CUSTOM)
                    .build();
            when(knowledgeBaseQueryProviderProvider.getObject()).thenReturn(knowledgeBaseQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(KnowledgeBaseInfoTool.class, result);
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("记忆搜索工具配置返回 MemoryQueryTool 实例")
        void memoryTool_shouldReturnMemoryQueryTool() {
            ToolConfigDTO config = ToolConfigDTO.builder()
                    .id(MemoryQueryTool.TOOL_NAME)
                    .name(MemoryQueryTool.TOOL_NAME)
                    .toolType(ToolType.CUSTOM)
                    .build();
            when(memoryQueryProviderProvider.getObject()).thenReturn(memoryQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(MemoryQueryTool.class, result);
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("历史查询工具配置返回 HistoryQueryTool 实例")
        void historyTool_shouldReturnHistoryQueryTool() {
            ToolConfigDTO config = ToolConfigDTO.builder()
                    .id(HistoryQueryTool.TOOL_NAME)
                    .name(HistoryQueryTool.TOOL_NAME)
                    .toolType(ToolType.CUSTOM)
                    .build();
            when(historyMessageQueryProviderProvider.getObject()).thenReturn(historyMessageQueryProvider);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(HistoryQueryTool.class, result);
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("子会话回调工具配置返回 SubSessionCallbackTool 实例")
        void subSessionTool_shouldReturnSubSessionCallbackTool() {
            ToolConfigDTO config = ToolConfigDTO.builder()
                    .id(SubSessionCallbackTool.TOOL_NAME)
                    .name(SubSessionCallbackTool.TOOL_NAME)
                    .toolType(ToolType.CUSTOM)
                    .build();
            when(agentContextManagerProvider.getObject()).thenReturn(agentContextManager);

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(SubSessionCallbackTool.class, result);
            verify(agentContextManagerProvider).getObject();
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("AgentContextManager 通过 ObjectProvider 注入，避免构造器循环依赖")
        void agentContextManager_shouldBeInjectedViaObjectProvider() throws Exception {
            // AgentContextManager → AgentAssembler → toolDataProvider 环为构造器注入环，
            // 直接字段注入会在 Spring Boot 3.2（allow-circular-references=false）启动时抛
            // BeanCurrentlyInCreationException；ObjectProvider 注入不实例化目标 Bean，按需获取打破环
            java.lang.reflect.Field field = DefaultToolDataProvider.class.getDeclaredField("agentContextManagerProvider");
            field.setAccessible(true);
            assertTrue(ObjectProvider.class.isAssignableFrom(field.getType()),
                    "agentContextManagerProvider 字段必须是 ObjectProvider 类型以解除构造器循环依赖");
        }

        @Test
        @DisplayName("send_result_to_parent 工具配置返回 SendResultToParentTool 实例")
        void sendResultToParentTool_shouldReturnSendResultToParentTool() {
            ToolConfigDTO config = ToolConfigDTO.builder()
                    .id(SendResultToParentTool.TOOL_NAME)
                    .name(SendResultToParentTool.TOOL_NAME)
                    .toolType(ToolType.CUSTOM)
                    .build();

            CustomToolInvoker result = provider.getCustomInvoker(config);

            assertInstanceOf(SendResultToParentTool.class, result);
            verify(toolConfigService, never()).getById(anyLong());
        }

        @Test
        @DisplayName("CUSTOM + RAG_KNOWLEDGE 知识库搜索工具执行时以 String ID 调用 Provider")
        void ragKnowledgeSearch_execute_shouldUseStringIds() {
            ToolConfigDTO config = createToolConfigWithName(ToolType.CUSTOM, KnowledgeSearchTool.TOOL_NAME);
            ToolDetailDTO detail = createToolDetail(SubToolType.RAG_KNOWLEDGE);
            when(toolConfigService.getById(toolId)).thenReturn(detail);
            when(knowledgeBaseQueryProviderProvider.getObject()).thenReturn(knowledgeBaseQueryProvider);
            when(knowledgeBaseQueryProvider.searchChunks("100", null, SearchType.VECTOR, "hello", 5))
                    .thenReturn(List.of(new TextChunkWithFile("100", "2", "a.txt",
                            List.of(new TextChunkWithFile.TextChunk(5, "line5")))));

            CustomToolInvoker invoker = provider.getCustomInvoker(config);

            assertInstanceOf(KnowledgeSearchTool.class, invoker);
            String result = invoker.execute(null, """
                    {
                      "knowledgeBaseId": "100",
                      "searchType": "VECTOR",
                      "query": "hello",
                      "searchLimit": 5
                    }
                    """);

            assertNotNull(result);
            verify(knowledgeBaseQueryProvider).searchChunks("100", null, SearchType.VECTOR, "hello", 5);
        }
    }

    @Nested
    @DisplayName("getBuiltinTools")
    class GetBuiltinToolsTest {

        @Test
        @DisplayName("OPENAI + responses 返回 web_search")
        void openaiResponses_shouldReturnWebSearch() {
            when(modelConfigMapper.selectById(1L))
                    .thenReturn(createModelConfig(PlatformType.OPENAI, RequestType.RESPONSES.getCode()));

            List<Map<String, Object>> result = provider.getBuiltinTools("1");

            assertEquals(1, result.size());
            assertEquals("web_search", result.get(0).get("type"));
        }

        @Test
        @DisplayName("DEEPSEEK + responses_stateless 返回 web_search")
        void deepseekResponsesStateless_shouldReturnWebSearch() {
            when(modelConfigMapper.selectById(2L))
                    .thenReturn(createModelConfig(PlatformType.DEEPSEEK, RequestType.RESPONSES_STATELESS.getCode()));

            List<Map<String, Object>> result = provider.getBuiltinTools("2");

            assertEquals(1, result.size());
            assertEquals("web_search", result.get(0).get("type"));
        }

        @Test
        @DisplayName("OPENAI + completions 返回空列表")
        void openaiCompletions_shouldReturnEmpty() {
            when(modelConfigMapper.selectById(1L))
                    .thenReturn(createModelConfig(PlatformType.OPENAI, RequestType.COMPLETIONS.getCode()));

            List<Map<String, Object>> result = provider.getBuiltinTools("1");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("ANTHROPIC + responses 返回空列表")
        void anthropicResponses_shouldReturnEmpty() {
            when(modelConfigMapper.selectById(3L))
                    .thenReturn(createModelConfig(PlatformType.ANTHROPIC, RequestType.RESPONSES.getCode()));

            List<Map<String, Object>> result = provider.getBuiltinTools("3");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("模型不存在时返回空列表")
        void modelNotFound_shouldReturnEmpty() {
            when(modelConfigMapper.selectById(99L)).thenReturn(null);

            List<Map<String, Object>> result = provider.getBuiltinTools("99");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("modelId 为 null 时返回空列表且不查询数据库")
        void nullModelId_shouldReturnEmpty() {
            List<Map<String, Object>> result = provider.getBuiltinTools(null);

            assertTrue(result.isEmpty());
            verify(modelConfigMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("KIMI + requestType 为 null 返回 builtin_function")
        void kimiNullRequestType_shouldReturnBuiltinFunction() {
            when(modelConfigMapper.selectById(4L))
                    .thenReturn(createModelConfig(PlatformType.KIMI, null));

            List<Map<String, Object>> result = provider.getBuiltinTools("4");

            assertEquals(1, result.size());
            assertEquals("builtin_function", result.get(0).get("type"));
            assertEquals("$web_search",
                    ((Map<?, ?>) result.get(0).get("function")).get("name"));
        }

        @Test
        @DisplayName("KIMI + requestType 为空字符串返回 builtin_function")
        void kimiEmptyRequestType_shouldReturnBuiltinFunction() {
            when(modelConfigMapper.selectById(4L))
                    .thenReturn(createModelConfig(PlatformType.KIMI, ""));

            List<Map<String, Object>> result = provider.getBuiltinTools("4");

            assertEquals(1, result.size());
            assertEquals("builtin_function", result.get(0).get("type"));
            assertEquals("$web_search",
                    ((Map<?, ?>) result.get(0).get("function")).get("name"));
        }

        @Test
        @DisplayName("KIMI + requestType 为 completions 返回 builtin_function")
        void kimiCompletions_shouldReturnBuiltinFunction() {
            when(modelConfigMapper.selectById(4L))
                    .thenReturn(createModelConfig(PlatformType.KIMI, RequestType.COMPLETIONS.getCode()));

            List<Map<String, Object>> result = provider.getBuiltinTools("4");

            assertEquals(1, result.size());
            assertEquals("builtin_function", result.get(0).get("type"));
            assertEquals("$web_search",
                    ((Map<?, ?>) result.get(0).get("function")).get("name"));
        }

        @Test
        @DisplayName("KIMI + responses 返回空列表")
        void kimiResponses_shouldReturnEmpty() {
            when(modelConfigMapper.selectById(4L))
                    .thenReturn(createModelConfig(PlatformType.KIMI, RequestType.RESPONSES.getCode()));

            List<Map<String, Object>> result = provider.getBuiltinTools("4");

            assertTrue(result.isEmpty());
        }
    }
}
