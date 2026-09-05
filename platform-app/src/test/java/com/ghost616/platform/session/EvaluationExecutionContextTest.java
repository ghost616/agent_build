package com.ghost616.platform.session;

import com.ghost616.agentbase.sendmessage.SendUserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评估执行上下文（{@link EvaluationExecutionContext}）单元测试。
 *
 * <p>覆盖：执行标记生命周期（入口设/结束清）、SendUserMessage 槽位写入覆盖与取出即清、
 * 线程隔离与跨线程传播（结合 ContextThreadVariableHandler 传播到异步线程）。</p>
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
    void getAndClear_槽位为空_返回null() {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        assertNull(context.getAndClearPendingSendUserMessage());
    }

    @Test
    void setPending与getAndClear_取出即清() {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        context.setPendingSendUserMessage(message("300", "hello"));

        SendUserMessage taken = context.getAndClearPendingSendUserMessage();
        assertNotNull(taken);
        assertEquals("300", taken.getSessionId());
        assertEquals("hello", taken.getContent());
        assertNull(context.getAndClearPendingSendUserMessage(), "取出即清：再次读取应为 null");
    }

    @Test
    void setPending_写入覆盖当前槽位() {
        EvaluationExecutionContext context = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(context);

        context.setPendingSendUserMessage(message("300", "first"));
        context.setPendingSendUserMessage(message("301", "second"));

        SendUserMessage taken = context.getAndClearPendingSendUserMessage();
        assertEquals("301", taken.getSessionId());
        assertEquals("second", taken.getContent());
        assertNull(context.getAndClearPendingSendUserMessage());
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
