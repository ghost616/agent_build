package com.ghost616.agentbase.service.agent.invoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ghost616.agentbase.dto.model.ToolDefinition;

/**
 * 前置系统提示词构建数据载体，实现 {@link HookData} 结果类型
 * {@link SystemPromptHookResult}。
 *
 * <p>承载当前系统工具定义列表（{@link ToolDefinition}），供
 * {@link HookPhase#AFTER_PRE_SYSTEM_PROMPT_BUILD} 阶段的 HOOK 消费以生成
 * 可用技能/工具相关的前置提示词。构造时对传入列表做防御性深拷贝：
 * 列表复制 + 单个 ToolDefinition 用 builder 重建（name/description 拷贝，
 * parameters Map 新建 HashMap 拷贝），隔离调用方后续修改；
 * toolDefinitions 为 null 时视为空列表。</p>
 *
 * @author ghost616
 */
public class SystemPromptHookData implements HookData<SystemPromptHookResult> {

    private final List<ToolDefinition> toolDefinitions;

    public SystemPromptHookData(List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null) {
            this.toolDefinitions = List.of();
            return;
        }
        List<ToolDefinition> copied = new ArrayList<>(toolDefinitions.size());
        for (ToolDefinition def : toolDefinitions) {
            if (def == null) {
                copied.add(null);
                continue;
            }
            Map<String, Object> parametersCopy = def.getParameters() != null
                    ? new HashMap<>(def.getParameters()) : null;
            copied.add(ToolDefinition.builder()
                    .name(def.getName())
                    .description(def.getDescription())
                    .parameters(parametersCopy)
                    .build());
        }
        this.toolDefinitions = List.copyOf(copied);
    }

    /**
     * 获取工具定义列表（防御性深拷贝后的不可变副本）。
     *
     * @return 工具定义列表，永不返回 null（null 输入视为空列表）
     */
    public List<ToolDefinition> getToolDefinitions() {
        return toolDefinitions;
    }
}