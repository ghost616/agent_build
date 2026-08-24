package com.ghost616.platform.service.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.platform.dto.session.SessionDTO;
import com.ghost616.platform.entity.AgentTool;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.repository.AgentToolMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionToolMapper;
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

    @Override
    @Transactional
    public void deleteSession(Long id) {
        Session entity = sessionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        LambdaQueryWrapper<SessionTool> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(SessionTool::getSessionId, id);
        sessionToolMapper.delete(deleteWrapper);

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
        Session entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return defaultMessageDataProvider.toSessionMessageDTOs(messageService.getAllMessages(sessionId));
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
