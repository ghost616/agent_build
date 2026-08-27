package com.ghost616.platform.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.CommonStatus;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.ChatDataProvider;
import com.ghost616.agentbase.service.agent.ToolDataProvider;
import com.ghost616.agentbase.service.agent.ToolDataProvider.SessionToolInfo;
import com.ghost616.agentbase.service.agent.invoker.HookInvoker;
import com.ghost616.platform.entity.AgentSkill;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SkillConfig;
import com.ghost616.platform.event.AgentChangedEvent;
import com.ghost616.platform.repository.AgentSkillMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SkillConfigMapper;
import com.ghost616.platform.util.IdConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class DefaultChatDataProvider implements ChatDataProvider {

    /** WEBSOCKET 子会话前置系统提示词：任务完成后必须调用 send_result_to_parent 工具回传结果，禁止不调用工具直接结束；无需轮询获取父会话或其他会话状态 */
    static final String WEB_SOCKET_SUB_SESSION_PRE_SYSTEM_PROMPT =
            "你是子会话执行者。完成任务后，必须调用 send_result_to_parent 工具，将执行结果作为参数发送给父会话，"
                    + "禁止不调用工具直接结束；结果内容为最终答案。无需轮询获取父会话或其他会话状态，"
                    + "只需完成分配的任务并回传结果。";

    private final ModelConfigMapper modelConfigMapper;
    private final SessionMapper sessionMapper;
    private final ApplicationContext applicationContext;
    private final SubSessionWebSocketModeResolver subSessionWebSocketModeResolver;
    private final ToolDataProvider toolDataProvider;
    private final AgentSkillMapper agentSkillMapper;
    private final SkillConfigMapper skillConfigMapper;

    @Override
    public ModelConfigData getModelConfig(String modelId) {
        Long id = IdConverter.parse(modelId);
        if (id == null) {
            return null;
        }
        ModelConfig entity = modelConfigMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        return new ModelConfigData(
                IdConverter.toString(entity.getId()),
                entity.getApiKey(),
                entity.getBaseUrl(),
                entity.getModelName(),
                entity.getTemperature(),
                entity.getMaxTokens(),
                entity.getPlatformType() != null ? entity.getPlatformType().name() : null,
                entity.getRequestType()
        );
    }

    @Override
    public void updateSessionModelId(String sessionId, String modelId) {
        Long sid = IdConverter.parse(sessionId);
        Long mid = IdConverter.parse(modelId);
        Session session = sessionMapper.selectById(sid);
        if (session != null) {
            session.setModelId(mid);
            sessionMapper.updateById(session);
        }
    }

    @Override
    public List<HookInvoker> getHooks() {
        Map<String, HookInvoker> map = applicationContext.getBeansOfType(HookInvoker.class);
        return new ArrayList<>(map.values());
    }

    @Override
    public List<HookInvoker> getHooks(String sessionId) {
        return List.of();
    }

    @Override
    public String getPreSystemPrompt(String sessionId) {
        if (subSessionWebSocketModeResolver.isWebSocketSubSession(sessionId)) {
            return WEB_SOCKET_SUB_SESSION_PRE_SYSTEM_PROMPT;
        }
        Long sid = IdConverter.parse(sessionId);
        if (sid == null) {
            return null;
        }
        Session session = sessionMapper.selectById(sid);
        if (session == null || Boolean.TRUE.equals(session.getIsChild())) {
            return null;
        }
        return buildSubSessionPermissionPrompt(session);
    }

    /**
     * 主会话重建子会话相关的工具/技能权限说明（与原 ChatService.buildContextSystemInfo 生成文本完全一致）：
     * 工具经 {@link ToolDataProvider#getSessionToolIds} 获取 id+sessionAuth 并逐项 {@link ToolDataProvider#getToolById}
     * 补充 name/description，技能经 AgentSkillMapper 查询 agent 关联技能（含 sessionAuth）并由 SkillConfigMapper
     * 补充 name/description；sessionAuth==CHILD 归入【仅子会话可用】，null/ALL 归入【均可用】。
     * 无任何分组项时返回 null。
     */
    private String buildSubSessionPermissionPrompt(Session session) {
        List<ToolConfigDTO> childOnlyTools = new ArrayList<>();
        List<ToolConfigDTO> allAuthTools = new ArrayList<>();
        List<SessionToolInfo> toolInfos = toolDataProvider.getSessionToolIds(IdConverter.toString(session.getId()));
        if (toolInfos != null) {
            for (SessionToolInfo info : toolInfos) {
                SessionAuthType auth = info.sessionAuth();
                if (auth != SessionAuthType.CHILD && auth != null && auth != SessionAuthType.ALL) {
                    continue;
                }
                ToolConfigDTO tool = toolDataProvider.getToolById(info.toolId());
                if (tool == null) {
                    continue;
                }
                if (auth == SessionAuthType.CHILD) {
                    childOnlyTools.add(tool);
                } else {
                    allAuthTools.add(tool);
                }
            }
        }

        List<SkillConfigDTO> childOnlySkills = new ArrayList<>();
        List<SkillConfigDTO> allAuthSkills = new ArrayList<>();
        Long agentId = session.getAgentId();
        if (agentId != null) {
            List<AgentSkill> agentSkills = agentSkillMapper.selectList(
                    new LambdaQueryWrapper<AgentSkill>()
                            .eq(AgentSkill::getAgentId, agentId));
            if (agentSkills != null) {
                for (AgentSkill agentSkill : agentSkills) {
                    SessionAuthType auth = agentSkill.getSessionAuth();
                    if (auth != SessionAuthType.CHILD && auth != null && auth != SessionAuthType.ALL) {
                        continue;
                    }
                    SkillConfig skillConfig = skillConfigMapper.selectById(agentSkill.getSkillId());
                    if (skillConfig == null || skillConfig.getStatus() != CommonStatus.ENABLED) {
                        continue;
                    }
                    SkillConfigDTO dto = new SkillConfigDTO();
                    dto.setId(IdConverter.toString(skillConfig.getId()));
                    dto.setName(skillConfig.getName());
                    dto.setDescription(skillConfig.getDescription());
                    if (auth == SessionAuthType.CHILD) {
                        childOnlySkills.add(dto);
                    } else {
                        allAuthSkills.add(dto);
                    }
                }
            }
        }

        if (childOnlyTools.isEmpty() && allAuthTools.isEmpty()
                && childOnlySkills.isEmpty() && allAuthSkills.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下为子会话相关的工具/技能权限说明。标注【仅子会话可用】的项不可在当前会话直接调用，需通过子会话使用。标注【均可用】的项当前会话可直接调用。\n");
        if (!childOnlyTools.isEmpty()) {
            sb.append("\n【仅子会话可用】工具：\n");
            for (ToolConfigDTO t : childOnlyTools) {
                sb.append("- ").append(t.getName());
                if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                    sb.append(": ").append(t.getDescription());
                }
                sb.append("\n");
            }
        }
        if (!allAuthTools.isEmpty()) {
            sb.append("\n【均可用】工具：\n");
            for (ToolConfigDTO t : allAuthTools) {
                sb.append("- ").append(t.getName());
                if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                    sb.append(": ").append(t.getDescription());
                }
                sb.append("\n");
            }
        }
        if (!childOnlySkills.isEmpty()) {
            sb.append("\n【仅子会话可用】技能：\n");
            for (SkillConfigDTO s : childOnlySkills) {
                sb.append("- ").append(s.getName());
                if (s.getDescription() != null && !s.getDescription().isEmpty()) {
                    sb.append(": ").append(s.getDescription());
                }
                sb.append("\n");
            }
        }
        if (!allAuthSkills.isEmpty()) {
            sb.append("\n【均可用】技能：\n");
            for (SkillConfigDTO s : allAuthSkills) {
                sb.append("- ").append(s.getName());
                if (s.getDescription() != null && !s.getDescription().isEmpty()) {
                    sb.append(": ").append(s.getDescription());
                }
                sb.append("\n");
            }
        }
        sb.append("\n如需使用上述子会话工具/技能，可开启子会话执行任务：创建或复用子会话并通过回调执行用户消息，支持指定工具和技能");
        sb.append("\n子会话执行期间请勿反复调用 callback_sub_session（发消息工具）轮询询问子会话结果，只需等候子会话通过 send_result_to_parent 返回执行结果；如需与子会话多轮交互可等待其返回后再发下一条消息。");
        return sb.toString();
    }

    @Override
    public String getPostSystemPrompt(String sessionId) {
        return null;
    }

    /**
     * 智能体配置变更后清空子会话 WEBSOCKET 模式解析缓存，保证配置更新即时生效。
     */
    @EventListener
    public void onAgentChanged(AgentChangedEvent event) {
        subSessionWebSocketModeResolver.clearCache();
    }
}
