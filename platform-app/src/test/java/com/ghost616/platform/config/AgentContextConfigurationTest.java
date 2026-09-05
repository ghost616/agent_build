package com.ghost616.platform.config;

import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.service.agent.ChatDataCacheManager;
import com.ghost616.agentbase.service.agent.AgentMessageProxy;
import com.ghost616.agentbase.service.agent.ChatDataProvider;
import com.ghost616.agentbase.service.agent.ChatService;
import com.ghost616.agentbase.service.agent.ContextDataProvider;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.agentbase.service.agent.ToolDataProvider;
import com.ghost616.agentbase.service.agent.ToolExecutionService;
import com.ghost616.agentbase.service.agent.ToolExecutionProvider;
import com.ghost616.agentbase.service.agent.invoker.SystemToolProvider;
import com.ghost616.agentbase.sendmessage.MessageSender;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerFactory;
import com.ghost616.agentbase.service.agent.invoker.HookInvoker;
import com.ghost616.agentinteg.AgentAssembler;
import com.ghost616.agentinteg.hook.AvailableSkillsSystemHook;
import com.ghost616.agentinteg.hook.LoadedSkillsToolHook;
import com.ghost616.agentinteg.hook.SubSessionResultFallbackHook;
import com.ghost616.agentinteg.hook.SubSessionResultProvider;
import com.ghost616.agentbase.service.agent.invoker.SystemTool;
import com.ghost616.platform.repository.AgentSkillMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SkillConfigMapper;
import com.ghost616.platform.service.agent.DatabaseAgentLog;
import com.ghost616.platform.service.agent.DefaultChatDataCacheProvider;
import com.ghost616.platform.service.agent.DefaultChatDataProvider;
import com.ghost616.platform.service.agent.SubSessionWebSocketModeResolver;
import com.ghost616.platform.session.ContextThreadVariableHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentContextConfigurationTest {

    private final AgentContextConfiguration config = new AgentContextConfiguration(
            mock(ContextDataProvider.class),
            mock(MessageDataProvider.class),
            mock(ToolDataProvider.class)
    );

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ToolExecutionProvider toolExecutionProvider;

    @Mock
    private DatabaseAgentLog databaseAgentLog;

    @Mock
    private SubSessionWebSocketModeResolver subSessionWebSocketModeResolver;

    @Mock
    private SubSessionResultProvider subSessionResultProvider;

    @Mock
    private AgentSkillMapper agentSkillMapper;

    @Mock
    private SkillConfigMapper skillConfigMapper;

    @Test
    void defaultChatDataProvider_正确创建实例() {
        DefaultChatDataProvider provider = config.defaultChatDataProvider(
                modelConfigMapper, sessionMapper, applicationContext, subSessionWebSocketModeResolver,
                mock(ToolDataProvider.class), agentSkillMapper, skillConfigMapper);

        assertNotNull(provider);
    }

    @Test
    void subSessionResultFallbackHook_正确创建并注入Provider实现() {
        SubSessionResultFallbackHook hook = config.subSessionResultFallbackHook(subSessionResultProvider);

        assertNotNull(hook);
        // 实现 SystemPostHook → SystemHook → HookInvoker，可被 getBeansOfType(HookInvoker.class) 自动收集
        assertInstanceOf(HookInvoker.class, hook);
        assertSame(SubSessionResultFallbackHook.class, hook.getClass());
    }

    @Test
    void availableSkillsSystemHook_正确创建为HookInvoker() {
        AvailableSkillsSystemHook hook = config.availableSkillsSystemHook();

        assertNotNull(hook);
        // 实现 SystemHook → HookInvoker，可被 getBeansOfType(HookInvoker.class) 自动收集
        assertInstanceOf(HookInvoker.class, hook);
        assertSame(AvailableSkillsSystemHook.class, hook.getClass());
    }

    @Test
    void defaultChatDataProvider_getHooks_收集AvailableSkillsSystemHook() {
        AvailableSkillsSystemHook hook = config.availableSkillsSystemHook();
        when(applicationContext.getBeansOfType(HookInvoker.class))
                .thenReturn(Map.of("availableSkillsSystemHook", hook));

        DefaultChatDataProvider provider = config.defaultChatDataProvider(
                modelConfigMapper, sessionMapper, applicationContext, subSessionWebSocketModeResolver,
                mock(ToolDataProvider.class), agentSkillMapper, skillConfigMapper);

        assertTrue(provider.getHooks().contains(hook));
    }

    @Test
    void loadedSkillsToolHook_正确创建为HookInvoker() {
        LoadedSkillsToolHook hook = config.loadedSkillsToolHook();

        assertNotNull(hook);
        // 实现 SystemHook → HookInvoker，可被 getBeansOfType(HookInvoker.class) 自动收集
        assertInstanceOf(HookInvoker.class, hook);
        assertSame(LoadedSkillsToolHook.class, hook.getClass());
    }

    @Test
    void defaultChatDataProvider_getHooks_收集LoadedSkillsToolHook() {
        LoadedSkillsToolHook hook = config.loadedSkillsToolHook();
        when(applicationContext.getBeansOfType(HookInvoker.class))
                .thenReturn(Map.of("loadedSkillsToolHook", hook));

        DefaultChatDataProvider provider = config.defaultChatDataProvider(
                modelConfigMapper, sessionMapper, applicationContext, subSessionWebSocketModeResolver,
                mock(ToolDataProvider.class), agentSkillMapper, skillConfigMapper);

        assertTrue(provider.getHooks().contains(hook));
    }

    @Test
    void agentAssembler_正确创建实例() {
        SystemToolProvider systemToolProvider = mock(SystemToolProvider.class);
        ModelInvokerFactory modelInvokerFactory = mock(ModelInvokerFactory.class);
        ChatDataProvider chatDataProvider = mock(ChatDataProvider.class);

        AgentAssembler agentAssembler = config.agentAssembler(systemToolProvider, modelInvokerFactory, chatDataProvider,
                toolExecutionProvider, new ContextThreadVariableHandler(), mock(MessageSender.class));

        assertNotNull(agentAssembler);
    }

    @Test
    void chatService_通过AgentAssembler创建() {
        SystemToolProvider systemToolProvider = mock(SystemToolProvider.class);
        ModelInvokerFactory modelInvokerFactory = mock(ModelInvokerFactory.class);
        ChatDataProvider chatDataProvider = mock(ChatDataProvider.class);

        AgentAssembler agentAssembler = config.agentAssembler(systemToolProvider, modelInvokerFactory, chatDataProvider,
                toolExecutionProvider, new ContextThreadVariableHandler(), mock(MessageSender.class));
        ChatService chatService = config.chatService(agentAssembler, databaseAgentLog);

        assertNotNull(chatService);
    }

    @Test
    void defaultChatDataCacheProvider_正确创建实例() {
        DefaultChatDataCacheProvider provider = config.defaultChatDataCacheProvider();

        assertNotNull(provider);
    }

    @Test
    void chatDataCacheManager_正确创建并注入日志() {
        DefaultChatDataCacheProvider provider = config.defaultChatDataCacheProvider();
        ChatDataCacheManager cacheManager = config.chatDataCacheManager(databaseAgentLog, provider);

        assertNotNull(cacheManager);
        cacheManager.startCache("session-1", "conv-1");
        verify(databaseAgentLog).addLog(any(LogData.class));
    }

    @Test
    void chatDataCacheManager_DatabaseAgentLog为null时_不设置缓存管理器日志() {
        DefaultChatDataCacheProvider provider = config.defaultChatDataCacheProvider();
        ChatDataCacheManager cacheManager = config.chatDataCacheManager(null, provider);

        assertNotNull(cacheManager);
        cacheManager.startCache("session-1", "conv-1");
        verify(databaseAgentLog, never()).addLog(any(LogData.class));
    }

    @Test
    void toolExecutionService_通过AgentAssembler创建() {
        SystemToolProvider systemToolProvider = mock(SystemToolProvider.class);
        ModelInvokerFactory modelInvokerFactory = mock(ModelInvokerFactory.class);
        ChatDataProvider chatDataProvider = mock(ChatDataProvider.class);

        AgentAssembler agentAssembler = config.agentAssembler(systemToolProvider, modelInvokerFactory, chatDataProvider,
                toolExecutionProvider, new ContextThreadVariableHandler(), mock(MessageSender.class));
        ToolExecutionService toolExecutionService = config.toolExecutionService(agentAssembler);

        assertNotNull(toolExecutionService);
    }

    @Test
    void systemToolProvider不再注册callback_sub_session系统工具() {
        when(applicationContext.getBeansOfType(SystemTool.class)).thenReturn(Map.of());

        var provider = config.systemToolProvider(applicationContext);
        Map<String, SystemTool> tools = provider.discoverSystemTools();

        assertTrue(tools.isEmpty());
        assertFalse(tools.containsKey("callback_sub_session"));
    }

    @Test
    void systemToolProvider包含ApplicationContext中注册的SystemTool() {
        SystemTool mockTool = mock(SystemTool.class);
        when(mockTool.getToolName()).thenReturn("my_custom_tool");
        when(applicationContext.getBeansOfType(SystemTool.class))
                .thenReturn(Map.of("myCustomTool", mockTool));

        var provider = config.systemToolProvider(applicationContext);
        Map<String, SystemTool> tools = provider.discoverSystemTools();

        assertTrue(tools.containsKey("my_custom_tool"));
        assertFalse(tools.containsKey("callback_sub_session"));
        assertEquals(1, tools.size());
    }

    @Test
    void systemToolProvider过滤掉toolName为null的SystemTool() {
        SystemTool nullNameTool = mock(SystemTool.class);
        when(nullNameTool.getToolName()).thenReturn(null);
        when(applicationContext.getBeansOfType(SystemTool.class))
                .thenReturn(Map.of("nullNameTool", nullNameTool));

        var provider = config.systemToolProvider(applicationContext);
        Map<String, SystemTool> tools = provider.discoverSystemTools();

        assertFalse(tools.containsKey(null));
        assertTrue(tools.isEmpty());
    }

    @Test
    void systemToolProvider过滤掉toolName为空字符串的SystemTool() {
        SystemTool blankNameTool = mock(SystemTool.class);
        when(blankNameTool.getToolName()).thenReturn("");
        when(applicationContext.getBeansOfType(SystemTool.class))
                .thenReturn(Map.of("blankNameTool", blankNameTool));

        var provider = config.systemToolProvider(applicationContext);
        Map<String, SystemTool> tools = provider.discoverSystemTools();

        assertTrue(tools.isEmpty());
    }

    @Test
    void agentMessageProxy_正确创建独立单例Bean() {
        AgentMessageProxy proxy = config.agentMessageProxy(
                mock(ChatService.class), mock(ToolExecutionService.class), mock(ChatDataCacheManager.class));

        assertNotNull(proxy);
        assertSame(AgentMessageProxy.class, proxy.getClass());
    }

    @Test
    void subSessionEvaluationExecutor_独立配置创建评估子会话驱动线程池() {
        // 该 @Bean 位于独立 SubSessionEvaluationConfig，避免参与 AgentContextConfiguration 构造链造成循环依赖
        ExecutorService executor = new SubSessionEvaluationConfig().subSessionEvaluationExecutor();

        assertNotNull(executor);
        assertFalse(executor.isShutdown());
        executor.shutdown();
    }

    @Test
    void contextThreadVariableHandler_实现ThreadVariableHandler并统一传播两类上下文() {
        ContextThreadVariableHandler handler = new ContextThreadVariableHandler();

        assertInstanceOf(ThreadVariableHandler.class, handler);
        assertNotNull(handler.wrap());
    }
}
