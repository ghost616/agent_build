package com.ghost616.agentbase.service.agent.invoker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;

/**
 * HOOK 管理基础设施。
 *
 * <p>持有系统 HOOK、系统后置 HOOK 与普通阶段 HOOK 三类缓存（均为
 * {@link HookInvokerWrapper} 包装），提供全局阶段触发、会话级触发与后置触发。
 * 触发方法统一返回 {@link List}{@code <HookResult>}：执行前先按数据载体类型匹配
 * （{@link HookInvokerWrapper#supports}），不匹配或返回 null 的结果跳过收集，
 * 执行异常记录 WARN 日志并跳过，不中断整体流程。</p>
 *
 * @author ghost616
 */
public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final AgentComponentRegistry registry;
    private final Map<HookPhase, List<HookInvokerWrapper>> systemHooks = new HashMap<>();
    private final List<HookInvokerWrapper> systemPostHooks = new ArrayList<>();
    private final Map<HookPhase, List<HookInvokerWrapper>> regularPhaseHooks = new HashMap<>();

    public HookManager(AgentComponentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 刷新 HOOK 缓存：从数据提供者获取原始 HookInvoker 列表，包装为
     * {@link HookInvokerWrapper} 后按类型分类放入缓存。
     */
    public void refreshHooks() {
        systemHooks.clear();
        systemPostHooks.clear();
        regularPhaseHooks.clear();
        List<HookInvoker> hooks = registry.getChatDataProvider().getHooks();
        for (HookInvoker hook : hooks) {
            HookPhase phase = hook.getPhase();
            HookInvokerWrapper wrapper = new HookInvokerWrapper(hook);
            if (hook instanceof SystemPostHook) {
                systemPostHooks.add(wrapper);
            } else if (hook instanceof SystemHook) {
                systemHooks.computeIfAbsent(phase, k -> new ArrayList<>()).add(wrapper);
            } else {
                regularPhaseHooks.computeIfAbsent(phase, k -> new ArrayList<>()).add(wrapper);
            }
        }
    }

    /**
     * 触发全局阶段钩子（普通 HOOK 与系统 HOOK，系统 HOOK 按 index 升序）。
     *
     * @param phase 触发阶段
     * @param ctx   智能体执行上下文
     * @param data  数据载体
     * @return 收集到的非空执行结果列表
     */
    public List<HookResult> triggerHooks(HookPhase phase, AgentExecutionContext ctx, HookData<?> data) {
        List<HookResult> results = new ArrayList<>();
        List<HookInvokerWrapper> regularHooks = regularPhaseHooks.get(phase);
        if (regularHooks != null) {
            for (HookInvokerWrapper wrapper : regularHooks) {
                try {
                    if (wrapper.supports(data)) {
                        HookResult result = wrapper.execute(ctx, data);
                        if (result != null) {
                            results.add(result);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Hook execution failed for {}", wrapper.getClass().getName(), e);
                }
            }
        }
        List<HookInvokerWrapper> hooks = systemHooks.get(phase);
        if (hooks != null) {
            List<HookInvokerWrapper> sorted = new ArrayList<>(hooks);
            sorted.sort(Comparator.comparingInt(HookInvokerWrapper::getIndex));
            for (HookInvokerWrapper wrapper : sorted) {
                try {
                    if (wrapper.supports(data)) {
                        HookResult result = wrapper.execute(ctx, data);
                        if (result != null) {
                            results.add(result);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Hook execution failed for {}", wrapper.getClass().getName(), e);
                }
            }
        }
        return results;
    }

    /**
     * 触发会话专属钩子：普通 HOOK 直接执行，系统 HOOK 按 index 升序执行，
     * 系统后置 HOOK 跳过（归 {@link #executePostHooks} 处理）。
     *
     * @param sessionId 会话 ID
     * @param phase     触发阶段
     * @param ctx       智能体执行上下文
     * @param data      数据载体
     * @return 收集到的非空执行结果列表
     */
    public List<HookResult> triggerSessionHooks(String sessionId, HookPhase phase, AgentExecutionContext ctx, HookData<?> data) {
        List<HookResult> results = new ArrayList<>();
        List<HookInvoker> sessionHooks = registry.getChatDataProvider().getHooks(sessionId);
        if (sessionHooks == null || sessionHooks.isEmpty()) {
            return results;
        }
        List<HookInvoker> sessionSystemHooks = new ArrayList<>();
        for (HookInvoker hook : sessionHooks) {
            if (hook.getPhase() != phase) {
                continue;
            }
            if (hook instanceof SystemPostHook) {
                continue;
            }
            if (hook instanceof SystemHook) {
                sessionSystemHooks.add(hook);
                continue;
            }
            try {
                if (HookDataMatcher.matches(hook, data)) {
                    HookResult result = hook.execute(ctx, data);
                    if (result != null) {
                        results.add(result);
                    }
                }
            } catch (Exception e) {
                log.warn("Session hook execution failed for {}", hook.getClass().getName(), e);
            }
        }
        sessionSystemHooks.sort(Comparator.comparingInt(h -> ((SystemHook) h).getIndex()));
        for (HookInvoker hook : sessionSystemHooks) {
            try {
                if (HookDataMatcher.matches(hook, data)) {
                    HookResult result = hook.execute(ctx, data);
                    if (result != null) {
                        results.add(result);
                    }
                }
            } catch (Exception e) {
                log.warn("Session hook execution failed for {}", hook.getClass().getName(), e);
            }
        }
        return results;
    }

    /**
     * 触发系统后置钩子（按 index 升序）。
     *
     * @param ctx  智能体执行上下文
     * @param data 数据载体
     * @return 收集到的非空执行结果列表
     */
    public List<HookResult> executePostHooks(AgentExecutionContext ctx, HookData<?> data) {
        List<HookResult> results = new ArrayList<>();
        List<HookInvokerWrapper> sorted = new ArrayList<>(systemPostHooks);
        sorted.sort(Comparator.comparingInt(HookInvokerWrapper::getIndex));
        for (HookInvokerWrapper wrapper : sorted) {
            try {
                if (wrapper.supports(data)) {
                    HookResult result = wrapper.execute(ctx, data);
                    if (result != null) {
                        results.add(result);
                    }
                }
            } catch (Exception e) {
                log.warn("Hook execution failed for {}", wrapper.getClass().getName(), e);
            }
        }
        return results;
    }

    /**
     * 将 HOOK 执行结果安全转换为指定类型。
     *
     * <p>内部使用 {@code clazz.isInstance} 判断，result/clazz 为 null 或类型不匹配时返回 null。</p>
     *
     * @param result HOOK 执行结果，可为 null
     * @param clazz  目标类型
     * @param <T>    目标类型参数
     * @return 转换后的结果；不匹配返回 null
     */
    public <T extends HookResult> T castHookResult(HookResult result, Class<T> clazz) {
        if (result == null || clazz == null || !clazz.isInstance(result)) {
            return null;
        }
        return clazz.cast(result);
    }
}
