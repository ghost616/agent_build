package com.ghost616.platform.service.agent;

import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.enums.SubSessionOpenMode;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultSubSessionCallbackTest {

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private AgentConfigMapper agentConfigMapper;

    @Mock
    private SubSessionRunningCache subSessionRunningCache;

    private DefaultSubSessionCallback callback;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        callback = new DefaultSubSessionCallback(sessionMapper, agentConfigMapper, subSessionRunningCache);
    }

    @Test
    void executeShouldBlockAndReturnResult() throws Exception {
        Long sessionId = 100L;
        Long parentSessionId = 10L;
        String userMessage = "test message";
        Message expectedMessage = Message.builder().role("assistant").content("response").build();

        Session session = mock(Session.class);
        when(session.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        CompletableFuture<Message> futureResult = CompletableFuture.supplyAsync(
                () -> callback.execute(null, String.valueOf(sessionId), userMessage, null), executor);

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);
        assertEquals(sessionId, data.getChildSessionId());
        assertEquals(userMessage, data.getUserMessage());
        assertNull(data.getThinking());

        data.getMessageResult().complete(expectedMessage);

        Message actual = futureResult.get(3, TimeUnit.SECONDS);
        assertEquals(expectedMessage, actual);
    }

    @Test
    void executeWithThinkingTrueShouldStoreThinking() throws Exception {
        Long sessionId = 800L;
        Long parentSessionId = 80L;
        String userMessage = "thinking test";

        Session session = mock(Session.class);
        when(session.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        CompletableFuture<Message> futureResult = CompletableFuture.supplyAsync(
                () -> callback.execute(null, String.valueOf(sessionId), userMessage, true), executor);

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);
        assertEquals(sessionId, data.getChildSessionId());
        assertEquals(true, data.getThinking());

        data.getMessageResult().complete(Message.builder().role("assistant").content("thinking done").build());
        futureResult.get(3, TimeUnit.SECONDS);
    }

    @Test
    void executeWithNullParentSessionIdReturnsNull() {
        Long sessionId = 200L;

        Session session = mock(Session.class);
        when(session.getParentSessionId()).thenReturn(null);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        Message result = callback.execute(null, String.valueOf(sessionId), "no parent", null);
        assertNull(result);
    }

    @Test
    void executeWithSessionNotFoundReturnsNull() {
        Long sessionId = 300L;
        when(sessionMapper.selectById(sessionId)).thenReturn(null);

        Message result = callback.execute(null, String.valueOf(sessionId), "not found", null);
        assertNull(result);
    }

    @Test
    void executeShouldRemoveEntryAfterCompletion() throws Exception {
        Long sessionId = 400L;
        Long parentSessionId = 40L;

        Session session = mock(Session.class);
        when(session.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        CompletableFuture<Message> futureResult = CompletableFuture.supplyAsync(
                () -> callback.execute(null, String.valueOf(sessionId), "cleanup test", null), executor);

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);

        data.getMessageResult().complete(Message.builder().role("assistant").content("done").build());
        futureResult.get(3, TimeUnit.SECONDS);

        assertNull(callback.getSubSessionData(parentSessionId));
    }

    @Test
    void getSubSessionDataShouldReturnNullWhenNoData() {
        assertNull(callback.getSubSessionData(999L));
    }

    @Test
    void getSubSessionDataShouldReturnCorrectData() throws Exception {
        Long sessionId = 600L;
        Long parentSessionId = 60L;
        String userMessage = "data test";
        Message expectedMessage = Message.builder().role("user").content("hello").build();

        Session session = mock(Session.class);
        when(session.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        CompletableFuture<Message> futureResult = CompletableFuture.supplyAsync(
                () -> callback.execute(null, String.valueOf(sessionId), userMessage, null), executor);

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);
        assertEquals(sessionId, data.getChildSessionId());
        assertEquals(userMessage, data.getUserMessage());
        assertNull(data.getThinking());
        assertFalse(data.getMessageResult().isDone());

        data.getMessageResult().complete(expectedMessage);
        Message actual = futureResult.get(3, TimeUnit.SECONDS);
        assertEquals(expectedMessage, actual);
    }

    @Test
    void executeShouldThrowOnInterruption() {
        Long sessionId = 700L;
        Long parentSessionId = 70L;

        Session session = mock(Session.class);
        when(session.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session);

        Thread testThread = new Thread(() -> {
            try {
                callback.execute(null, String.valueOf(sessionId), "interrupt test", null);
                fail("Should have thrown exception");
            } catch (RuntimeException e) {
                assertTrue(e.getMessage().contains("interrupted"));
            }
        });
        testThread.start();

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);

        testThread.interrupt();
        try {
            testThread.join(3000);
        } catch (InterruptedException e) {
            fail("Test thread join interrupted");
        }
    }

    @Test
    void executeWithWebSocketModeShouldSendMessageAndReturnImmediately() {
        Long sessionId = 900L;
        Long parentSessionId = 90L;
        Long mainSessionId = 9L;
        Long agentId = 5L;
        String userMessage = "websocket message";

        Session childSession = mock(Session.class);
        when(childSession.getParentSessionId()).thenReturn(parentSessionId);
        when(childSession.getTitle()).thenReturn("子会话A");
        when(sessionMapper.selectById(sessionId)).thenReturn(childSession);

        Session mainSession = mock(Session.class);
        when(mainSession.getParentSessionId()).thenReturn(null);
        when(mainSession.getAgentId()).thenReturn(agentId);
        Session intermediateSession = mock(Session.class);
        when(intermediateSession.getParentSessionId()).thenReturn(mainSessionId);
        when(sessionMapper.selectById(parentSessionId)).thenReturn(intermediateSession);
        when(sessionMapper.selectById(mainSessionId)).thenReturn(mainSession);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSubSessionOpenMode(SubSessionOpenMode.WEBSOCKET);
        when(agentConfigMapper.selectById(agentId)).thenReturn(agentConfig);

        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(ctx.getModelId()).thenReturn("model-1");

        Message result = callback.execute(ctx, String.valueOf(sessionId), userMessage, true);

        assertNotNull(result);
        assertEquals("已发送消息到子会话子会话A，请等候子会话返回消息", result.getContent());
        verify(ctx).sendUserMessage(String.valueOf(sessionId), userMessage, "model-1", true);
        verify(subSessionRunningCache).add(sessionId);
        assertNull(callback.getSubSessionData(parentSessionId));
        verify(agentConfigMapper).selectById(agentId);
    }

    @Test
    void executeWithWebSocketModeAndNullTitleUsesSessionId() {
        Long sessionId = 901L;
        Long parentSessionId = 91L;
        Long mainSessionId = 19L;
        Long agentId = 6L;
        String userMessage = "no title message";

        Session childSession = mock(Session.class);
        when(childSession.getParentSessionId()).thenReturn(parentSessionId);
        when(childSession.getTitle()).thenReturn(null);
        when(sessionMapper.selectById(sessionId)).thenReturn(childSession);

        Session mainSession = mock(Session.class);
        when(mainSession.getParentSessionId()).thenReturn(null);
        when(mainSession.getAgentId()).thenReturn(agentId);
        Session intermediateSession = mock(Session.class);
        when(intermediateSession.getParentSessionId()).thenReturn(mainSessionId);
        when(sessionMapper.selectById(parentSessionId)).thenReturn(intermediateSession);
        when(sessionMapper.selectById(mainSessionId)).thenReturn(mainSession);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSubSessionOpenMode(SubSessionOpenMode.WEBSOCKET);
        when(agentConfigMapper.selectById(agentId)).thenReturn(agentConfig);

        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(ctx.getModelId()).thenReturn("model-2");

        Message result = callback.execute(ctx, String.valueOf(sessionId), userMessage, false);

        assertEquals("已发送消息到子会话" + sessionId + "，请等候子会话返回消息", result.getContent());
        verify(ctx).sendUserMessage(String.valueOf(sessionId), userMessage, "model-2", false);
        verify(subSessionRunningCache).add(sessionId);
        assertNull(callback.getSubSessionData(parentSessionId));
    }

    @Test
    void executeWithToolCallModeShouldBlockAndReturnResult() throws Exception {
        Long sessionId = 902L;
        Long parentSessionId = 92L;
        Long mainSessionId = 29L;
        Long agentId = 7L;
        String userMessage = "tool call message";
        Message expectedMessage = Message.builder().role("assistant").content("tool call response").build();

        Session childSession = mock(Session.class);
        when(childSession.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(childSession);

        Session mainSession = mock(Session.class);
        when(mainSession.getParentSessionId()).thenReturn(null);
        when(mainSession.getAgentId()).thenReturn(agentId);
        Session intermediateSession = mock(Session.class);
        when(intermediateSession.getParentSessionId()).thenReturn(mainSessionId);
        when(sessionMapper.selectById(parentSessionId)).thenReturn(intermediateSession);
        when(sessionMapper.selectById(mainSessionId)).thenReturn(mainSession);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSubSessionOpenMode(SubSessionOpenMode.TOOL_CALL);
        when(agentConfigMapper.selectById(agentId)).thenReturn(agentConfig);

        AgentExecutionContext ctx = mock(AgentExecutionContext.class);

        CompletableFuture<Message> futureResult = CompletableFuture.supplyAsync(
                () -> callback.execute(ctx, String.valueOf(sessionId), userMessage, null), executor);

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);
        assertEquals(sessionId, data.getChildSessionId());
        assertEquals(userMessage, data.getUserMessage());

        data.getMessageResult().complete(expectedMessage);
        Message actual = futureResult.get(3, TimeUnit.SECONDS);
        assertEquals(expectedMessage, actual);
        verify(ctx, never()).sendUserMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void executeWithMissingAgentConfigFallsBackToToolCall() throws Exception {
        Long sessionId = 903L;
        Long parentSessionId = 93L;
        Long mainSessionId = 39L;
        Long agentId = 8L;
        String userMessage = "missing config message";
        Message expectedMessage = Message.builder().role("assistant").content("fallback response").build();

        Session childSession = mock(Session.class);
        when(childSession.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(childSession);

        Session mainSession = mock(Session.class);
        when(mainSession.getParentSessionId()).thenReturn(null);
        when(mainSession.getAgentId()).thenReturn(agentId);
        Session intermediateSession = mock(Session.class);
        when(intermediateSession.getParentSessionId()).thenReturn(mainSessionId);
        when(sessionMapper.selectById(parentSessionId)).thenReturn(intermediateSession);
        when(sessionMapper.selectById(mainSessionId)).thenReturn(mainSession);

        when(agentConfigMapper.selectById(agentId)).thenReturn(null);

        AgentExecutionContext ctx = mock(AgentExecutionContext.class);

        CompletableFuture<Message> futureResult = CompletableFuture.supplyAsync(
                () -> callback.execute(ctx, String.valueOf(sessionId), userMessage, null), executor);

        DefaultSubSessionCallback.SubSessionData data = waitForMapEntry(parentSessionId);
        assertNotNull(data);

        data.getMessageResult().complete(expectedMessage);
        Message actual = futureResult.get(3, TimeUnit.SECONDS);
        assertEquals(expectedMessage, actual);
        verify(ctx, never()).sendUserMessage(anyString(), anyString(), any(), any());
    }

    @Test
    void executeWithWebSocketModeAndRunningCacheHitReturnsErrorJson() {
        Long sessionId = 950L;
        Long parentSessionId = 95L;
        Long mainSessionId = 59L;
        Long agentId = 15L;
        String userMessage = "duplicate send";

        Session childSession = mock(Session.class);
        when(childSession.getParentSessionId()).thenReturn(parentSessionId);
        when(sessionMapper.selectById(sessionId)).thenReturn(childSession);

        Session mainSession = mock(Session.class);
        when(mainSession.getParentSessionId()).thenReturn(null);
        when(mainSession.getAgentId()).thenReturn(agentId);
        Session intermediateSession = mock(Session.class);
        when(intermediateSession.getParentSessionId()).thenReturn(mainSessionId);
        when(sessionMapper.selectById(parentSessionId)).thenReturn(intermediateSession);
        when(sessionMapper.selectById(mainSessionId)).thenReturn(mainSession);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSubSessionOpenMode(SubSessionOpenMode.WEBSOCKET);
        when(agentConfigMapper.selectById(agentId)).thenReturn(agentConfig);

        // 子会话正在执行中：命中运行缓存
        when(subSessionRunningCache.contains(sessionId)).thenReturn(true);

        AgentExecutionContext ctx = mock(AgentExecutionContext.class);

        Message result = callback.execute(ctx, String.valueOf(sessionId), userMessage, true);

        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertEquals("{\"status\":\"error\",\"errMsg\":\"子会话正在运行，请等候\"}", result.getContent());
        // 命中缓存：不推送消息、不写入运行缓存
        verify(ctx, never()).sendUserMessage(anyString(), anyString(), any(), any());
        verify(subSessionRunningCache, never()).add(sessionId);
        assertNull(callback.getSubSessionData(parentSessionId));
    }

    @Test
    void executeWithWebSocketModeAndRunningCacheMissAddsToCache() {
        Long sessionId = 951L;
        Long parentSessionId = 96L;
        Long mainSessionId = 69L;
        Long agentId = 16L;
        String userMessage = "first send";

        Session childSession = mock(Session.class);
        when(childSession.getParentSessionId()).thenReturn(parentSessionId);
        when(childSession.getTitle()).thenReturn("子会话B");
        when(sessionMapper.selectById(sessionId)).thenReturn(childSession);

        Session mainSession = mock(Session.class);
        when(mainSession.getParentSessionId()).thenReturn(null);
        when(mainSession.getAgentId()).thenReturn(agentId);
        Session intermediateSession = mock(Session.class);
        when(intermediateSession.getParentSessionId()).thenReturn(mainSessionId);
        when(sessionMapper.selectById(parentSessionId)).thenReturn(intermediateSession);
        when(sessionMapper.selectById(mainSessionId)).thenReturn(mainSession);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setSubSessionOpenMode(SubSessionOpenMode.WEBSOCKET);
        when(agentConfigMapper.selectById(agentId)).thenReturn(agentConfig);

        // 子会话未在执行中：未命中运行缓存
        when(subSessionRunningCache.contains(sessionId)).thenReturn(false);

        AgentExecutionContext ctx = mock(AgentExecutionContext.class);
        when(ctx.getModelId()).thenReturn("model-3");

        Message result = callback.execute(ctx, String.valueOf(sessionId), userMessage, false);

        assertNotNull(result);
        assertEquals("已发送消息到子会话子会话B，请等候子会话返回消息", result.getContent());
        // 未命中：记入运行缓存并正常推送
        verify(subSessionRunningCache).add(sessionId);
        verify(ctx).sendUserMessage(String.valueOf(sessionId), userMessage, "model-3", false);
        assertNull(callback.getSubSessionData(parentSessionId));
    }

    // ========== exists 子会话存在性校验 ==========

    @Test
    void exists_会话存在_返回true() {
        Long childSessionId = 500L;
        when(sessionMapper.selectById(childSessionId)).thenReturn(mock(Session.class));

        assertTrue(callback.exists(String.valueOf(childSessionId)));
        verify(sessionMapper).selectById(childSessionId);
    }

    @Test
    void exists_会话不存在_返回false() {
        Long childSessionId = 501L;
        when(sessionMapper.selectById(childSessionId)).thenReturn(null);

        assertFalse(callback.exists(String.valueOf(childSessionId)));
        verify(sessionMapper).selectById(childSessionId);
    }

    @Test
    void exists_软删会话_selectById返回null_返回false() {
        // Session @TableLogic 使已假删（deleted=1）会话 selectById 自动返回 null
        Long childSessionId = 502L;
        when(sessionMapper.selectById(childSessionId)).thenReturn(null);

        assertFalse(callback.exists(String.valueOf(childSessionId)));
        verify(sessionMapper).selectById(childSessionId);
    }

    @Test
    void exists_会话ID为null或空白_返回false() {
        assertFalse(callback.exists(null));
        assertFalse(callback.exists(""));
        assertFalse(callback.exists("   "));
        verify(sessionMapper, never()).selectById(any());
    }

    @Test
    void exists_无效ID格式_返回false() {
        assertFalse(callback.exists("not-a-number"));
        verify(sessionMapper, never()).selectById(any());
    }

    private DefaultSubSessionCallback.SubSessionData waitForMapEntry(Long key) {
        for (int i = 0; i < 50; i++) {
            DefaultSubSessionCallback.SubSessionData data = callback.getSubSessionData(key);
            if (data != null) {
                return data;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }
}
