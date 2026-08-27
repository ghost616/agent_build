package com.ghost616.agentbase.service.agent.invoker;

import java.util.List;

import com.ghost616.agentbase.dto.tool.ToolConfigDTO;

/**
 * 工具定义构建数据载体，实现 {@link HookData} 结果类型
 * {@link ToolDefinitionsHookResult}。
 *
 * <p>承载会话上下文工具配置列表（{@code context.getTools()} 的引用，
 * 按现有 HookData 风格直接持有），供
 * {@link HookPhase#BEFORE_TOOL_DEFINITIONS_BUILD} 阶段的 HOOK 消费以
 * 调整/替换参与模型工具定义构建的工具列表。</p>
 *
 * @author ghost616
 */
public class ToolDefinitionsHookData implements HookData<ToolDefinitionsHookResult> {

    private final List<ToolConfigDTO> tools;

    public ToolDefinitionsHookData(List<ToolConfigDTO> tools) {
        this.tools = tools;
    }

    /**
     * 获取承载的工具配置列表（可为 null）。
     *
     * @return 工具配置列表，可为 null
     */
    public List<ToolConfigDTO> getTools() {
        return tools;
    }
}