package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;

/**
 * 系统 HOOK 接口，扩展 {@link HookInvoker}，新增执行顺序控制。
 *
 * <p>泛型参数透传自 {@link HookInvoker}：{@code D} 为数据载体类型，
 * {@code R} 为结果类型。</p>
 *
 * @param <D> HOOK 数据载体类型
 * @param <R> 结果类型
 * @author ghost616
 */
public interface SystemHook<D extends HookData<R>, R extends HookResult> extends HookInvoker<D, R> {

    /**
     * 获取该 HOOK 的执行顺序索引，数值越小越早执行。
     *
     * @return 执行顺序索引，默认 0
     */
    default int getIndex() {
        return 0;
    }
}
