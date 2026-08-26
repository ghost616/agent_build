package com.ghost616.agentbase.service.agent.invoker;

/**
 * HOOK 执行结果接口。
 *
 * <p>作为 {@link HookInvoker#execute} 的返回值契约，供 HOOK 向调用方回传
 * 执行产物。实现类需提供具体的结果载体；无结果场景可使用 {@link #empty()}。</p>
 *
 * @author ghost616
 */
public interface HookResult {

    /**
     * 返回一个空结果实例（{@link EmptyHookResult} 单例）。
     *
     * @return 空结果
     */
    static HookResult empty() {
        return EmptyHookResult.INSTANCE;
    }
}
