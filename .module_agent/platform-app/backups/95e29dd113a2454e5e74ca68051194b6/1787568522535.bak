package com.ghost616.platform.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.dto.evaluation.EvaluationExecutionStatusDTO;
import com.ghost616.platform.entity.Evaluation;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.entity.SessionSkill;
import com.ghost616.platform.entity.SessionTool;
import com.ghost616.platform.repository.EvaluationMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.service.agent.DefaultChatDataCacheProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationExecutionService {

    public record ExecutionSessionContext(Long sessionId, List<MessageDataProvider.MessageDTO> userMessages) {}

    private static final long STATUS_TTL_MS = 3_600_000;

    private final EvaluationMapper evaluationMapper;
    private final SessionMapper sessionMapper;
    private final SessionToolMapper sessionToolMapper;
    private final SessionSkillMapper sessionSkillMapper;
    private final MessageDataProvider messageDataProvider;
    private final EvaluationResultGenerateService evaluationResultGenerateService;
    private final AsyncEvaluationExecutor asyncEvaluationExecutor;
    private final DefaultChatDataCacheProvider defaultChatDataCacheProvider;
    private final ThreadVariableHandler threadVariableHandler;

    private final Map<String, EvaluationExecutionStatusDTO> executionStatusMap = new ConcurrentHashMap<>();
    private final Map<String, Long> statusTimestamps = new ConcurrentHashMap<>();

    private final Map<String, EvaluationExecutionStatusDTO> generateStatusMap = new ConcurrentHashMap<>();
    private final Map<String, Long> generateStatusTimestamps = new ConcurrentHashMap<>();

    public EvaluationExecutionStatusDTO execute(Long evaluationId) {
        Evaluation evaluation = evaluationMapper.selectById(evaluationId);
        if (evaluation == null) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_FOUND);
        }

        Long benchmarkSessionId = evaluation.getBenchmarkSessionId();
        if (benchmarkSessionId == null) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_FOUND);
        }

        List<MessageDataProvider.MessageDTO> userMessages = getBenchmarkUserMessages(benchmarkSessionId);

        Session executionSession = copyBenchmarkSession(benchmarkSessionId);

        String statusKey = String.valueOf(evaluationId);
        EvaluationExecutionStatusDTO statusDTO = EvaluationExecutionStatusDTO.builder()
                .evaluationId(evaluationId)
                .executionSessionId(executionSession.getId())
                .status("PENDING")
                .currentStep(0)
                .totalSteps(userMessages.size())
                .build();
        executionStatusMap.put(statusKey, statusDTO);
        statusTimestamps.put(statusKey, System.currentTimeMillis());

        ThreadVariableWrapper threadVariableWrapper = threadVariableHandler.wrap();
        asyncEvaluationExecutor.executeAsync(evaluationId, executionSession, userMessages, executionStatusMap,
                threadVariableWrapper);

        String sessionId = String.valueOf(executionSession.getId());
        String cacheId = null;
        while (true) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            EvaluationExecutionStatusDTO current = executionStatusMap.get(statusKey);
            if (current != null && "FAILED".equals(current.getStatus())) {
                return current;
            }
            if (cacheId == null) {
                List<String> cacheIds = defaultChatDataCacheProvider.getCacheIdsBySessionId(sessionId);
                if (cacheIds.isEmpty()) {
                    continue;
                }
                cacheId = cacheIds.get(0);
            }
            if (defaultChatDataCacheProvider.getMaxChunkIndex(cacheId) > 0) {
                break;
            }
        }

        return statusDTO;
    }

    public EvaluationExecutionStatusDTO getStatus(Long evaluationId) {
        String statusKey = String.valueOf(evaluationId);
        EvaluationExecutionStatusDTO dto = executionStatusMap.get(statusKey);
        if (dto == null) {
            throw new BusinessException(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND);
        }
        return dto;
    }

    public ExecutionSessionContext createExecutionSession(Evaluation evaluation) {
        if (evaluation == null) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_FOUND);
        }

        Long benchmarkSessionId = evaluation.getBenchmarkSessionId();
        if (benchmarkSessionId == null) {
            throw new BusinessException(ErrorCode.EVALUATION_NOT_FOUND);
        }

        List<MessageDataProvider.MessageDTO> userMessages = getBenchmarkUserMessages(benchmarkSessionId);

        Session executionSession = copyBenchmarkSession(benchmarkSessionId);
        return new ExecutionSessionContext(executionSession.getId(), userMessages);
    }

    public ExecutionSessionContext createExecutionSession(Long evaluationId) {
        Evaluation evaluation = evaluationMapper.selectById(evaluationId);
        return createExecutionSession(evaluation);
    }

    public void generateResult(Long evaluationId, Long executionSessionId) {
        evaluationResultGenerateService.generate(evaluationId, executionSessionId);
    }

    public EvaluationExecutionStatusDTO generateResultAsync(Long evaluationId, Long executionSessionId) {
        String statusKey = evaluationId + ":" + executionSessionId;
        EvaluationExecutionStatusDTO statusDTO = EvaluationExecutionStatusDTO.builder()
                .evaluationId(evaluationId)
                .executionSessionId(executionSessionId)
                .status("RUNNING")
                .build();
        generateStatusMap.put(statusKey, statusDTO);
        generateStatusTimestamps.put(statusKey, System.currentTimeMillis());

        ThreadVariableWrapper threadVariableWrapper = threadVariableHandler.wrap();
        asyncEvaluationExecutor.generateResultAsync(evaluationId, executionSessionId, generateStatusMap,
                threadVariableWrapper);

        return statusDTO;
    }

    public EvaluationExecutionStatusDTO getGenerateStatus(Long evaluationId, Long executionSessionId) {
        String statusKey = evaluationId + ":" + executionSessionId;
        EvaluationExecutionStatusDTO dto = generateStatusMap.get(statusKey);
        if (dto == null) {
            throw new BusinessException(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND);
        }
        return dto;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupStaleStatuses() {
        long now = System.currentTimeMillis();
        statusTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > STATUS_TTL_MS) {
                executionStatusMap.remove(entry.getKey());
                return true;
            }
            return false;
        });
        generateStatusTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > STATUS_TTL_MS) {
                generateStatusMap.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private List<MessageDataProvider.MessageDTO> getBenchmarkUserMessages(Long benchmarkSessionId) {
        List<MessageDataProvider.MessageDTO> benchmarkMessages = messageDataProvider.getMessages(
                String.valueOf(benchmarkSessionId));
        List<MessageDataProvider.MessageDTO> userMessages = benchmarkMessages.stream()
                .filter(m -> "user".equals(m.role()))
                .filter(m -> m.rollback() == null || !m.rollback())
                .toList();
        if (userMessages.isEmpty()) {
            throw new BusinessException(ErrorCode.EVALUATION_BENCHMARK_NO_USER_MESSAGE);
        }
        return userMessages;
    }

    private Session copyBenchmarkSession(Long benchmarkSessionId) {
        Session benchmarkSession = sessionMapper.selectById(benchmarkSessionId);
        if (benchmarkSession == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        Session newSession = new Session();
        newSession.setAgentId(benchmarkSession.getAgentId());
        newSession.setModelId(benchmarkSession.getModelId());
        newSession.setTitle(benchmarkSession.getTitle() + "_exec");
        newSession.setSystemPrompt(benchmarkSession.getSystemPrompt());
        newSession.setThinking(benchmarkSession.getThinking());
        newSession.setIsEvaluation(true);
        sessionMapper.insert(newSession);

        Long newSessionId = newSession.getId();

        LambdaQueryWrapper<SessionTool> toolWrapper = new LambdaQueryWrapper<>();
        toolWrapper.eq(SessionTool::getSessionId, benchmarkSessionId);
        List<SessionTool> benchmarkTools = sessionToolMapper.selectList(toolWrapper);
        for (SessionTool st : benchmarkTools) {
            SessionTool newSt = new SessionTool();
            newSt.setSessionId(newSessionId);
            newSt.setToolId(st.getToolId());
            newSt.setSessionAuth(st.getSessionAuth());
            sessionToolMapper.insert(newSt);
        }

        LambdaQueryWrapper<SessionSkill> skillWrapper = new LambdaQueryWrapper<>();
        skillWrapper.eq(SessionSkill::getSessionId, benchmarkSessionId);
        List<SessionSkill> benchmarkSkills = sessionSkillMapper.selectList(skillWrapper);
        for (SessionSkill ss : benchmarkSkills) {
            SessionSkill newSs = new SessionSkill();
            newSs.setSessionId(newSessionId);
            newSs.setSkillId(ss.getSkillId());
            newSs.setSessionAuth(ss.getSessionAuth());
            sessionSkillMapper.insert(newSs);
        }

        return newSession;
    }
}
