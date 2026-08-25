package com.ghost616.platform.service.agent_evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.dto.agent_evaluation.AgentEvaluationCreateRequest;
import com.ghost616.platform.dto.agent_evaluation.AgentEvaluationDTO;
import com.ghost616.platform.dto.agent_evaluation.AgentEvaluationUpdateRequest;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.AgentEvaluation;
import com.ghost616.platform.entity.Evaluation;
import com.ghost616.platform.entity.EvaluationResult;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.MessageToolCall;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionSkill;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.entity.SessionVariable;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.AgentEvaluationMapper;
import com.ghost616.platform.repository.EvaluationMapper;
import com.ghost616.platform.repository.EvaluationResultMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.MessageToolCallMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.repository.SessionVariableMapper;
import com.ghost616.platform.session.UserContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体评估业务实现。创建数据时从 {@link UserContextUtil} 获取当前登录用户填充 user_id，
 * 查询/列表仅返回当前用户数据，单条访问校验数据归属，实现智能体评估数据用户隔离。
 */
@Service
@RequiredArgsConstructor
public class AgentEvaluationServiceImpl implements AgentEvaluationService {

    private final AgentEvaluationMapper agentEvaluationMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final EvaluationMapper evaluationMapper;
    private final EvaluationResultMapper evaluationResultMapper;
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final MessageToolCallMapper messageToolCallMapper;
    private final SessionVariableMapper sessionVariableMapper;
    private final SessionToolMapper sessionToolMapper;
    private final SessionSkillMapper sessionSkillMapper;

    @Override
    public List<AgentEvaluationDTO> list() {
        LambdaQueryWrapper<AgentEvaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEvaluation::getUserId, UserContextUtil.requireUserId());
        wrapper.orderByDesc(AgentEvaluation::getCreateTime);
        List<AgentEvaluation> entities = agentEvaluationMapper.selectList(wrapper);
        return entities.stream().map(this::toDTO).toList();
    }

    @Override
    public AgentEvaluationDTO getById(Long id) {
        AgentEvaluation entity = agentEvaluationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.AGENT_EVALUATION_NOT_FOUND);
        }
        requireOwned(entity);
        return toDTO(entity);
    }

    @Override
    @Transactional
    public AgentEvaluationDTO create(AgentEvaluationCreateRequest request) {
        Long userId = UserContextUtil.requireUserId();
        LambdaQueryWrapper<AgentEvaluation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentEvaluation::getUserId, userId)
                .eq(AgentEvaluation::getName, request.getName());
        if (agentEvaluationMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.AGENT_EVALUATION_ALREADY_EXISTS);
        }

        AgentEvaluation entity = new AgentEvaluation();
        entity.setUserId(userId);
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setAgentId(request.getAgentId());
        agentEvaluationMapper.insert(entity);

        return toDTO(entity);
    }

    @Override
    @Transactional
    public AgentEvaluationDTO update(Long id, AgentEvaluationUpdateRequest request) {
        AgentEvaluation entity = agentEvaluationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.AGENT_EVALUATION_NOT_FOUND);
        }
        requireOwned(entity);

        if (request.getName() != null) {
            if (!request.getName().equals(entity.getName())) {
                LambdaQueryWrapper<AgentEvaluation> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AgentEvaluation::getUserId, entity.getUserId())
                        .eq(AgentEvaluation::getName, request.getName());
                if (agentEvaluationMapper.selectCount(wrapper) > 0) {
                    throw new BusinessException(ErrorCode.AGENT_EVALUATION_ALREADY_EXISTS);
                }
                entity.setName(request.getName());
            }
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getAgentId() != null) {
            entity.setAgentId(request.getAgentId());
        }

        agentEvaluationMapper.updateById(entity);

        return toDTO(entity);
    }

    @Override
    public void delete(Long id) {
        AgentEvaluation entity = agentEvaluationMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.AGENT_EVALUATION_NOT_FOUND);
        }
        requireOwned(entity);

        LambdaQueryWrapper<Evaluation> evalWrapper = new LambdaQueryWrapper<>();
        evalWrapper.eq(Evaluation::getAgentEvalId, id);
        List<Evaluation> evaluations = evaluationMapper.selectList(evalWrapper);

        List<Long> evalIds = evaluations.stream().map(Evaluation::getId).toList();

        if (!evalIds.isEmpty()) {
            LambdaQueryWrapper<EvaluationResult> resultWrapper = new LambdaQueryWrapper<>();
            resultWrapper.in(EvaluationResult::getEvaluationId, evalIds);
            List<EvaluationResult> allResults = evaluationResultMapper.selectList(resultWrapper);

            List<Long> evalSessionIds = allResults.stream()
                    .map(EvaluationResult::getEvaluationSessionId)
                    .distinct()
                    .filter(s -> s != null)
                    .toList();

            List<Long> benchmarkSessionIds = evaluations.stream()
                    .map(Evaluation::getBenchmarkSessionId)
                    .distinct()
                    .filter(s -> s != null)
                    .toList();

            List<Long> allSessionIds = evalSessionIds.stream()
                    .filter(s -> !benchmarkSessionIds.contains(s))
                    .collect(Collectors.toList());
            allSessionIds.addAll(benchmarkSessionIds);

            for (Long sessionId : allSessionIds) {
                sessionVariableMapper.delete(new LambdaQueryWrapper<SessionVariable>()
                        .eq(SessionVariable::getSessionId, sessionId));
                sessionToolMapper.delete(new LambdaQueryWrapper<SessionTool>()
                        .eq(SessionTool::getSessionId, sessionId));
                sessionSkillMapper.delete(new LambdaQueryWrapper<SessionSkill>()
                        .eq(SessionSkill::getSessionId, sessionId));

                LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
                msgWrapper.eq(Message::getSessionId, sessionId);
                List<Message> messages = messageMapper.selectList(msgWrapper);
                if (!messages.isEmpty()) {
                    List<Long> messageIds = messages.stream().map(Message::getId).toList();
                    messageToolCallMapper.deleteByMessageIds(messageIds);
                    messageMapper.delete(msgWrapper);
                }

                sessionMapper.deleteById(sessionId);
            }

            evaluationResultMapper.delete(resultWrapper);
            evaluationMapper.delete(evalWrapper);
        }

        agentEvaluationMapper.deleteById(id);
    }

    private AgentEvaluationDTO toDTO(AgentEvaluation entity) {
        String agentName = null;
        if (entity.getAgentId() != null) {
            AgentConfig agentConfig = agentConfigMapper.selectById(entity.getAgentId());
            if (agentConfig != null) {
                agentName = agentConfig.getName();
            }
        }

        return AgentEvaluationDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .agentId(entity.getAgentId())
                .agentName(agentName)
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    /**
     * 校验智能体评估归属当前用户，非本人数据按不存在处理（不泄露数据存在性）。
     *
     * @param entity 智能体评估实体
     */
    private void requireOwned(AgentEvaluation entity) {
        Long userId = UserContextUtil.requireUserId();
        if (entity.getUserId() != null && !entity.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AGENT_EVALUATION_NOT_FOUND);
        }
    }
}
