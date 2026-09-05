package com.ghost616.platform.session;

import com.ghost616.agentbase.sendmessage.SendUserMessage;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 评估执行上下文：承载单次「评估后台执行」运行期的执行标记与待处理 {@link SendUserMessage} 槽位。
 *
 * <p>由 {@link ContextThreadVariableHandler} 统一传播：评估执行入口
 * （{@code AsyncEvaluationExecutor.executeAsync}）在当前线程创建并写入实例，
 * 各跨线程提交点（工具异步线程、子会话后台驱动线程）提交前通过
 * {@code ThreadVariableHandler.wrap()} 捕获当前线程的实例快照，异步线程开始执行时
 * {@code apply()} 恢复、finally 中清理，保证评估执行标记与待处理消息跨线程可见且不串号/泄漏。</p>
 *
 * <p>槽位语义：单值即可——写入覆盖当前槽位，取出即清空，由
 * {@link com.ghost616.platform.service.evaluation.AsyncEvaluationExecutor} 的消息驱动循环消费。</p>
 */
public final class EvaluationExecutionContext {

    private static final ThreadLocal<EvaluationExecutionContext> CURRENT = new ThreadLocal<>();

    private final AtomicReference<SendUserMessage> pendingSendUserMessage = new AtomicReference<>();

    private EvaluationExecutionContext() {
    }

    /**
     * 创建新的评估执行上下文实例（评估执行入口调用，随后 {@link #set} 到当前线程）。
     */
    public static EvaluationExecutionContext create() {
        return new EvaluationExecutionContext();
    }

    /**
     * 获取当前线程的评估执行上下文。
     *
     * @return 评估执行上下文；当前线程不在评估后台执行中时返回 null
     */
    public static EvaluationExecutionContext get() {
        return CURRENT.get();
    }

    /**
     * 判断当前线程是否处于评估后台执行（评估执行标记生效）。
     *
     * @return true 表示当前线程执行于评估后台执行链路（主会话/子会话/消息驱动线程）
     */
    public static boolean isEvaluation() {
        return CURRENT.get() != null;
    }

    /**
     * 将评估执行上下文绑定到当前线程。
     *
     * @param context 评估执行上下文，非 null
     */
    public static void set(EvaluationExecutionContext context) {
        CURRENT.set(context);
    }

    /**
     * 解除当前线程的评估执行上下文绑定，防止线程复用导致上下文串号。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 将待处理的 {@link SendUserMessage} 写入槽位（覆盖当前槽值）。
     *
     * <p>评估后台执行无 WebSocket 前端，{@code DefaultMessageSender} 在评估执行标记生效时
     * 拦截 SEND_USER_MESSAGE 事件并调用本方法写入槽位，由消息驱动循环消费。</p>
     *
     * @param message 待处理的发送用户消息事件
     */
    public void setPendingSendUserMessage(SendUserMessage message) {
        pendingSendUserMessage.set(message);
    }

    /**
     * 取出并清空槽位中的待处理 {@link SendUserMessage}（取出即清）。
     *
     * @return 槽位中的消息；槽位为空时返回 null
     */
    public SendUserMessage getAndClearPendingSendUserMessage() {
        return pendingSendUserMessage.getAndSet(null);
    }
}
