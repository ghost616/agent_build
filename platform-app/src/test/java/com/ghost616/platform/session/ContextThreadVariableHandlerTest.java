package com.ghost616.platform.session;

import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.platform.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextThreadVariableHandler 单元测试（由原用户上下文线程变量处理器测试迁移并扩展）。
 *
 * <p>验证 wrap() 捕获「用户会话 + 评估执行上下文」两类快照、apply() 在目标线程恢复/清理、
 * clear() 清理当前线程上下文的语义，以及异步线程通过 wrapper 同时传播用户上下文与评估
 * 执行上下文（执行标记跨线程可见）的能力。</p>
 */
class ContextThreadVariableHandlerTest {

    private final ContextThreadVariableHandler handler = new ContextThreadVariableHandler();

    @AfterEach
    void tearDown() {
        UserContext.clear();
        EvaluationExecutionContext.clear();
    }

    private UserSession newSession(Long userId) {
        User user = new User();
        user.setId(userId);
        return new UserSession("ctx-test", user, System.currentTimeMillis());
    }

    @Test
    void wrap_捕获当前用户会话与评估执行上下文_apply恢复() {
        UserSession session = newSession(42L);
        EvaluationExecutionContext evaluationContext = EvaluationExecutionContext.create();
        UserContext.set(session);
        EvaluationExecutionContext.set(evaluationContext);

        ThreadVariableWrapper wrapper = handler.wrap();
        assertNotNull(wrapper);

        UserContext.clear();
        EvaluationExecutionContext.clear();
        wrapper.apply();

        assertSame(session, UserContext.get(), "apply() 应恢复 wrap() 时捕获的会话");
        assertSame(evaluationContext, EvaluationExecutionContext.get(), "apply() 应恢复 wrap() 时捕获的评估执行上下文");
        assertEquals(42L, UserContext.get().getUser().getId());
    }

    @Test
    void wrap_无用户上下文无评估上下文_apply清空目标线程残留上下文() {
        UserContext.clear();
        EvaluationExecutionContext.clear();
        ThreadVariableWrapper wrapper = handler.wrap();
        assertNotNull(wrapper);

        // 目标线程已有残留上下文（线程复用场景）
        UserContext.set(newSession(7L));
        EvaluationExecutionContext.set(EvaluationExecutionContext.create());
        wrapper.apply();

        assertNull(UserContext.get(), "捕获为 null 时 apply() 应清空当前线程用户上下文");
        assertNull(EvaluationExecutionContext.get(), "捕获为 null 时 apply() 应清空当前线程评估执行上下文");
    }

    @Test
    void apply_清空主线程后_异步线程通过wrapper恢复用户与评估上下文() throws Exception {
        UserSession session = newSession(99L);
        EvaluationExecutionContext evaluationContext = EvaluationExecutionContext.create();
        UserContext.set(session);
        EvaluationExecutionContext.set(evaluationContext);
        ThreadVariableWrapper wrapper = handler.wrap();
        UserContext.clear();
        EvaluationExecutionContext.clear();

        AtomicReference<UserSession> seenSession = new AtomicReference<>();
        AtomicReference<EvaluationExecutionContext> seenEvalCtx = new AtomicReference<>();
        Thread t = new Thread(() -> {
            wrapper.apply();
            try {
                seenSession.set(UserContext.get());
                seenEvalCtx.set(EvaluationExecutionContext.get());
            } finally {
                wrapper.clear();
            }
        });
        t.start();
        t.join(5000);

        assertFalse(t.isAlive(), "异步线程应正常结束");
        assertSame(session, seenSession.get(), "异步线程应能读到捕获的用户会话");
        assertSame(evaluationContext, seenEvalCtx.get(), "异步线程应能读到捕获的评估执行上下文");
        assertNull(UserContext.get(), "主线程用户上下文不应被异步线程污染");
        assertNull(EvaluationExecutionContext.get(), "主线程评估执行上下文不应被异步线程污染");
    }

    @Test
    void 多次apply_可重复恢复() {
        UserSession session = newSession(5L);
        EvaluationExecutionContext evaluationContext = EvaluationExecutionContext.create();
        UserContext.set(session);
        EvaluationExecutionContext.set(evaluationContext);
        ThreadVariableWrapper wrapper = handler.wrap();
        UserContext.clear();
        EvaluationExecutionContext.clear();

        wrapper.apply();
        assertSame(session, UserContext.get());
        assertSame(evaluationContext, EvaluationExecutionContext.get());
        UserContext.clear();
        EvaluationExecutionContext.clear();
        wrapper.apply();
        assertSame(session, UserContext.get());
        assertSame(evaluationContext, EvaluationExecutionContext.get());
    }

    @Test
    void clear_清空已恢复的用户与评估执行上下文() {
        UserSession session = newSession(42L);
        EvaluationExecutionContext evaluationContext = EvaluationExecutionContext.create();
        UserContext.set(session);
        EvaluationExecutionContext.set(evaluationContext);
        ThreadVariableWrapper wrapper = handler.wrap();
        UserContext.clear();
        EvaluationExecutionContext.clear();

        wrapper.apply();
        assertSame(session, UserContext.get());
        assertSame(evaluationContext, EvaluationExecutionContext.get());

        wrapper.clear();

        assertNull(UserContext.get(), "clear() 后当前线程 UserContext 应被清空");
        assertNull(EvaluationExecutionContext.get(), "clear() 后当前线程评估执行上下文应被清空");
    }

    @Test
    void clear_捕获为空时_清空线程残留上下文() {
        UserContext.clear();
        EvaluationExecutionContext.clear();
        ThreadVariableWrapper wrapper = handler.wrap();

        // 模拟共享线程残留上下文场景
        UserContext.set(newSession(7L));
        EvaluationExecutionContext.set(EvaluationExecutionContext.create());
        wrapper.clear();

        assertNull(UserContext.get(), "clear() 应清空当前线程残留的用户上下文");
        assertNull(EvaluationExecutionContext.get(), "clear() 应清空当前线程残留的评估执行上下文");
    }

    @Test
    void clear_不破坏wrapper快照_可再次apply恢复() {
        UserSession session = newSession(88L);
        EvaluationExecutionContext evaluationContext = EvaluationExecutionContext.create();
        UserContext.set(session);
        EvaluationExecutionContext.set(evaluationContext);
        ThreadVariableWrapper wrapper = handler.wrap();
        UserContext.clear();
        EvaluationExecutionContext.clear();

        wrapper.apply();
        assertSame(session, UserContext.get());
        assertSame(evaluationContext, EvaluationExecutionContext.get());

        wrapper.clear();
        assertNull(UserContext.get());
        assertNull(EvaluationExecutionContext.get());

        wrapper.apply();
        assertSame(session, UserContext.get(), "clear() 后 apply() 仍可基于捕获快照恢复用户上下文");
        assertSame(evaluationContext, EvaluationExecutionContext.get(),
                "clear() 后 apply() 仍可基于捕获快照恢复评估执行上下文");
    }
}
