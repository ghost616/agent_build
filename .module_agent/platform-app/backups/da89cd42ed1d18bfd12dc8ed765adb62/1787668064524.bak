package com.ghost616.platform.service.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.platform.dto.session.SessionDTO;
import com.ghost616.platform.entity.AgentTool;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionSkill;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.entity.SessionVariable;
import com.ghost616.platform.repository.AgentToolMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.repository.SessionVariableMapper;
import com.ghost616.platform.service.agent.DefaultMessageDataProvider;
import com.ghost616.platform.service.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.session.UserContextUtil;
import com.ghost616.platform.dto.session.SessionMessageDTO;
import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.agentbase.service.agent.SessionManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.platform.util.IdConverter;


@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionMapper sessionMapper;
    private final AgentToolMapper agentToolMapper;
    private final SessionToolMapper sessionToolMapper;
    private final SessionManager sessionManager;
    private final AgentContextManager agentContextManager;
    private final ToolManager toolManager;
    private final MessageMapper messageMapper;
    private final DefaultMessageDataProvider defaultMessageDataProvider;
    private final MessageService messageService;
    private final SessionVariableMapper sessionVariableMapper;
    private final SessionSkillMapper sessionSkillMapper;

    @Override
    public List<SessionDTO> listSessions(Long agentId) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getUserId, UserContextUtil.requireUserId());
        if (agentId != null) {
            wrapper.eq(Session::getAgentId, agentId);
        }
        wrapper.and(w -> w.isNull(Session::getIsChild).or().eq(Session::getIsChild, false));
        wrapper.eq(Session::getIsEvaluation, false);
        wrapper.orderByDesc(Session::getCreateTime);

        List<Session> entities = sessionMapper.selectList(wrapper);
        return entities.stream().map(this::toDTO).toList();
    }

    @Override
    public List<SessionDTO> listLogSessions() {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getUserId, UserContextUtil.requireUserId());
        wrapper.and(w -> w.isNull(Session::getIsChild).or().eq(Session::getIsChild, false));
        wrapper.orderByDesc(Session::getCreateTime);

        List<Session> entities = sessionMapper.selectList(wrapper);
        return entities.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public SessionDTO createSession(Long agentId, Long modelId, String title) {
        Session entity = new Session();
        entity.setUserId(UserContextUtil.requireUserId());
        entity.setAgentId(agentId);
        entity.setModelId(modelId);
        entity.setTitle(title);
        entity.setIsChild(false);
        sessionMapper.insert(entity);

        LambdaQueryWrapper<AgentTool> toolWrapper = new LambdaQueryWrapper<>();
        toolWrapper.eq(AgentTool::getAgentId, agentId);
        List<AgentTool> agentTools = agentToolMapper.selectList(toolWrapper);
        if (!agentTools.isEmpty()) {
            Long sessionId = entity.getId();
            for (AgentTool agentTool : agentTools) {
                SessionTool sessionTool = new SessionTool();
                sessionTool.setSessionId(sessionId);
                sessionTool.setToolId(agentTool.getToolId());
                sessionTool.setSessionAuth(agentTool.getSessionAuth());
                sessionToolMapper.insert(sessionTool);
            }
        }

        return toDTO(entity);
    }

    @Override
    public SessionDTO getSession(Long id) {
        Session entity = sessionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return toDTO(entity);
    }

    /**
     * 删除会话（级联假删）：会话、消息、会话工具/变量/技能关联均通过 @TableLogic
     * 软删（deleted=1，物理数据保留），不递归删除子孙会话；保留上下文与工具缓存清理。
     *
     * <p>不声明 @Transactional：消息假删经 {@code @DS("message")} 路由到 message 数据源，
     * 在主库事务内 @DS 路由会失效（与 rollback 拆分事务策略一致），故各数据源操作自行提交。</p>
     */
    @Override
    public void deleteSession(Long id) {
        Session entity = sessionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        // 会话工具关联假删（@TableLogic delete → UPDATE session_tool SET deleted=1）
        LambdaQueryWrapper<SessionTool> toolWrapper = new LambdaQueryWrapper<>();
        toolWrapper.eq(SessionTool::getSessionId, id);
        sessionToolMapper.delete(toolWrapper);

        // 会话变量假删
        LambdaQueryWrapper<SessionVariable> variableWrapper = new LambdaQueryWrapper<>();
        variableWrapper.eq(SessionVariable::getSessionId, id);
        sessionVariableMapper.delete(variableWrapper);

        // 会话技能关联假删
        LambdaQueryWrapper<SessionSkill> skillWrapper = new LambdaQueryWrapper<>();
        skillWrapper.eq(SessionSkill::getSessionId, id);
        sessionSkillMapper.delete(skillWrapper);

        // 消息假删（message 数据源，@TableLogic delete → UPDATE message SET deleted=1）
        LambdaQueryWrapper<Message> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(Message::getSessionId, id);
        messageMapper.delete(messageWrapper);

        // 会话假删（@TableLogic deleteById → UPDATE session SET deleted=1）
        sessionMapper.deleteById(id);
        agentContextManager.remove(IdConverter.toString(id));
        toolManager.clearSessionCache(IdConverter.toString(id));
    }

    @Override
    public int rollback(Long sessionId) {
        Session entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        int deleted = sessionManager.rollbackToLastUserMessage(IdConverter.toString(sessionId));
        agentContextManager.remove(IdConverter.toString(sessionId));
        return deleted;
    }

    @Override
    public List<SessionMessageDTO> getMessages(Long sessionId) {
        return getMessages(sessionId, null);
    }

    @Override
    public List<SessionMessageDTO> getMessages(Long sessionId, Boolean userInput) {
        Session entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        List<Message> messages = messageService.getAllMessages(sessionId);
        if (Boolean.TRUE.equals(userInput)) {
            messages = messages.stream()
                    .filter(m -> "user".equals(m.getRole()) && Boolean.TRUE.equals(m.getUserInput()))
                    .toList();
        }
        return defaultMessageDataProvider.toSessionMessageDTOs(messages);
    }

    @Override
    public List<SessionMessageDTO> getMessagesByConversationId(String conversationId) {
        List<Message> messages = messageMapper.selectByConversationId(conversationId);
        return defaultMessageDataProvider.toSessionMessageDTOs(messages);
    }

    @Override
    public List<SessionDTO> listChildSessions(Long parentId) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getParentSessionId, parentId);
        wrapper.eq(Session::getIsChild, true);
        wrapper.orderByDesc(Session::getCreateTime);

        List<Session> entities = sessionMapper.selectList(wrapper);
        return entities.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public void updateThinking(Long sessionId, Boolean thinking) {
        Session entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        entity.setThinking(thinking);
        sessionMapper.updateById(entity);
    }

    private SessionDTO toDTO(Session entity) {
        return SessionDTO.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .modelId(entity.getModelId())
                .title(entity.getTitle())
                .systemPrompt(entity.getSystemPrompt())
                .parentSessionId(entity.getParentSessionId())
                .isChild(entity.getIsChild())
                .description(entity.getDescription())
                .isEvaluation(entity.getIsEvaluation())
                .thinking(entity.getThinking())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .totalTokenUsed(entity.getTotalTokenUsed())
                .build();
    }
}
