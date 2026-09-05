package com.ghost616.platform.session;

import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import org.springframework.stereotype.Component;

/**
 * 用户上下文与评估执行上下文线程变量处理器。
 *
 * <p>实现 agent-base 的 {@link ThreadVariableHandler}，统一捕获/恢复「用户上下文
 * ({@link UserContext} 的 {@link UserSession}) + 评估执行上下文 ({@link EvaluationExecutionContext})」
 * 两类线程变量：在提交异步任务（如工具执行的 {@code CompletableFuture.supplyAsync}、
 * 评估子会话后台驱动、消息分发）前通过 {@link #wrap()} 捕获当前线程的两类上下文快照，
 * 异步线程开始执行时通过 {@link ContextThreadVariableWrapper#apply()} 恢复；
 * 在 reactor 等共享线程的异步回调中，恢复后通过 {@link ContextThreadVariableWrapper#clear()}
 * 清理当前线程上下文，防止线程复用导致会话串号。保证异步场景下
 * {@link UserContext#get()} 仍可取到当前登录用户、评估执行标记与待处理消息跨线程可见。</p>
 */
@Component
public class ContextThreadVariableHandler implements ThreadVariableHandler {

    @Override
    public ThreadVariableWrapper wrap() {
        UserSession session = UserContext.get();
        EvaluationExecutionContext evaluationContext = EvaluationExecutionContext.get();
        return new ContextThreadVariableWrapper(session, evaluationContext);
    }

    /**
     * 上下文线程变量包装器，承载捕获的用户会话快照与评估执行上下文快照。
     */
    private static class ContextThreadVariableWrapper implements ThreadVariableWrapper {

        private final UserSession session;
        private final EvaluationExecutionContext evaluationContext;

        ContextThreadVariableWrapper(UserSession session, EvaluationExecutionContext evaluationContext) {
            this.session = session;
            this.evaluationContext = evaluationContext;
        }

        @Override
        public void apply() {
            if (session != null) {
                UserContext.set(session);
            } else {
                UserContext.clear();
            }
            if (evaluationContext != null) {
                EvaluationExecutionContext.set(evaluationContext);
            } else {
                EvaluationExecutionContext.clear();
            }
        }

        @Override
        public void clear() {
            UserContext.clear();
            EvaluationExecutionContext.clear();
        }
    }
}
