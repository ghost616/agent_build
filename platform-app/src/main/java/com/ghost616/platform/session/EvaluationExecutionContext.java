package com.ghost616.platform.session;

import com.ghost616.agentbase.sendmessage.SendUserMessage;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 评估执行上下文：承载单次「评估后台执行」运行期的执行标记与待处理 {@link SendUserMessage} 有序列表。
 *
 * <p>由 {@link ContextThreadVariableHandler} 统一传播：评估执行入口
 * （{@code AsyncEvaluationExecutor.executeAsync}）在当前线程创建并写入实例，
 * 各跨线程提交点（工具异步线程、子会话后台驱动线程）提交前通过
 * {@code ThreadVariableHandler.wrap()} 捕获当前线程的实例快照，异步线程开始执行时
 * {@code apply()} 恢复、finally 中清理，保证评估执行标记与待处理消息跨线程可见且不串号/泄漏。</p>
 *
 * <p>待处理列表语义：并发安全的有序多值队列（FIFO）——同一驱动窗口内并发/连续产生的多条
 * {@link SendUserMessage} 均被保留并按产生顺序消费（不再单值覆盖丢消息），追加（{@link #addPendingSendUserMessage}）
 * 与取出（{@link #pollNextPendingSendUserMessage}，取出即移除）分离，由
 * {@link com.ghost616.platform.service.evaluation.AsyncEvaluationExecutor} 的消息驱动循环逐条消费至空。</p>
 */
public final class EvaluationExecutionContext {

    private static final ThreadLocal<EvaluationExecutionContext> CURRENT = new ThreadLocal<>();

    private final ConcurrentLinkedQueue<SendUserMessage> pendingSendUserMessages = new ConcurrentLinkedQueue<>();

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
     * 将待处理的 {@link SendUserMessage} 追加到待处理列表末尾（追加语义，不再覆盖）。
     *
     * <p>评估后台执行无 WebSocket 前端，{@code DefaultMessageSender} 在评估执行标记生效时
     * 拦截 SEND_USER_MESSAGE 事件并调用本方法追加写入，由消息驱动循环按产生顺序逐条消费，
     * 同一驱动窗口内并发/连续产生的多条消息均不丢失。</p>
     *
     * @param message 待处理的发送用户消息事件
     */
    public void addPendingSendUserMessage(SendUserMessage message) {
        pendingSendUserMessages.add(message);
    }

    /**
     * 取出待处理列表队首的 {@link SendUserMessage} 并移除（取出即清）。
     *
     * @return 队首消息；列表为空时返回 null
     */
    public SendUserMessage pollNextPendingSendUserMessage() {
        return pendingSendUserMessages.poll();
    }
}
