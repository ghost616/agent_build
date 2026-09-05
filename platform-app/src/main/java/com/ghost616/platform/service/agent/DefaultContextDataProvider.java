package com.ghost616.platform.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.AgentSkill;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionSkill;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.entity.SessionVariable;
import com.ghost616.platform.entity.SkillConfig;
import com.ghost616.platform.entity.SkillTool;
import com.ghost616.platform.entity.ToolConfig;
import com.ghost616.agentbase.enums.CommonStatus;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.AgentSkillMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.repository.SessionVariableMapper;
import com.ghost616.platform.repository.SkillConfigMapper;
import com.ghost616.platform.repository.SkillToolMapper;
import com.ghost616.platform.repository.ToolConfigMapper;
import com.ghost616.platform.service.tool.ToolConfigService;
import com.ghost616.platform.session.UserContextUtil;
import com.ghost616.platform.util.IdConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.ContextDataProvider;
import com.ghost616.agentbase.service.agent.MessageDataProvider;


@Component
@RequiredArgsConstructor
public class DefaultContextDataProvider implements ContextDataProvider {

    private final SessionMapper sessionMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final SessionVariableMapper sessionVariableMapper;
    private final AgentSkillMapper agentSkillMapper;
    private final SkillConfigMapper skillConfigMapper;
    private final SkillToolMapper skillToolMapper;
    private final ToolConfigService toolConfigService;
    private final SessionSkillMapper sessionSkillMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ToolConfigMapper toolConfigMapper;
    private final SessionToolMapper sessionToolMapper;
    private final MessageDataProvider messageDataProvider;

    @Override
    public AgentContextData loadAgentContext(String sessionId) {
        Long sid = IdConverter.parse(sessionId);
        Session session = sessionMapper.selectById(sid);
        if (session == null) {
            return null;
        }

        if (Boolean.TRUE.equals(session.getIsChild())) {
            Long parentSessionId = session.getParentSessionId();
            Integer recentMessageCount = null;
            if (parentSessionId != null) {
                Session parentSession = sessionMapper.selectById(parentSessionId);
                if (parentSession != null && parentSession.getAgentId() != null) {
                    AgentConfig agentConfig = agentConfigMapper.selectById(parentSession.getAgentId());
                    recentMessageCount = agentConfig != null ? agentConfig.getRecentMessageCount() : null;
                }
            }

            List<SessionSkill> sessionSkills = sessionSkillMapper.selectList(
                    new LambdaQueryWrapper<SessionSkill>()
                            .eq(SessionSkill::getSessionId, sid));
            Map<Long, SessionAuthType> skillAuthMap = new HashMap<>();
            List<Long> skillIds = sessionSkills.stream()
                    .peek(ss -> skillAuthMap.put(ss.getSkillId(),
                            ss.getSessionAuth() != null ? ss.getSessionAuth() : SessionAuthType.ALL))
                    .map(SessionSkill::getSkillId)
                    .distinct()
                    .toList();

            List<SkillConfigDTO> skills = skillIds.isEmpty() ? List.of() : loadSkillConfigDTOs(skillIds, skillAuthMap);
            Map<String, String> sessionVariables = loadSessionVariablesInternal(sid);

            return new AgentContextData(null, session.getSystemPrompt(), IdConverter.toString(session.getModelId()),
                    recentMessageCount, skills, sessionVariables,
                    IdConverter.toString(session.getParentSessionId()), null, session.getLastResponseId(),
                    null);
        }

        Long agentId = session.getAgentId();
        AgentConfig agentConfig = agentConfigMapper.selectById(agentId);
        String systemPrompt = agentConfig != null ? agentConfig.getSystemPrompt() : null;
        Long defaultModelId = agentConfig != null ? agentConfig.getModelId() : null;
        Integer recentMessageCount = agentConfig != null ? agentConfig.getRecentMessageCount() : null;

        List<SkillConfigDTO> skills = loadSkillsInternal(agentId);
        Map<String, String> sessionVariables = loadSessionVariablesInternal(sid);

        List<AgentExecutionContext.ChildSession> childSessions = sessionMapper.selectList(
                        new LambdaQueryWrapper<Session>()
                                .eq(Session::getParentSessionId, sid))
                .stream()
                .map(s -> new AgentExecutionContext.ChildSession(IdConverter.toString(s.getId()), s.getTitle(), s.getDescription(), IdConverter.toString(s.getModelId())))
                .toList();

        return new AgentContextData(IdConverter.toString(agentId), systemPrompt, IdConverter.toString(defaultModelId), recentMessageCount, skills, sessionVariables,
                null, childSessions, session.getLastResponseId(), null);
    }

