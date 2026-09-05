package com.ghost616.platform.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.sendmessage.MessageDefinition;
import com.ghost616.agentbase.sendmessage.SendUserMessage;
import com.ghost616.platform.entity.User;
import com.ghost616.platform.session.ContextThreadVariableHandler;
import com.ghost616.platform.session.EvaluationExecutionContext;
import com.ghost616.platform.session.UserContext;
import com.ghost616.platform.session.UserSession;
import com.ghost616.platform.websocket.SessionConnectionRegistry;
import com.ghost616.platform.websocket.WebSocketPushService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultMessageSenderTest {

    @Mock
    private WebSocketPushService pushService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
        EvaluationExecutionContext.clear();
    }

    private DefaultMessageSender newSender() {
        return new DefaultMessageSender(pushService, new ContextThreadVariableHandler());
    }

    private UserSession newUserSession(String sessionId) {
        User user = new User();
        user.setId(42L);
        return new UserSession(sessionId, user, System.currentTimeMillis());
    }

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("等待异步执行超时");
    }

    @Test
    void send_SEND_USER_MESSAGE_推送一次() {
        SendUserMessage message = new SendUserMessage("child-1", "hello", "conv-1", List.of("parent-1", "main-1"));
        DefaultMessageSender sender = newSender();

        sender.send(message);

        verify(pushService, timeout(2000)).pushToSession(message);
    }

    @Test
    void send_未知消息类型_静默跳过不推送() throws Exception {
        MessageDefinition unknown = () -> "UNKNOWN_TYPE";
        DefaultMessageSender sender = newSender();

        sender.send(unknown);

        Thread.sleep(200);
        verifyNoInteractions(pushService);
    }

    @Test
    void send_null消息_静默忽略() throws Exception {
        DefaultMessageSender sender = newSender();

        sender.send(null);

        Thread.sleep(100);
        verifyNoInteractions(pushService);
    }

    @Test
    void send_携带UserContext_异步线程恢复上下文() throws Exception {
        UserSession snapshot = newUserSession("usr-1");
        UserContext.set(snapshot);
        RecordingPushService recording = new RecordingPushService();
        DefaultMessageSender sender = new DefaultMessageSender(recording, new ContextThreadVariableHandler());

        sender.send(new SendUserMessage("child-1", "hello", "conv-1", List.of("parent-1", "main-1")));

        awaitUntil(() -> recording.seenSession != null);
        assertEquals(snapshot, recording.seenSession);
    }

    @Test
    void send_无UserContext_异步线程无上下文() throws Exception {
        RecordingPushService recording = new RecordingPushService();
        DefaultMessageSender sender = new DefaultMessageSender(recording, new ContextThreadVariableHandler());

        sender.send(new SendUserMessage("child-1", "hello", "conv-1", List.of("parent-1", "main-1")));

        awaitUntil(() -> recording.called);
        assertNull(recording.seenSession);
    }

    // ========== 评估拦截仅针对 SendUserMessage（写入评估执行上下文槽位、不推送、不调度异步）；
    // 其余消息（含评估中的非 SendUserMessage）一律走统一异步分发通道 ==========

    @Test
    void send_评估执行标记生效_SendUserMessage写入槽位且不推送() throws Exception {
        EvaluationExecutionContext.set(EvaluationExecutionContext.create());
        SendUserMessage message = new SendUserMessage("child-1", "hello", "conv-1", List.of("parent-1", "main-1"));
        AtomicBoolean asyncEntered = new AtomicBoolean();
        ThreadVariableHandler recording = () -> new ThreadVariableWrapper() {
            @Override
            public void apply() {
                asyncEntered.set(true);
            }
        };
        DefaultMessageSender sender = new DefaultMessageSender(pushService, recording);

        sender.send(message);

        // 同步拦截：写入当前线程评估执行上下文槽位（取出即清），不调度异步执行通道、不触发 WebSocket 推送
        EvaluationExecutionContext evalContext = EvaluationExecutionContext.get();
        assertNotNull(evalContext);
        SendUserMessage taken = evalContext.getAndClearPendingSendUserMessage();
        assertSame(message, taken);
        assertNull(evalContext.getAndClearPendingSendUserMessage());
        Thread.sleep(100);
        assertFalse(asyncEntered.get(), "评估拦截 SendUserMessage 不应调度异步执行");
        verifyNoInteractions(pushService);
    }

    @Test
    void send_评估执行标记生效_未知消息类型进入异步分发通道不被吞() throws Exception {
        EvaluationExecutionContext.set(EvaluationExecutionContext.create());
        MessageDefinition unknown = () -> "UNKNOWN_TYPE";
        AtomicBoolean asyncEntered = new AtomicBoolean();
        ThreadVariableHandler recording = () -> new ThreadVariableWrapper() {
            @Override
            public void apply() {
                asyncEntered.set(true);
            }
        };
        DefaultMessageSender sender = new DefaultMessageSender(pushService, recording);

        sender.send(unknown);

        // 评估下非 SendUserMessage 不再被同步吞掉：正常进入异步执行通道
        // （dispatch 对未支持类型仅 log.debug，不推送），不写入评估执行上下文槽位
        awaitUntil(asyncEntered::get);
        EvaluationExecutionContext evalContext = EvaluationExecutionContext.get();
        assertNotNull(evalContext);
        assertNull(evalContext.getAndClearPendingSendUserMessage());
        Thread.sleep(100);
        verifyNoInteractions(pushService);
    }

    @Test
    void send_非评估执行_仍推送_回归() {
        SendUserMessage message = new SendUserMessage("child-1", "hello", "conv-1", List.of("parent-1", "main-1"));
        DefaultMessageSender sender = newSender();

        sender.send(message);

        verify(pushService, timeout(2000)).pushToSession(message);
    }

    /**
     * 记录异步线程内 UserContext 状态的推送服务替身。
     */
    private static class RecordingPushService extends WebSocketPushService {

        volatile boolean called;
        volatile UserSession seenSession;

        RecordingPushService() {
            super(mock(SessionConnectionRegistry.class), new ObjectMapper());
        }

        @Override
        public void pushToSession(Object payload) {
            called = true;
            seenSession = UserContext.get();
        }
    }
}
