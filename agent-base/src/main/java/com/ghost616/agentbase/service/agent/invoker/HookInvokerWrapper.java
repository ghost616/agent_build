package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;

/**
 * {@link HookInvoker} 包装类。
 *
 * <p>构造时通过 {@link HookDataMatcher#resolveDataType} 预解析并缓存数据载体类型
 * （dataType），用于 {@link #supports} 匹配判断，避免每次执行时反射解析。
 * 执行时内部以 unchecked 强转委托给原始 {@link HookInvoker}。</p>
 *
 * @author ghost616
 */
public class HookInvokerWrapper {

    private final HookInvoker<?, ?> delegate;
    private final Class<?> dataType;

    /**
     * 构造包装类，预解析数据载体类型。
     *
     * @param hook 原始 HOOK 调用器
     */
    public HookInvokerWrapper(HookInvoker<?, ?> hook) {
        this.delegate = hook;
        this.dataType = HookDataMatcher.resolveDataType(hook);
    }

    /**
     * 获取该 HOOK 生效的阶段。
     *
     * @return HOOK 生效阶段
     */
    public HookPhase getPhase() {
        return delegate.getPhase();
    }

    /**
     * 获取执行顺序索引（委托 {@link SystemHook#getIndex}）。
     *
     * @return 执行顺序索引；非系统 HOOK 返回 0
     */
    public int getIndex() {
        if (delegate instanceof SystemHook) {
            return ((SystemHook<?, ?>) delegate).getIndex();
        }
        return 0;
    }

    /**
     * 判断该 HOOK 是否支持给定的数据载体。
     *
     * <p>数据载体类型无法解析（dataType 为 null）时放行返回 true。</p>
     *
     * @param data 数据载体
     * @return 匹配返回 true
     */
    public boolean supports(HookData<?> data) {
        if (dataType == null) {
            return true;
        }
        return data != null && dataType.isInstance(data);
    }

    /**
     * 执行 HOOK（内部 unchecked 强转委托执行）。
     *
     * @param ctx  智能体执行上下文
     * @param data HOOK 数据载体
     * @return HOOK 执行结果
     */
    @SuppressWarnings("unchecked")
    public HookResult execute(AgentExecutionContext ctx, HookData<?> data) {
        return ((HookInvoker) delegate).execute(ctx, data);
    }

    /**
     * 获取原始 HOOK 调用器。
     *
     * @return 原始调用器
     */
    public HookInvoker<?, ?> getDelegate() {
        return delegate;
    }
}
