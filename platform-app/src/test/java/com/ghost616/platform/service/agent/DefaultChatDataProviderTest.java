package com.ghost616.platform.service.agent;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.CommonStatus;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.ToolDataProvider;
import com.ghost616.agentbase.service.agent.ToolDataProvider.SessionToolInfo;
import com.ghost616.agentbase.service.agent.invoker.HookInvoker;
import com.ghost616.agentinteg.model.PlatformType;
import com.ghost616.platform.entity.AgentSkill;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SkillConfig;
import com.ghost616.platform.event.AgentChangedEvent;
import com.ghost616.platform.repository.AgentSkillMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SkillConfigMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultChatDataProviderTest {

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SubSessionWebSocketModeResolver subSessionWebSocketModeResolver;

    @Mock
    private ToolDataProvider toolDataProvider;

    @Mock
    private AgentSkillMapper agentSkillMapper;

    @Mock
    private SkillConfigMapper skillConfigMapper;

    private DefaultChatDataProvider provider;

    @BeforeAll
    static void initTableInfo() {
        // 初始化 MyBatis-Plus TableInfo 缓存，使 LambdaQueryWrapper.getSqlSegment() 在纯单测环境可解析列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AgentSkill.class);
    }

    @BeforeEach
    void setUp() {
        provider = new DefaultChatDataProvider(modelConfigMapper, sessionMapper, applicationContext,
                subSessionWebSocketModeResolver, toolDataProvider, agentSkillMapper, skillConfigMapper);
    }

    private Session mainSession(Long id, Long agentId) {
        Session session = new Session();
        session.setId(id);
        session.setAgentId(agentId);
        session.setIsChild(false);
        return session;
    }

    private ToolConfigDTO toolDto(String id, String name, String description) {
        ToolConfigDTO dto = new ToolConfigDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setDescription(description);
        return dto;
    }

    private SkillConfig enabledSkill(Long id, String name, String description) {
        // SkillConfig.id 继承自 BaseEntity，Lombok @Builder 不含父类字段，需构建后 setId
        SkillConfig skillConfig = SkillConfig.builder()
                .name(name)
                .description(description)
                .status(CommonStatus.ENABLED)
                .build();
        skillConfig.setId(id);
        return skillConfig;
    }

    @Test
    void getModelConfig_实体存在_返回ModelConfigData() {
        ModelConfig entity = new ModelConfig();
        entity.setId(1L);
        entity.setApiKey("sk-test");
        entity.setBaseUrl("https://test.com");
        entity.setModelName("gpt-4");
        entity.setTemperature(0.7);
        entity.setMaxTokens(4096);
        entity.setPlatformType(PlatformType.OPENAI);
        when(modelConfigMapper.selectById(1L)).thenReturn(entity);

        ModelConfigData result = provider.getModelConfig("1");

        assertNotNull(result);
        assertEquals("1", result.id());
        assertEquals("sk-test", result.apiKey());
        assertEquals("https://test.com", result.baseUrl());
        assertEquals("gpt-4", result.modelName());
        assertEquals(0.7, result.temperature());
        assertEquals(4096, result.maxTokens());
        assertEquals("OPENAI", result.platformType());
    }

    @Test
    void getModelConfig_实体为null_返回null() {
        when(modelConfigMapper.selectById(99L)).thenReturn(null);

        ModelConfigData result = provider.getModelConfig("99");

        assertNull(result);
    }

    @Test
    void getModelConfig_platformType为null_platformType返回null() {
        ModelConfig entity = new ModelConfig();
        entity.setId(2L);
        entity.setApiKey("sk-another");
        entity.setBaseUrl("https://another.com");
        entity.setModelName("claude-3");
        entity.setTemperature(1.0);
        entity.setMaxTokens(8192);
        entity.setPlatformType(null);
        when(modelConfigMapper.selectById(2L)).thenReturn(entity);

        ModelConfigData result = provider.getModelConfig("2");

        assertNotNull(result);
        assertEquals("2", result.id());
        assertNull(result.platformType());
    }

    @Test
    void updateSessionModelId_会话存在_更新modelId() {
        Session session = new Session();
        session.setId(1L);
        session.setModelId(10L);
        session.setAgentId(100L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        provider.updateSessionModelId("1", "99");

        assertEquals(99L, session.getModelId());
        verify(sessionMapper).updateById((Session) session);
    }

    @Test
    void updateSessionModelId_会话为null_不执行更新() {
        when(sessionMapper.selectById(999L)).thenReturn(null);

        provider.updateSessionModelId("999", "99");

        verify(sessionMapper, never()).updateById(any(Session.class));
    }

    @Test
    void getHooks_返回所有HookInvokerBean() {
        HookInvoker hook1 = mock(HookInvoker.class);
        HookInvoker hook2 = mock(HookInvoker.class);
        when(applicationContext.getBeansOfType(HookInvoker.class))
                .thenReturn(Map.of("hook1", hook1, "hook2", hook2));

        List<HookInvoker> hooks = provider.getHooks();

        assertEquals(2, hooks.size());
        assertTrue(hooks.contains(hook1));
        assertTrue(hooks.contains(hook2));
    }

    @Test
    void getHooks_无HookInvokerBean_返回空列表() {
        when(applicationContext.getBeansOfType(HookInvoker.class))
                .thenReturn(Map.of());

        List<HookInvoker> hooks = provider.getHooks();

        assertNotNull(hooks);
        assertTrue(hooks.isEmpty());
    }

    @Test
    void getHooks_withSessionId_返回空列表() {
        List<HookInvoker> hooks = provider.getHooks("1");

        assertNotNull(hooks);
        assertTrue(hooks.isEmpty());
    }

    @Test
    void getPreSystemPrompt_WEBSOCKET子会话_返回含无需轮询引导的前置提示词() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("1")).thenReturn(true);

        String result = provider.getPreSystemPrompt("1");

        assertNotNull(result);
        assertEquals(DefaultChatDataProvider.WEB_SOCKET_SUB_SESSION_PRE_SYSTEM_PROMPT, result);
        assertTrue(result.contains("必须调用 send_result_to_parent 工具"));
        assertTrue(result.contains("将执行结果作为参数发送给父会话"));
        assertTrue(result.contains("禁止不调用工具直接结束"));
        assertTrue(result.contains("结果内容为最终答案"));
        assertTrue(result.contains("无需轮询获取父会话或其他会话状态"));
        assertTrue(result.contains("只需完成分配的任务并回传结果"));
        verify(toolDataProvider, never()).getSessionToolIds(anyString());
        verify(agentSkillMapper, never()).selectList(any());
    }

    @Test
    void getPreSystemPrompt_子会话非WEBSOCKET_返回null() {
        Session child = new Session();
        child.setId(4L);
        child.setIsChild(true);
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("4")).thenReturn(false);
        when(sessionMapper.selectById(4L)).thenReturn(child);

        String result = provider.getPreSystemPrompt("4");

        assertNull(result);
        verify(toolDataProvider, never()).getSessionToolIds(anyString());
        verify(agentSkillMapper, never()).selectList(any());
    }

    @Test
    void getPreSystemPrompt_会话不存在_返回null() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("5")).thenReturn(false);
        when(sessionMapper.selectById(5L)).thenReturn(null);

        String result = provider.getPreSystemPrompt("5");

        assertNull(result);
        verify(toolDataProvider, never()).getSessionToolIds(anyString());
        verify(agentSkillMapper, never()).selectList(any());
    }

    @Test
    void getPreSystemPrompt_sessionId为空_返回null() {
        assertNull(provider.getPreSystemPrompt(null));
        assertNull(provider.getPreSystemPrompt(""));
        verify(sessionMapper, never()).selectById(any());
        verify(toolDataProvider, never()).getSessionToolIds(anyString());
    }

    @Test
    void getPreSystemPrompt_主会话_仅子会话工具_生成权限说明() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("1")).thenReturn(false);
        when(sessionMapper.selectById(1L)).thenReturn(mainSession(1L, 100L));
        when(toolDataProvider.getSessionToolIds("1"))
                .thenReturn(List.of(new SessionToolInfo("10", SessionAuthType.CHILD)));
        when(toolDataProvider.getToolById("10")).thenReturn(toolDto("10", "child-tool", "子会话专用工具"));
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());

        String result = provider.getPreSystemPrompt("1");

        assertNotNull(result);
        assertTrue(result.contains("以下为子会话相关的工具/技能权限说明。标注【仅子会话可用】的项不可在当前会话直接调用，需通过子会话使用。标注【均可用】的项当前会话可直接调用。"));
        assertTrue(result.contains("【仅子会话可用】工具：\n- child-tool: 子会话专用工具"));
        assertFalse(result.contains("【均可用】工具"));
        assertFalse(result.contains("【仅子会话可用】技能"));
        assertTrue(result.contains("如需使用上述子会话工具/技能，可开启子会话执行任务：创建或复用子会话并通过回调执行用户消息，支持指定工具和技能"));
        assertTrue(result.contains("子会话执行期间请勿反复调用 callback_sub_session（发消息工具）轮询询问子会话结果，只需等候子会话通过 send_result_to_parent 返回执行结果；如需与子会话多轮交互可等待其返回后再发下一条消息。"));
    }

    @Test
    void getPreSystemPrompt_主会话_均可用工具含内置工具_生成权限说明() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("2")).thenReturn(false);
        when(sessionMapper.selectById(2L)).thenReturn(mainSession(2L, 100L));
        when(toolDataProvider.getSessionToolIds("2")).thenReturn(List.of(
                new SessionToolInfo("20", SessionAuthType.ALL),
                new SessionToolInfo("callback_sub_session", SessionAuthType.ALL)));
        when(toolDataProvider.getToolById("20")).thenReturn(toolDto("20", "shared-tool", "通用工具"));
        when(toolDataProvider.getToolById("callback_sub_session"))
                .thenReturn(toolDto("callback_sub_session", "callback_sub_session", "回调子会话"));
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());

        String result = provider.getPreSystemPrompt("2");

        assertNotNull(result);
        assertTrue(result.contains("【均可用】工具：\n- shared-tool: 通用工具\n- callback_sub_session: 回调子会话"));
        assertFalse(result.contains("【仅子会话可用】工具"));
        assertFalse(result.contains("【仅子会话可用】技能"));
        assertFalse(result.contains("【均可用】技能"));
    }

    @Test
    void getPreSystemPrompt_主会话_技能分组_生成权限说明() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("3")).thenReturn(false);
        when(sessionMapper.selectById(3L)).thenReturn(mainSession(3L, 100L));
        when(toolDataProvider.getSessionToolIds("3")).thenReturn(List.of());

        AgentSkill childSkill = new AgentSkill();
        childSkill.setAgentId(100L);
        childSkill.setSkillId(1L);
        childSkill.setSessionAuth(SessionAuthType.CHILD);
        AgentSkill sharedSkill = new AgentSkill();
        sharedSkill.setAgentId(100L);
        sharedSkill.setSkillId(2L);
        sharedSkill.setSessionAuth(SessionAuthType.ALL);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of(childSkill, sharedSkill));
        when(skillConfigMapper.selectById(1L)).thenReturn(enabledSkill(1L, "child-skill", "子会话技能"));
        when(skillConfigMapper.selectById(2L)).thenReturn(enabledSkill(2L, "shared-skill", "通用技能"));

        String result = provider.getPreSystemPrompt("3");

        assertNotNull(result);
        assertTrue(result.contains("【仅子会话可用】技能：\n- child-skill: 子会话技能"));
        assertTrue(result.contains("【均可用】技能：\n- shared-skill: 通用技能"));
        assertFalse(result.contains("【仅子会话可用】工具"));
        assertFalse(result.contains("【均可用】工具"));
    }

    @Test
    void getPreSystemPrompt_主会话_混合工具与技能_生成完整权限说明() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("6")).thenReturn(false);
        when(sessionMapper.selectById(6L)).thenReturn(mainSession(6L, 100L));
        when(toolDataProvider.getSessionToolIds("6")).thenReturn(List.of(
                new SessionToolInfo("10", SessionAuthType.CHILD),
                new SessionToolInfo("20", SessionAuthType.ALL),
                new SessionToolInfo("callback_sub_session", SessionAuthType.ALL)));
        when(toolDataProvider.getToolById("10")).thenReturn(toolDto("10", "child-tool", "子会话专用工具"));
        when(toolDataProvider.getToolById("20")).thenReturn(toolDto("20", "shared-tool", "通用工具"));
        when(toolDataProvider.getToolById("callback_sub_session"))
                .thenReturn(toolDto("callback_sub_session", "callback_sub_session", "回调子会话"));

        AgentSkill childSkill = new AgentSkill();
        childSkill.setAgentId(100L);
        childSkill.setSkillId(1L);
        childSkill.setSessionAuth(SessionAuthType.CHILD);
        AgentSkill sharedSkill = new AgentSkill();
        sharedSkill.setAgentId(100L);
        sharedSkill.setSkillId(2L);
        sharedSkill.setSessionAuth(SessionAuthType.ALL);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of(childSkill, sharedSkill));
        when(skillConfigMapper.selectById(1L)).thenReturn(enabledSkill(1L, "child-skill", "子会话技能"));
        when(skillConfigMapper.selectById(2L)).thenReturn(enabledSkill(2L, "shared-skill", "通用技能"));

        String result = provider.getPreSystemPrompt("6");

        assertNotNull(result);
        String expected = "以下为子会话相关的工具/技能权限说明。标注【仅子会话可用】的项不可在当前会话直接调用，需通过子会话使用。标注【均可用】的项当前会话可直接调用。\n"
                + "\n【仅子会话可用】工具：\n- child-tool: 子会话专用工具\n"
                + "\n【均可用】工具：\n- shared-tool: 通用工具\n- callback_sub_session: 回调子会话\n"
                + "\n【仅子会话可用】技能：\n- child-skill: 子会话技能\n"
                + "\n【均可用】技能：\n- shared-skill: 通用技能\n"
                + "\n如需使用上述子会话工具/技能，可开启子会话执行任务：创建或复用子会话并通过回调执行用户消息，支持指定工具和技能"
                + "\n子会话执行期间请勿反复调用 callback_sub_session（发消息工具）轮询询问子会话结果，只需等候子会话通过 send_result_to_parent 返回执行结果；如需与子会话多轮交互可等待其返回后再发下一条消息。";
        assertEquals(expected, result);
    }

    @Test
    void getPreSystemPrompt_主会话_无权限项_返回null() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("7")).thenReturn(false);
        when(sessionMapper.selectById(7L)).thenReturn(mainSession(7L, 100L));
        when(toolDataProvider.getSessionToolIds("7")).thenReturn(List.of());
        when(agentSkillMapper.selectList(any())).thenReturn(List.of());

        String result = provider.getPreSystemPrompt("7");

        assertNull(result);
        verify(toolDataProvider, never()).getToolById(anyString());
        verify(skillConfigMapper, never()).selectById(any());
    }

    @Test
    void getPreSystemPrompt_主会话_agentId为空_返回null() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("8")).thenReturn(false);
        when(sessionMapper.selectById(8L)).thenReturn(mainSession(8L, null));
        when(toolDataProvider.getSessionToolIds("8")).thenReturn(List.of());

        String result = provider.getPreSystemPrompt("8");

        assertNull(result);
        verify(agentSkillMapper, never()).selectList(any());
    }

    @Test
    void getPreSystemPrompt_主会话_技能被禁用_不纳入权限说明() {
        when(subSessionWebSocketModeResolver.isWebSocketSubSession("9")).thenReturn(false);
        when(sessionMapper.selectById(9L)).thenReturn(mainSession(9L, 100L));
        when(toolDataProvider.getSessionToolIds("9")).thenReturn(List.of());

        AgentSkill sharedSkill = new AgentSkill();
        sharedSkill.setAgentId(100L);
        sharedSkill.setSkillId(2L);
        sharedSkill.setSessionAuth(SessionAuthType.ALL);
        when(agentSkillMapper.selectList(any())).thenReturn(List.of(sharedSkill));
        // SkillConfig.id 继承自 BaseEntity，Lombok @Builder 不含父类字段，需构建后 setId
        SkillConfig disabledSkill = SkillConfig.builder()
                .name("disabled-skill")
                .description("已禁用技能")
                .status(CommonStatus.DISABLED)
                .build();
        disabledSkill.setId(2L);
        when(skillConfigMapper.selectById(2L)).thenReturn(disabledSkill);

        String result = provider.getPreSystemPrompt("9");

        assertNull(result);
    }

    @Test
    void getPostSystemPrompt_默认返回null() {
        assertNull(provider.getPostSystemPrompt("1"));
    }

    @Test
    void getPostSystemPrompt_sessionId为空_返回null() {
        assertNull(provider.getPostSystemPrompt(null));
        assertNull(provider.getPostSystemPrompt(""));
    }

    @Test
    void onAgentChanged_清空解析器缓存() {
        provider.onAgentChanged(new AgentChangedEvent(this, 1L));

        verify(subSessionWebSocketModeResolver).clearCache();
    }
}
