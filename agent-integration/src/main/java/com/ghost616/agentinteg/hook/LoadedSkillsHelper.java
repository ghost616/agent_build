package com.ghost616.agentinteg.hook;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.util.JsonMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 已加载技能解析辅助类（包级私有）。
 *
 * <p>从会话变量（{@link LoadSkillsSystemTool#SESSION_KEY}）读取已加载技能名列表，
 * 并按名称从 {@code ctx.getSkills()} 过滤出已加载技能配置（主会话跳过
 * sessionAuth==CHILD）。供 {@link AvailableSkillsSystemHook} 与
 * {@link LoadedSkillsToolHook} 共用，会话变量缺失/空白/JSON 解析失败一律降级
 * 返回空列表，不影响正常流程。</p>
 *
 * @author ghost616
 */
final class LoadedSkillsHelper {

    private LoadedSkillsHelper() {
    }

    /**
     * 解析会话变量中的已加载技能名列表。
     *
     * @param ctx 智能体执行上下文（可为 null）
     * @return 已加载技能名列表；会话变量缺失/空白/JSON 解析失败时返回空列表
     */
    static List<String> parseLoadedSkillNames(AgentExecutionContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        String json = ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> names = JsonMapper.MAPPER.readValue(json, new TypeReference<List<String>>() {});
            if (names == null) {
                return List.of();
            }
            return names;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 从技能配置中过滤出已加载技能（主会话跳过 sessionAuth==CHILD）。
     *
     * @param ctx 智能体执行上下文（可为 null）
     * @return 已加载技能配置列表；无已加载技能或技能配置为 null 时返回空列表
     */
    static List<SkillConfigDTO> collectLoadedSkills(AgentExecutionContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        List<String> loadedNames = parseLoadedSkillNames(ctx);
        if (loadedNames.isEmpty()) {
            return List.of();
        }
        Set<String> nameSet = new HashSet<>(loadedNames);
        List<SkillConfigDTO> result = new ArrayList<>();
        List<SkillConfigDTO> skills = ctx.getSkills();
        if (skills != null) {
            for (SkillConfigDTO skill : skills) {
                if (skill == null || !nameSet.contains(skill.getName())) {
                    continue;
                }
                if (ctx.isMainSession() && skill.getSessionAuth() == SessionAuthType.CHILD) {
                    continue;
                }
                result.add(skill);
            }
        }
        return result;
    }
}