    @Override
    public void updateLastResponseId(String sessionId, String lastResponseId) {
        Long sid = IdConverter.parse(sessionId);
        Session session = sessionMapper.selectById(sid);
        if (session == null) {
            return;
        }
        session.setLastResponseId(lastResponseId);
        sessionMapper.updateById(session);
    }

    private List<SkillConfigDTO> loadSkillsInternal(Long agentId) {
        if (agentId == null) {
            return List.of();
        }

        List<AgentSkill> agentSkills = agentSkillMapper.selectList(
                new LambdaQueryWrapper<AgentSkill>()
                        .eq(AgentSkill::getAgentId, agentId));
        Map<Long, SessionAuthType> skillAuthMap = new HashMap<>();
        List<Long> skillIds = agentSkills.stream()
                .peek(as -> skillAuthMap.put(as.getSkillId(),
                        as.getSessionAuth() != null ? as.getSessionAuth() : SessionAuthType.ALL))
                .map(AgentSkill::getSkillId)
                .distinct()
                .toList();

        if (skillIds.isEmpty()) {
            return List.of();
        }

        return loadSkillConfigDTOs(skillIds, skillAuthMap);
    }

    private List<SkillConfigDTO> loadSkillConfigDTOs(List<Long> skillIds, Map<Long, SessionAuthType> skillAuthMap) {
        List<SkillConfig> skillConfigs = skillConfigMapper.selectBatchIds(skillIds);
        if (skillConfigs == null || skillConfigs.isEmpty()) {
            return List.of();
        }

        List<SkillConfigDTO> result = new ArrayList<>();
        for (SkillConfig sc : skillConfigs) {
            if (sc.getStatus() == null || sc.getStatus() != CommonStatus.ENABLED) {
                continue;
            }

            List<SkillTool> skillTools = skillToolMapper.selectList(
                    new LambdaQueryWrapper<SkillTool>()
                            .eq(SkillTool::getSkillId, sc.getId()));
            List<Long> toolIds = skillTools.stream()
                    .map(SkillTool::getToolId)
                    .toList();

            List<ToolConfigDTO> toolDTOs = new ArrayList<>();
            for (Long toolId : toolIds) {
                ToolConfigDTO dto = toolConfigService.getById(toolId);
                toolDTOs.add(dto);
            }

            result.add(SkillConfigDTO.builder()
                    .id(IdConverter.toString(sc.getId()))
                    .name(sc.getName())
                    .description(sc.getDescription())
                    .prompt(sc.getPrompt())
                    .sessionAuth(skillAuthMap.getOrDefault(sc.getId(), SessionAuthType.ALL))
                    .skillTools(toolDTOs)
                    .build());
        }

        return result;
    }

    private Map<String, String> loadSessionVariablesInternal(Long sessionId) {
        List<SessionVariable> variables = sessionVariableMapper.selectList(
                new LambdaQueryWrapper<SessionVariable>()
                        .eq(SessionVariable::getSessionId, sessionId));
        Map<String, String> result = new HashMap<>();
        for (SessionVariable sv : variables) {
            result.put(sv.getVariableKey(), sv.getVariableValue());
        }
        return result;
    }

    @Override
    public void saveSessionVariable(String sessionId, String key, String value) {
        Long sid = IdConverter.parse(sessionId);
        List<SessionVariable> existing = sessionVariableMapper.selectList(
                new LambdaQueryWrapper<SessionVariable>()
                        .eq(SessionVariable::getSessionId, sid)
                        .eq(SessionVariable::getVariableKey, key));
        if (existing != null && !existing.isEmpty()) {
            SessionVariable sv = existing.get(0);
            sv.setVariableValue(value);
            sessionVariableMapper.updateById(sv);
        } else {
            SessionVariable sv = new SessionVariable();
            sv.setUserId(UserContextUtil.currentUserIdOrNull());
            sv.setSessionId(sid);
            sv.setVariableKey(key);
            sv.setVariableValue(value);
            sessionVariableMapper.insert(sv);
        }
    }

