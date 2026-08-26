package com.ghost616.agentbase.service.agent.invoker;

/**
 * 空 HOOK 结果实现（单例模式，构造私有）。
 *
 * <p>用于无结果返回的 HOOK 场景，通过 {@link HookResult#empty()} 或
 * {@link #INSTANCE} 获取实例。</p>
 *
 * @author ghost616
 */
public final class EmptyHookResult implements HookResult {

    /** 全局唯一实例 */
    public static final EmptyHookResult INSTANCE = new EmptyHookResult();

    private EmptyHookResult() {
    }
}
