package com.ghost616.platform.config;

import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.agentbase.service.agent.ChatDataCacheManager;
import com.ghost616.agentbase.service.agent.ChatDataProvider;
import com.ghost616.agentbase.service.agent.ChatService;
import com.ghost616.agentbase.service.agent.ContextDataProvider;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.agentbase.service.agent.SessionManager;
import com.ghost616.agentbase.service.agent.ToolDataProvider;
import com.ghost616.agentbase.service.agent.ToolExecutionService;
import com.ghost616.agentbase.service.agent.invoker.SystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemToolProvider;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerFactory;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import com.ghost616.agentbase.service.agent.ToolExecutionProvider;
import com.ghost616.agentbase.sendmessage.MessageSender;
import com.ghost616.agentinteg.AgentAssembler;
import com.ghost616.agentinteg.model.invoker.DefaultModelInvokerFactory;
import com.ghost616.agentinteg.subsession.SubSessionResultFallbackHook;
import com.ghost616.agentinteg.subsession.SubSessionResultProvider;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.service.agent.DatabaseAgentLog;
import com.ghost616.platform.service.agent.DefaultChatDataCacheProvider;
import com.ghost616.platform.service.agent.DefaultChatDataProvider;
import com.ghost616.platform.service.agent.DefaultToolExecutionProvider;
import com.ghost616.platform.service.agent.SubSessionWebSocketModeResolver;
import com.ghost616.platform.session.UserContextThreadVariableHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class AgentContextConfiguration {

    private final ContextDataProvider contextDataProvider;
    private final MessageDataProvider messageDataProvider;
    private final ToolDataProvider toolDataProvider;

    @Bean
    public SystemToolProvider systemToolProvider(ApplicationContext applicationContext) {
        return () -> {
            Map<String, SystemTool> beans = applicationContext.getBeansOfType(SystemTool.class);
            Map<String, SystemTool> tools = new HashMap<>();
            for (SystemTool tool : beans.values()) {
                String toolName = tool.getToolName();
                if (toolName != null && !toolName.isBlank()) {
                    tools.put(toolName, tool);
                }
            }
            return tools;
        };
    }

    @Bean
    public DefaultChatDataProvider defaultChatDataProvider(
            ModelConfigMapper modelConfigMapper,
            SessionMapper sessionMapper,
            ApplicationContext applicationContext,
            SubSessionWebSocketModeResolver subSessionWebSocketModeResolver) {
        return new DefaultChatDataProvider(modelConfigMapper, sessionMapper, applicationContext,
                subSessionWebSocketModeResolver);
    }

    @Bean
    public ModelInvokerFactory modelInvokerFactory(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {
        return new DefaultModelInvokerFactory(restClientBuilder, webClientBuilder);
    }

    @Bean
    public DefaultToolExecutionProvider toolExecutionProvider() {
        return new DefaultToolExecutionProvider();
    }

    /**
     * 子会话结果兜底回传 HOOK，构造注入本模块的 {@link SubSessionResultProvider} 实现。
     * 注册为 Spring Bean 后，由 {@link DefaultChatDataProvider#getHooks()}
     * （getBeansOfType(HookInvoker.class)）自动收集进聊天流程。
     */
    @Bean
    public SubSessionResultFallbackHook subSessionResultFallbackHook(SubSessionResultProvider resultProvider) {
        return new SubSessionResultFallbackHook(resultProvider);
    }

    @Bean
    public AgentAssembler agentAssembler(SystemToolProvider systemToolProvider,
                            ModelInvokerFactory modelInvokerFactory,
                            ChatDataProvider chatDataProvider,
                            ToolExecutionProvider toolExecutionProvider,
                            UserContextThreadVariableHandler userContextThreadVariableHandler,
                            MessageSender messageSender) {
        AgentAssembler assembler = new AgentAssembler(contextDataProvider, messageDataProvider, toolDataProvider,
                systemToolProvider, modelInvokerFactory, chatDataProvider, messageSender, toolExecutionProvider);
        assembler.setThreadVariableHandler(userContextThreadVariableHandler);
        return assembler;
    }

    @Bean
    public SessionManager sessionManager(AgentAssembler agentAssembler) {
        agentAssembler.build();
        return agentAssembler.sessionManager();
    }

    @Bean
    public AgentContextManager agentContextManager(AgentAssembler agentAssembler) {
        agentAssembler.build();
        return agentAssembler.agentContextManager();
    }

    @Bean
    public ToolManager toolManager(AgentAssembler agentAssembler) {
        agentAssembler.build();
        return agentAssembler.toolManager();
    }

    @Bean
    public ModelInvokerManager modelInvokerManager(AgentAssembler agentAssembler) {
        agentAssembler.build();
        return agentAssembler.modelInvokerManager();
    }

    @Bean
    public ChatService chatService(AgentAssembler agentAssembler, DatabaseAgentLog databaseAgentLog) {
        ChatService chatService = agentAssembler.build().chatService();
        agentAssembler.setAgentLog(databaseAgentLog);
        agentAssembler.refreshHooks();
        return chatService;
    }

    @Bean
    public DefaultChatDataCacheProvider defaultChatDataCacheProvider() {
        return new DefaultChatDataCacheProvider();
    }

    @Bean
    public ChatDataCacheManager chatDataCacheManager(DatabaseAgentLog databaseAgentLog,
                                                     DefaultChatDataCacheProvider defaultChatDataCacheProvider) {
        ChatDataCacheManager cacheManager = new ChatDataCacheManager(defaultChatDataCacheProvider);
        if (databaseAgentLog != null) {
            cacheManager.setAgentLog(databaseAgentLog);
        }
        return cacheManager;
    }

    @Bean
    public ToolExecutionService toolExecutionService(AgentAssembler agentAssembler) {
        return agentAssembler.build().toolExecutionService();
    }
}