    @Override
    public void deleteSessionVariable(String sessionId, String key) {
        Long sid = IdConverter.parse(sessionId);
        sessionVariableMapper.delete(
                new LambdaQueryWrapper<SessionVariable>()
                        .eq(SessionVariable::getSessionId, sid)
                        .eq(SessionVariable::getVariableKey, key));
    }

    @Override
    public String createChildSession(String parentSessionId, String sessionName, String description, String modelId,
                                    List<String> toolIds, List<String> skillIds, String prompt) {
        Long pid = IdConverter.parse(parentSessionId);
        Long mid = IdConverter.parse(modelId);
        List<Long> tids = IdConverter.parseList(toolIds);
        List<Long> sids = IdConverter.parseList(skillIds);
        Session parentSession = sessionMapper.selectById(pid);
        if (parentSession == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (mid != null) {
            ModelConfig modelConfig = modelConfigMapper.selectById(mid);
            if (modelConfig == null) {
                throw new BusinessException(ErrorCode.MODEL_NOT_FOUND);
            }
        }

        if (tids != null) {
            for (Long toolId : tids) {
                ToolConfig toolConfig = toolConfigMapper.selectById(toolId);
                if (toolConfig == null) {
                    throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
                }
            }
        }

        if (sids != null) {
            for (Long skillId : sids) {
                SkillConfig skillConfig = skillConfigMapper.selectById(skillId);
                if (skillConfig == null) {
                    throw new BusinessException(ErrorCode.SKILL_NOT_FOUND);
                }
            }
        }

        Session session = new Session();
        session.setUserId(UserContextUtil.currentUserIdOrNull());
        session.setTitle(sessionName);
        session.setSystemPrompt(prompt);
        session.setDescription(description);
        session.setParentSessionId(pid);
        session.setIsChild(true);
        // 评估执行标记继承：评估执行会话链（isEvaluation=true）下创建的子会话及嵌套子会话
        // 同样为 true；普通会话下继承 null/false，不影响其它逻辑
        session.setIsEvaluation(parentSession.getIsEvaluation());
        session.setModelId(mid);
        sessionMapper.insert(session);

        if (tids != null) {
            for (Long toolId : tids) {
                SessionTool st = new SessionTool();
                st.setSessionId(session.getId());
                st.setToolId(toolId);
                st.setSessionAuth(SessionAuthType.ALL);
                sessionToolMapper.insert(st);
            }
        }

        if (sids != null) {
            for (Long skillId : sids) {
                SessionSkill ss = new SessionSkill();
                ss.setSessionId(session.getId());
                ss.setSkillId(skillId);
                ss.setSessionAuth(SessionAuthType.ALL);
                sessionSkillMapper.insert(ss);
            }
        }

        return IdConverter.toString(session.getId());
    }

    @Override
    public List<MessageDataProvider.MessageDTO> getLatestMessages(String sessionId) {
        return messageDataProvider.getMessages(sessionId);
    }

    @Override
    public Map<String, String> getLatestSessionVariables(String sessionId) {
        Long sid = IdConverter.parse(sessionId);
        return loadSessionVariablesInternal(sid);
    }

    @Override
    public Map<String, String> getLatestConversationVariables(String sessionId) {
        return Map.of();
    }

    @Override
    public List<AgentExecutionContext.ChildSession> getLatestChildSessions(String sessionId) {
        Long sid = IdConverter.parse(sessionId);
        List<Session> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<Session>()
                        .eq(Session::getParentSessionId, sid));
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .map(s -> new AgentExecutionContext.ChildSession(IdConverter.toString(s.getId()), s.getTitle(), s.getDescription(), IdConverter.toString(s.getModelId())))
                .toList();
    }
}