package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.enums.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMessageProxyTest {

    @Mock
    private ChatService chatService;
    @Mock
    private ToolExecutionService toolExecutionService;
    @Mock
    private ChatDataCacheManager chatDataCacheManager;

    private AgentMessageProxy proxy;
    private final String sessionId = "1";
    private final String modelId = "100";

    @BeforeEach
    void setUp() {
        proxy = new AgentMessageProxy(chatService, toolExecutionService);
    }

    @Test
    void setChatDataCacheManager后字段应被设置() {
        ChatDataCacheManager manager = new ChatDataCacheManager(new ChatDataCacheProvider() {
            @Override
            public String createCache(String sessionId, String conversationId) {
                return null;
            }

            @Override
            public boolean cacheExists(String cacheId) {
                return false;
            }

            @Override
            public boolean cacheExists(String sessionId, String conversationId) {
                return false;
            }

            @Override
            public boolean isCacheDone(String cacheId) {
                return false;
            }

            @Override
            public String getCacheId(String sessionId, String conversationId) {
                return null;
            }

            @Override
            public CacheSessionInfo getCacheSessionInfo(String cacheId) {
                return null;
            }

            @Override
            public int getMaxChunkIndex(String cacheId) {
                return -1;
            }

            @Override
            public void appendChunk(String cacheId, ChatChunk chunk) {
            }

            @Override
            public void removeCache(String cacheId) {
            }

            @Override
            public List<ChatChunk> getChunks(String cacheId, int startIndex, int endIndex) {
                return Collections.emptyList();
            }
        });
        proxy.setChatDataCacheManager(manager);
        assertEquals(manager, getPrivateField(proxy, "chatDataCacheManager"));
    }

    @Test
    void sendUserMessage_无工具调用时返回文本消息() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Hello back").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("Hello back", result.getContent());
        verify(toolExecutionService, never()).executeTool(any());
    }

    @Test
    void sendUserMessage_带images时构建ChatRequest透传images() {
        List<ImageContent> images = List.of(
                ImageContent.builder().imgId("img-1").imgText("data:image/png;base64,AAA").build());
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("ok").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessage(sessionId, "看图", modelId, true, images);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertEquals(sessionId, request.getSessionId());
        assertEquals("看图", request.getContent());
        assertEquals(modelId, request.getModelId());
        assertEquals(true, request.getThinking());
        assertEquals(images, request.getImages());
    }

    @Test
    void sendUserMessage_不带images时ChatRequest的images为null() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("ok").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        assertNull(captor.getValue().getImages());
    }

    @Test
    void sendUserMessageToSession_带images时构建ChatRequest透传images() {
        List<ImageContent> images = List.of(
                ImageContent.builder().imgId("img-2").imgText("data:image/jpeg;base64,BBB").build());
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("ok").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessageToSession(sessionId, "看图", modelId, null, images);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertEquals(sessionId, request.getSessionId());
        assertEquals(images, request.getImages());
        assertNotNull(request.getConversationId(), "sendUserMessageToSession 应自动生成 conversationId");
    }

    @Test
    void sendUserMessage_工具正常执行后返回文本() {
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Result text").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(textEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("Result text", result.getContent());
        verify(toolExecutionService).executeTool(any());
        verify(toolExecutionService).continueAfterTools(any());
    }

    @Test
    void sendUserMessage_同一参数组合调用5次触发振荡保护() {
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "repeatedTool", "{\"x\":1}", true, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "repeatedTool", "{\"x\":1}", false, null);
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("", result.getContent());
        verify(toolExecutionService, times(5)).executeTool(any());
        verify(toolExecutionService, never()).continueAfterTools(any());
    }

    @Test
    void sendUserMessage_同一参数组合调用4次不触发振荡保护() {
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("OK").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(textEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "safeTool", "{}", false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "safeTool", "{}", false, null);
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("OK", result.getContent());
        verify(toolExecutionService, times(1)).executeTool(any());
    }

    @Test
    void sendUserMessage_工具执行返回empty时正常退出循环() {
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Done").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(textEvent));
        ToolExecutionService.ToolExecutionResult emptyResult = new ToolExecutionService.ToolExecutionResult(
                "empty", null, null, null, false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(emptyResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("Done", result.getContent());
        verify(toolExecutionService, times(1)).executeTool(any());
    }

    @Test
    void sendUserMessage_events为null时返回空消息() {
        when(chatService.chat(any())).thenReturn(Flux.empty());

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("", result.getContent());
    }

    @Test
    void sendUserMessageToSession_自动生成时间戳conversationId并透传() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Reply").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        Message result = proxy.sendUserMessageToSession(sessionId, "Hi", modelId, true);

        assertEquals("assistant", result.getRole());
        assertEquals("Reply", result.getContent());

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertEquals(sessionId, request.getSessionId());
        assertEquals("Hi", request.getContent());
        assertEquals(modelId, request.getModelId());
        assertEquals(Boolean.TRUE, request.getThinking());
        assertNotNull(request.getConversationId());
        assertTrue(request.getConversationId().matches("\\d+"));
    }

    @Test
    void sendUserMessage_未设置conversationId时自动生成时间戳conversationId() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Reply").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertNotNull(request.getConversationId());
        assertTrue(request.getConversationId().matches("\\d+"));
        assertEquals(sessionId, request.getSessionId());
    }

    @Test
    void sendUserMessage_带conversationId时构建ChatRequest透传conversationId() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Reply").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessage(sessionId, "Hi", modelId, true, "conv-abc-123");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertEquals(sessionId, request.getSessionId());
        assertEquals("Hi", request.getContent());
        assertEquals(modelId, request.getModelId());
        assertEquals(Boolean.TRUE, request.getThinking());
        assertEquals("conv-abc-123", request.getConversationId(), "sendUserMessage 应透传调用方 conversationId");
        assertNull(request.getImages());
    }

    @Test
    void sendUserMessage_带conversationId与images时构建ChatRequest透传两者() {
        List<ImageContent> images = List.of(
                ImageContent.builder().imgId("img-3").imgText("data:image/png;base64,CCC").build());
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("ok").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessage(sessionId, "看图", modelId, null, "conv-abc-123", images);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertEquals("conv-abc-123", request.getConversationId());
        assertEquals(images, request.getImages());
    }

    @Test
    void sendUserMessage_conversationId为null时自动生成时间戳conversationId() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Reply").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessage(sessionId, "Hi", modelId, null, (String) null);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService).chat(captor.capture());
        ChatRequest request = captor.getValue();
        assertNotNull(request.getConversationId());
        assertTrue(request.getConversationId().matches("\\d+"), "conversationId 为 null 时应由 processChat 自动生成时间戳兜底");
        assertEquals(sessionId, request.getSessionId());
        assertNull(request.getImages());
    }

    @Test
    void sendUserMessageToSession_每次调用生成的conversationId为时间戳() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Reply").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        proxy.sendUserMessageToSession(sessionId, "Hi", modelId, null);
        proxy.sendUserMessageToSession(sessionId, "Hi", modelId, null);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatService, times(2)).chat(captor.capture());
        for (ChatRequest request : captor.getAllValues()) {
            assertNotNull(request.getConversationId());
            assertTrue(request.getConversationId().matches("\\d+"));
        }
    }

    @Test
    void sendUserMessageToSession_工具正常执行后返回文本() {
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Result text").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(textEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessageToSession(sessionId, "Hi", modelId, null);

        assertEquals("assistant", result.getRole());
        assertEquals("Result text", result.getContent());
        verify(toolExecutionService).executeTool(any());
        verify(toolExecutionService).continueAfterTools(any());
    }

    @Test
    void sendUserMessage_无缓存管理器时_不调用缓存方法() {
        ServerSentEvent<ChatChunk> event = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Hello").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(event));

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("Hello", result.getContent());
        verify(chatDataCacheManager, never()).startCache(any(), any());
        verify(chatDataCacheManager, never()).appendChunk(any(), any());
    }

    @Test
    void sendUserMessage_缓存管理器存在时_创建缓存追加块并追加STOP结束块() {
        proxy.setChatDataCacheManager(chatDataCacheManager);
        when(chatDataCacheManager.startCache(eq(sessionId), any())).thenReturn("cache-1");
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Hello").hasToolCalls(false).build())
                .build();
        ServerSentEvent<ChatChunk> endEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("").finishReason(FinishReason.STOP).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(textEvent, endEvent));

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("Hello", result.getContent());
        verify(chatDataCacheManager).startCache(eq(sessionId), any());
        ArgumentCaptor<ChatChunk> captor = ArgumentCaptor.forClass(ChatChunk.class);
        verify(chatDataCacheManager, times(2)).appendChunk(eq("cache-1"), captor.capture());
        List<ChatChunk> chunks = captor.getAllValues();
        assertEquals("Hello", chunks.get(0).getDelta());
        assertNull(chunks.get(0).getFinishReason());
        assertNull(chunks.get(1).getDelta());
        assertEquals(FinishReason.STOP, chunks.get(1).getFinishReason());
    }

    @Test
    void sendUserMessage_缓存管理器存在时_工具流程缓存工具结果块与continue流块() {
        proxy.setChatDataCacheManager(chatDataCacheManager);
        when(chatDataCacheManager.startCache(eq(sessionId), any())).thenReturn("cache-1");
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("Result text").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(textEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "myTool", "{}", false, "tool result");
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("Result text", result.getContent());
        verify(chatDataCacheManager).startCache(eq(sessionId), any());
        ArgumentCaptor<ChatChunk> captor = ArgumentCaptor.forClass(ChatChunk.class);
        verify(chatDataCacheManager, times(4)).appendChunk(eq("cache-1"), captor.capture());
        List<ChatChunk> chunks = captor.getAllValues();
        assertTrue(chunks.get(0).getHasToolCalls());
        assertTrue(chunks.get(1).getDelta().contains("\"toolName\":\"myTool\""));
        assertTrue(chunks.get(1).getDelta().contains("\"toolId\":\"tid1\""));
        assertTrue(chunks.get(1).getDelta().contains("\"result\":\"tool result\""));
        assertEquals("Result text", chunks.get(2).getDelta());
        assertEquals(FinishReason.STOP, chunks.get(3).getFinishReason());
    }

    @Test
    void sendUserMessage_缓存管理器存在时_工具流程结束块不入缓存且工具终止时追加STOP() {
        proxy.setChatDataCacheManager(chatDataCacheManager);
        when(chatDataCacheManager.startCache(eq(sessionId), any())).thenReturn("cache-1");
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> contTextEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("cont text").hasToolCalls(false).build())
                .build();
        ServerSentEvent<ChatChunk> contEndEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("").finishReason(FinishReason.STOP).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(contTextEvent, contEndEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "myTool", "{}", false, "tool result");
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("cont text", result.getContent());
        ArgumentCaptor<ChatChunk> captor = ArgumentCaptor.forClass(ChatChunk.class);
        verify(chatDataCacheManager, times(4)).appendChunk(eq("cache-1"), captor.capture());
        List<ChatChunk> chunks = captor.getAllValues();
        assertTrue(chunks.get(0).getHasToolCalls());
        assertTrue(chunks.get(1).getDelta().contains("\"toolName\":\"myTool\""));
        assertTrue(chunks.get(1).getDelta().contains("\"toolId\":\"tid1\""));
        assertEquals("cont text", chunks.get(2).getDelta());
        assertEquals(FinishReason.STOP, chunks.get(3).getFinishReason());
    }

    @Test
    void sendUserMessage_缓存管理器存在时_工具振荡保护终止追加STOP结束块() {
        proxy.setChatDataCacheManager(chatDataCacheManager);
        when(chatDataCacheManager.startCache(eq(sessionId), any())).thenReturn("cache-1");
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "repeatedTool", "{\"x\":1}", true, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult statusResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "repeatedTool", "{\"x\":1}", false, "r");
        when(toolExecutionService.getToolStatus(any(), any())).thenReturn(statusResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("", result.getContent());
        verify(toolExecutionService, times(5)).executeTool(any());
        verify(toolExecutionService, never()).continueAfterTools(any());
        ArgumentCaptor<ChatChunk> captor = ArgumentCaptor.forClass(ChatChunk.class);
        verify(chatDataCacheManager, times(7)).appendChunk(eq("cache-1"), captor.capture());
        List<ChatChunk> chunks = captor.getAllValues();
        assertEquals(FinishReason.STOP, chunks.get(chunks.size() - 1).getFinishReason());
    }

    @Test
    void sendUserMessage_缓存管理器存在时_工具等待期间追加空块() {
        proxy.setChatDataCacheManager(chatDataCacheManager);
        when(chatDataCacheManager.startCache(eq(sessionId), any())).thenReturn("cache-1");
        ServerSentEvent<ChatChunk> toolEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().hasToolCalls(true).build())
                .build();
        ServerSentEvent<ChatChunk> textEvent = ServerSentEvent.<ChatChunk>builder()
                .data(ChatChunk.builder().delta("final").hasToolCalls(false).build())
                .build();
        when(chatService.chat(any())).thenReturn(Flux.just(toolEvent));
        when(toolExecutionService.continueAfterTools(any())).thenReturn(Flux.just(textEvent));
        ToolExecutionService.ToolExecutionResult execResult = new ToolExecutionService.ToolExecutionResult(
                "executing", "tid1", "myTool", "{}", false, null);
        when(toolExecutionService.executeTool(any())).thenReturn(execResult);
        ToolExecutionService.ToolStatusResult executingResult = new ToolExecutionService.ToolStatusResult(
                "executing", "tid1", "myTool", "{}", false, null);
        ToolExecutionService.ToolStatusResult doneResult = new ToolExecutionService.ToolStatusResult(
                "done", "tid1", "myTool", "{}", false, "tool result");
        when(toolExecutionService.getToolStatus(any(), any()))
                .thenReturn(executingResult)
                .thenReturn(doneResult)
                .thenReturn(doneResult);

        Message result = proxy.sendUserMessage(sessionId, "Hi", modelId, null);

        assertEquals("final", result.getContent());
        ArgumentCaptor<ChatChunk> captor = ArgumentCaptor.forClass(ChatChunk.class);
        verify(chatDataCacheManager, times(5)).appendChunk(eq("cache-1"), captor.capture());
        List<ChatChunk> chunks = captor.getAllValues();
        assertTrue(chunks.get(0).getHasToolCalls());
        assertEquals("", chunks.get(1).getDelta());
        assertTrue(chunks.get(2).getDelta().contains("\"toolName\":\"myTool\""));
        assertTrue(chunks.get(2).getDelta().contains("\"toolId\":\"tid1\""));
        assertEquals("final", chunks.get(3).getDelta());
        assertEquals(FinishReason.STOP, chunks.get(4).getFinishReason());
    }

    private static Object getPrivateField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
