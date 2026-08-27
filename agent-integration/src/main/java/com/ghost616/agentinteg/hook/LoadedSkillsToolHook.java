package com.ghost616.agentinteg.hook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.invoker.SystemHook;
import com.ghost616.agentbase.service.agent.invoker.ToolDefinitionsHookData;
import com.ghost616.agentbase.service.agent.invoker.ToolDefinitionsHookResult;

/**
 * 已加载技能工具注入 HOOK。
 *
 * <p>实现 {@link SystemHook}{@code <ToolDefinitionsHookData, ToolDefinitionsHookResult>}，
 * getPhase 返回 BEFORE_TOOL_DEFINITIONS_BUILD，由 HookManager 在
 * {@code triggerHooks(phase)} 中按 phase 分发。从会话变量
 * （{@code LoadSkillsSystemTool.SESSION_KEY}）读取已加载技能名并经
 * {@link LoadedSkillsHelper} 过滤出已加载技能配置（主会话跳过 sessionAuth==CHILD），
 * 收集这些技能的 skillTools 作为追加工具；返回的工具列表 = 基础工具列表
 * （data.getTools()）+ 追加的已加载技能工具（按名称/ID 去重，保持顺序）。</p>
 *
 * <p>无可追加工具时透传 data.getTools() 本身（ChatService 在存在该阶段 HOOK 时
 * 以返回结果接管工具列表，透传保证基础工具不丢失）；会话变量缺失/解析失败
 * 降级为空追加列表，不影响正常流程。无 Spring 依赖，可直接 new 使用。</p>
 *
 * @author ghost616
 */
public class LoadedSkillsToolHook implements SystemHook<ToolDefinitionsHookData, ToolDefinitionsHookResult> {

    @Override
    public HookPhase getPhase() {
        return HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD;
    }

    @Override
    public ToolDefinitionsHookResult execute(AgentExecutionContext ctx, ToolDefinitionsHookData data) {
        if (data == null) {
            return new ToolDefinitionsHookResult(null);
        }
        List<ToolConfigDTO> baseTools = data.getTools();
        List<ToolConfigDTO> loadedTools = ctx == null ? List.of() : collectLoadedSkillTools(ctx);
        if (loadedTools.isEmpty()) {
            return new ToolDefinitionsHookResult(baseTools);
        }
        return new ToolDefinitionsHookResult(mergeTools(baseTools, loadedTools));
    }

    /**
     * 收集已加载技能的关联工具（skillTools，null 安全）。
     *
     * @param ctx 智能体执行上下文
     * @return 已加载技能的 skillTools 列表（可能为空，不为 null）
     */
    private List<ToolConfigDTO> collectLoadedSkillTools(AgentExecutionContext ctx) {
        List<ToolConfigDTO> tools = new ArrayList<>();
        for (SkillConfigDTO skill : LoadedSkillsHelper.collectLoadedSkills(ctx)) {
            if (skill.getSkillTools() != null) {
                for (ToolConfigDTO tool : skill.getSkillTools()) {
                    if (tool != null) {
                        tools.add(tool);
                    }
                }
            }
        }
        return tools;
    }

    /**
     * 合并基础工具与追加工具：按名称/ID 去重（name 非空优先用 name，否则用 id），保持顺序。
     *
     * @param base     基础工具列表（可为 null）
     * @param appended 追加工具列表（可为 null）
     * @return 合并后的工具列表
     */
    private List<ToolConfigDTO> mergeTools(List<ToolConfigDTO> base, List<ToolConfigDTO> appended) {
        List<ToolConfigDTO> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (base != null) {
            for (ToolConfigDTO tool : base) {
                if (tool != null && seen.add(toolKey(tool))) {
                    merged.add(tool);
                }
            }
        }
        if (appended != null) {
            for (ToolConfigDTO tool : appended) {
                if (tool != null && seen.add(toolKey(tool))) {
                    merged.add(tool);
                }
            }
        }
        return merged;
    }

    /**
     * 计算工具去重键：name 非空白优先使用 name，否则使用 id（可为 null）。
     *
     * @param tool 工具配置
     * @return 去重键
     */
    private String toolKey(ToolConfigDTO tool) {
        if (tool.getName() != null && !tool.getName().isBlank()) {
            return tool.getName();
        }
        return tool.getId();
    }
}