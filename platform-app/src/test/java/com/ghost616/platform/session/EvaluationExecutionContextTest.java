package com.ghost616.platform.session;

import com.ghost616.agentbase.sendmessage.SendUserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评估执行上下文（{@link EvaluationExecutionContext}）单元测试。
 *
 * <p>覆盖：执行标记生命周期（入口设/结束清）、待处理 SendUserMessage 列表追加保序（FIFO）
 * 与取出即移除、多值并发追加不丢、空列表、线程隔离与跨线程传播（结合
 * ContextThreadVariableHandler 传播到异步线程）。</p>
 */
class EvaluationExecutionContextTest {

    @AfterEach
    void tearDown() {
        EvaluationExecutionContext.clear();
    }

    private SendUserMessage message(String target, String content) {
        return new SendUserMessage(target, content, "conv-1", List.of("main-1"));
    }

    @Test
    void createAndSet_执行标记生效() {
        assertFalse(EvaluationExecutionContext.isEvaluation());

        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        assertTrue(EvaluationExecutionContext.isEvaluation());
        assertSame(context, EvaluationExecutionContext.get());
    }

    @Test
    void clear_解除执行标记并清理() {
        EvaluationExecutionContext.set(EvaluationExecutionContext.create());

        EvaluationExecutionContext.clear();

        assertFalse(EvaluationExecutionContext.isEvaluation());
        assertNull(EvaluationExecutionContext.get());
    }

    @Test
    void poll_列表为空_返回null() {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        assertNull(context.pollNextPendingSendUserMessage());
        assertNull(context.pollNextPendingSendUserMessage(), "空列表重复读取仍为 null");
    }

    @Test
    void add与poll_取出即移除() {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        context.addPendingSendUserMessage(message("300", "hello"));

        SendUserMessage taken = context.pollNextPendingSendUserMessage();
        assertNotNull(taken);
        assertEquals("300", taken.getSessionId());
        assertEquals("hello", taken.getContent());
        assertNull(context.pollNextPendingSendUserMessage(), "取出即移除：再次读取应为 null");
    }

    @Test
    void add_多值追加保序_FIFO消费不丢() {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        context.addPendingSendUserMessage(message("300", "first"));
        context.addPendingSendUserMessage(message("301", "second"));
        context.addPendingSendUserMessage(message("302", "third"));

        assertEquals("300", context.pollNextPendingSendUserMessage().getSessionId());
        assertEquals("301", context.pollNextPendingSendUserMessage().getSessionId());
        assertEquals("302", context.pollNextPendingSendUserMessage().getSessionId());
        assertNull(context.pollNextPendingSendUserMessage(), "消费完所有追加消息后列表为空");
    }

    @Test
    void add_并发追加不丢_全部可消费() throws Exception {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        int producers = 8;
        int perProducer = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(producers);
        AtomicBoolean threadError = new AtomicBoolean(false);
        for (int p = 0; p < producers; p++) {
            int producer = p;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perProducer; i++) {
                        context.addPendingSendUserMessage(message("300", producer + "-" + i));
                    }
                } catch (Exception e) {
                    threadError.set(true);
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }
        start.countDown();
        done.await();

        assertFalse(threadError.get(), "并发追加线程不应抛异常");
        List<SendUserMessage> drained = new ArrayList<>();
        SendUserMessage taken;
        while ((taken = context.pollNextPendingSendUserMessage()) != null) {
            drained.add(taken);
        }

        assertEquals(producers * perProducer, drained.size(), "并发追加的消息不应丢失");
        Set<String> distinct = drained.stream()
                .map(m -> m.getContent())
                .collect(Collectors.toSet());
        assertEquals(producers * perProducer, distinct.size(), "并发追加的消息不应重复/缺失");
    }

    @Test
    void 执行标记线程隔离() throws Exception {
        EvaluationExecutionContext.set(EvaluationExecutionContext.create());

        AtomicReference<Boolean> otherThreadSees = new AtomicReference<>(null);
        Thread other = new Thread(() -> otherThreadSees.set(EvaluationExecutionContext.isEvaluation()));
        other.start();
        other.join(5000);

        assertFalse(other.isAlive(), "异步线程应正常结束");
        assertEquals(Boolean.FALSE, otherThreadSees.get(), "评估执行上下文不应泄漏到其它线程");
        assertTrue(EvaluationExecutionContext.isEvaluation(), "原线程执行标记不受影响");
    }
}
