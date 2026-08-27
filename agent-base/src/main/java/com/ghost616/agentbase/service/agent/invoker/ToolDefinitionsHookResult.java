package com.ghost616.agentbase.service.agent.invoker;

import java.util.List;

import com.ghost616.agentbase.dto.tool.ToolConfigDTO;

/**
 * 工具定义构建 HOOK 执行结果。
 *
 * <p>由 {@link HookPhase#BEFORE_TOOL_DEFINITIONS_BUILD} 阶段的 HOOK 返回，
 * 携带工具配置列表（{@link ToolConfigDTO}），供 ChatService 在构建模型工具
 * 定义时合并使用。getTools() 可能返回 null，调用方需做 null 安全处理。</p>
 *
 * @author ghost616
 */
public class ToolDefinitionsHookResult implements HookResult {

    private final List<ToolConfigDTO> tools;

    public ToolDefinitionsHookResult(List<ToolConfigDTO> tools) {
        this.tools = tools;
    }

    /**
     * 获取 HOOK 提供的工具配置列表（可能为 null，调用方需判空）。
     *
     * @return 工具配置列表，可为 null
     */
    public List<ToolConfigDTO> getTools() {
        return tools;
    }
}