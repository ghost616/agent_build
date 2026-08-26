package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;

/**
 * HOOK 执行契约接口。
 *
 * <p>泛型参数 {@code D} 为该 HOOK 消费的数据载体类型（{@link HookData} 实现），
 * {@code R} 为数据载体对应的结果类型（{@link HookResult}）。</p>
 *
 * @param <D> HOOK 数据载体类型
 * @param <R> 结果类型
 * @author ghost616
 */
public interface HookInvoker<D extends HookData<R>, R extends HookResult> {

    /**
     * 获取该 HOOK 生效的阶段。
     *
     * @return HOOK 生效阶段
     */
    HookPhase getPhase();

    /**
     * 执行 HOOK。
     *
     * @param ctx  智能体执行上下文
     * @param data HOOK 数据载体
     * @return HOOK 执行结果
     */
    R execute(AgentExecutionContext ctx, D data);
}
