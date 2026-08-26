package com.ghost616.agentbase.service.agent.invoker;

/**
 * HOOK 数据载体接口。
 *
 * <p>数据载体是 HOOK 执行时携带的业务数据，通过泛型参数 {@code R} 声明
 * 该数据载体对应的结果类型（{@link HookResult}）。具体实现类负责承载各自的
 * 业务字段（如 {@link ChatChunkHookData} 承载 {@code ChatChunk}、
 * {@link ToolHookContext} 承载工具调用上下文）。</p>
 *
 * @param <R> 数据载体对应的结果类型
 * @author ghost616
 */
public interface HookData<R extends HookResult> {
}
