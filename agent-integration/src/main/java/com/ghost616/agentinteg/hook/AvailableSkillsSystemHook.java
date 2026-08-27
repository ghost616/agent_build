package com.ghost616.agentinteg.hook;

import java.util.ArrayList;
import java.util.List;

import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemHook;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookData;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookResult;

/**
 * 可用技能（SKILL）列表提示词 HOOK。
 *
 * <p>实现 {@link SystemHook}{@code <SystemPromptHookData, SystemPromptHookResult>}，
 * getPhase 返回 AFTER_PRE_SYSTEM_PROMPT_BUILD，由 HookManager 在
 * {@code triggerHooks(phase)} 中按 phase 分发。当系统工具定义列表包含加载技能工具
 * （{@link LoadSkillsSystemTool#FULL_TOOL_NAME}）时，生成"可用技能列表"提示词
 * （逐字复刻原 ChatService.buildContextSystemInfo 逻辑），提示模型技能本身不是工具、
 * 需先经 load_skills 系统工具加载后再调用其关联工具。</p>
 *
 * <p>工具定义列表不含加载技能工具、技能列表为空、或主会话过滤 CHILD 技能后为空时
 * 一律返回 null（静默跳过），不影响原有流程。无 Spring 依赖，可直接 new 使用。</p>
 *
 * @author ghost616
 */
public class AvailableSkillsSystemHook implements SystemHook<SystemPromptHookData, SystemPromptHookResult> {

    /** 可用技能列表提示词固定开头 */
    private static final String SKILLS_LIST_HEADER =
            "以下是可用的技能（SKILL）列表（技能本身不是工具，需先加载再使用其关联的工具）：\n";

    @Override
    public HookPhase getPhase() {
        return HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD;
    }

    @Override
    public SystemPromptHookResult execute(AgentExecutionContext ctx, SystemPromptHookData data) {
        if (ctx == null || data == null || !hasLoadSkillsTool(data.getToolDefinitions())) {
            return null;
        }
        List<SkillConfigDTO> availableSkills = collectAvailableSkills(ctx);
        if (availableSkills.isEmpty()) {
            return null;
        }
        return new SystemPromptHookResult(buildSkillsPrompt(availableSkills));
    }

    /**
     * 判断系统工具定义列表中是否包含加载技能系统工具。
     *
     * @param toolDefinitions 当前系统工具定义列表（可为 null）
     * @return 包含 {@link LoadSkillsSystemTool#FULL_TOOL_NAME} 同名工具定义时返回 true
     */
    private boolean hasLoadSkillsTool(List<ToolDefinition> toolDefinitions) {
        if (toolDefinitions == null) {
            return false;
        }
        return toolDefinitions.stream()
                .anyMatch(def -> def != null && LoadSkillsSystemTool.FULL_TOOL_NAME.equals(def.getName()));
    }

    /**
     * 收集可用技能列表：遍历 ctx.getSkills()（null 安全），主会话跳过 sessionAuth==CHILD 的技能。
     *
     * @param ctx 智能体执行上下文
     * @return 可用技能列表（可能为空，不为 null）
     */
    private List<SkillConfigDTO> collectAvailableSkills(AgentExecutionContext ctx) {
        List<SkillConfigDTO> availableSkills = new ArrayList<>();
        List<SkillConfigDTO> skills = ctx.getSkills();
        if (skills != null) {
            for (SkillConfigDTO skill : skills) {
                if (skill == null) {
                    continue;
                }
                if (ctx.isMainSession() && skill.getSessionAuth() == SessionAuthType.CHILD) {
                    continue;
                }
                availableSkills.add(skill);
            }
        }
        return availableSkills;
    }

    /**
     * 构建可用技能列表提示词文本（逐字复刻原 ChatService.buildContextSystemInfo 逻辑）。
     *
     * @param availableSkills 可用技能列表（非空）
     * @return 提示词文本
     */
    private String buildSkillsPrompt(List<SkillConfigDTO> availableSkills) {
        StringBuilder sb = new StringBuilder();
        sb.append(SKILLS_LIST_HEADER);
        for (SkillConfigDTO skill : availableSkills) {
            sb.append("- ").append(skill.getName());
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                sb.append(": ").append(skill.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\n请使用 ").append(LoadSkillsSystemTool.FULL_TOOL_NAME)
                .append(" 系统工具加载所需技能。加载后，该技能的关联工具将变为可用，届时再调用具体工具。禁止直接以技能名称作为工具调用。");
        return sb.toString();
    }
}