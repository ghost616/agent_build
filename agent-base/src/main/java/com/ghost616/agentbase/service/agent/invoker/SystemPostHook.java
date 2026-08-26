package com.ghost616.agentbase.service.agent.invoker;

/**
 * 系统后置 HOOK 标记接口，继承 {@link SystemHook}，拥有执行顺序控制能力。
 * 标记此接口的 Bean 将被 {@code HookManager.refreshHooks()} 识别并加入 {@code systemPostHooks} 列表，
 * 在每次触发 HOOK 时最后执行。
 *
 * @param <D> HOOK 数据载体类型
 * @param <R> 结果类型
 * @author ghost616
 */
public interface SystemPostHook<D extends HookData<R>, R extends HookResult> extends SystemHook<D, R> {
}
