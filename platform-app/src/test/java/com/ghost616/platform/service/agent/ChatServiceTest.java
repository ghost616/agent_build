package com.ghost616.platform.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.ChatDataProvider;
import com.ghost616.agentbase.service.agent.SessionManager;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.agent.ChatService;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock
    private AgentContextManager agentContextManager;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private ModelInvokerManager modelInvokerManager;
    @Mock
    private ModelConfigMapper modelConfigMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private SystemToolManager systemToolManager;
    @Mock
    private ToolManager toolManager;
    @Mock
    private ModelInvoker modelInvoker;
    @Mock
    private AgentComponentRegistry registry;
    @Mock
    private ChatDataProvider chatDataProvider;
    @Mock
    private HookManager hookManager;

    @InjectMocks
    private ChatService chatService;

    private AgentExecutionContext context;
    private AgentExecutionContext.AgentContextMutator mutator;
    private AgentContextManager.AgentSessionContext sessionContext;

    @BeforeEach
    void setUp() {
        mutator = new AgentExecutionContext.AgentContextMutator();
        context = new AgentExecutionContext(
                "1", "1", "system prompt", "1", 10,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                mutator, new HashMap<>(), new HashMap<>(), null, null, null, null);
        when(registry.getAgentContextManager()).thenReturn(agentContextManager);
        when(registry.getSessionManager()).thenReturn(sessionManager);
        when(registry.getModelInvokerManager()).thenReturn(modelInvokerManager);
        when(registry.getSystemToolManager()).thenReturn(systemToolManager);
        when(registry.getToolManager()).thenReturn(toolManager);
        when(toolManager.getBuiltinTools(any())).thenReturn(new ArrayList<>());
        when(registry.getChatDataProvider()).thenReturn(chatDataProvider);
        when(chatDataProvider.getModelConfig(any())).thenReturn(new ModelConfigData("1", "key", "url", "model", 0.7, 4096, "openai", null));
        when(registry.getHookManager()).thenReturn(hookManager);

        AtomicBoolean toolInvoking = new AtomicBoolean(false);
        sessionContext = new AgentContextManager.AgentSessionContext(context, mutator, toolInvoking);
        when(systemToolManager.getToolDefinitions()).thenReturn(java.util.Collections.emptyList());

        SessionManager.MessageSaveBuilder saveBuilder = mock(SessionManager.MessageSaveBuilder.class);
        when(sessionManager.messageSave()).thenReturn(saveBuilder);
        when(saveBuilder.sessionId(any())).thenReturn(saveBuilder);
        when(saveBuilder.role(any())).thenReturn(saveBuilder);
        when(saveBuilder.content(any())).thenReturn(saveBuilder);
        when(saveBuilder.images(any())).thenReturn(saveBuilder);
        when(saveBuilder.userInput(any())).thenReturn(saveBuilder);
        when(saveBuilder.conversationId(any())).thenReturn(saveBuilder);
        when(saveBuilder.save()).thenReturn(null);
    }

    @Test
    void takeWhile_stopped为true时_非toolContinue消息会resetStopped_正常发射() {
        ChatRequest request = ChatRequest.builder()
                .sessionId("1")
                .content("hello")
                .modelId("1")
                .conversationId("conv-1")
                .build();

        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build("1")).thenReturn(builder);
        when(builder.modelIdOverride("1")).thenReturn(builder);
        when(builder.build()).thenReturn(sessionContext);
        when(sessionMapper.selectById(1L)).thenReturn(null);

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(1L);
        when(modelConfigMapper.selectById(1L)).thenReturn(modelConfig);
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(modelInvoker);

        ChatChunk chunk1 = ChatChunk.builder().delta("chunk1").build();
        ChatChunk chunk2 = ChatChunk.builder().delta("chunk2").build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk1, chunk2));

        mutator.setStopped();

        Flux<ServerSentEvent<ChatChunk>> result = chatService.chat(request);

        StepVerifier.create(result)
                .expectNextMatches(sse -> "chunk1".equals(sse.data().getDelta()))
                .expectNextMatches(sse -> "chunk2".equals(sse.data().getDelta()))
                .expectComplete()
                .verify();

        assertTrue(context.isStopped() == false);
    }

    @Test
    void takeWhile_stopped为false时正常发射() {
        ChatRequest request = ChatRequest.builder()
                .sessionId("1")
                .content("hello")
                .modelId("1")
                .conversationId("conv-1")
                .build();

        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build("1")).thenReturn(builder);
        when(builder.modelIdOverride("1")).thenReturn(builder);
        when(builder.build()).thenReturn(sessionContext);
        when(sessionMapper.selectById(1L)).thenReturn(null);

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(1L);
        when(modelConfigMapper.selectById(1L)).thenReturn(modelConfig);
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(modelInvoker);

        ChatChunk chunk1 = ChatChunk.builder().delta("chunk1").build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk1));

        Flux<ServerSentEvent<ChatChunk>> result = chatService.chat(request);

        StepVerifier.create(result)
                .expectNextMatches(sse -> {
                    ChatChunk data = sse.data();
                    return "chunk1".equals(data.getDelta());
                })
                .expectComplete()
                .verify();
    }

    @Test
    void toolContinue路径不应调用resetStopped() {
        ChatRequest request = ChatRequest.builder()
                .sessionId("1")
                .content("[tool_continue]")
                .modelId("1")
                .conversationId("conv-1")
                .build();

        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build("1")).thenReturn(builder);
        when(builder.modelIdOverride("1")).thenReturn(builder);
        when(builder.build()).thenReturn(sessionContext);
        when(sessionMapper.selectById(1L)).thenReturn(null);

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(1L);
        when(modelConfigMapper.selectById(1L)).thenReturn(modelConfig);
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(modelInvoker);

        ChatChunk chunk1 = ChatChunk.builder().delta("chunk1").build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk1));

        mutator.setStopped();

        Flux<ServerSentEvent<ChatChunk>> result = chatService.chat(request);

        StepVerifier.create(result)
                .expectComplete()
                .verify();

        assertTrue(context.isStopped());
    }

    @Test
    void doOnCancel_调用setStopped() {
        ChatRequest request = ChatRequest.builder()
                .sessionId("1")
                .content("hello")
                .modelId("1")
                .conversationId("conv-1")
                .build();

        AgentContextManager.Builder builder = mock(AgentContextManager.Builder.class);
        when(agentContextManager.build("1")).thenReturn(builder);
        when(builder.modelIdOverride("1")).thenReturn(builder);
        when(builder.build()).thenReturn(sessionContext);
        when(sessionMapper.selectById(1L)).thenReturn(null);

        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(1L);
        when(modelConfigMapper.selectById(1L)).thenReturn(modelConfig);
        when(modelInvokerManager.getInvoker(any(ModelConfigData.class))).thenReturn(modelInvoker);

        ChatChunk chunk1 = ChatChunk.builder().delta("chunk1").build();
        when(modelInvoker.invokeStream(any())).thenReturn(Flux.just(chunk1).delayElements(Duration.ofMillis(100)));

        Flux<ServerSentEvent<ChatChunk>> result = chatService.chat(request);

        StepVerifier.create(result)
                .thenCancel()
                .verify();

        assertTrue(context.isStopped());
    }
}
