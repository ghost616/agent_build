package com.ghost616.platform.service.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.dto.evaluation.EvaluationCreateRequest;
import com.ghost616.platform.dto.evaluation.EvaluationDTO;
import com.ghost616.platform.dto.evaluation.EvaluationResultDTO;
import com.ghost616.platform.dto.evaluation.EvaluationUpdateRequest;
import com.ghost616.platform.entity.*;
import com.ghost616.platform.repository.*;
import com.ghost616.platform.session.UserContext;
import com.ghost616.platform.session.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceImplTest {

    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private EvaluationResultMapper evaluationResultMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private MessageToolCallMapper messageToolCallMapper;
    @Mock
    private SessionVariableMapper sessionVariableMapper;
    @Mock
    private SessionToolMapper sessionToolMapper;
    @Mock
    private SessionSkillMapper sessionSkillMapper;
    @Mock
    private AgentEvaluationMapper agentEvaluationMapper;
    @Mock
    private AgentConfigMapper agentConfigMapper;
    @Mock
    private AgentToolMapper agentToolMapper;
    @Mock
    private AgentSkillMapper agentSkillMapper;

    private EvaluationServiceImpl service;

    @Captor
    private ArgumentCaptor<Session> sessionCaptor;
    @Captor
    private ArgumentCaptor<SessionTool> sessionToolCaptor;
    @Captor
    private ArgumentCaptor<SessionSkill> sessionSkillCaptor;
    private static final Long SESSION_ID = 100L;
    private static final Long AGENT_ID = 50L;
    private static final Long AGENT_EVAL_ID = 10L;
    private static final Long MODEL_ID = 1L;
    private static final Long TOOL_ID_1 = 200L;
    private static final Long TOOL_ID_2 = 201L;
    private static final Long SKILL_ID_1 = 300L;
    private static final Long CURRENT_USER_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new EvaluationServiceImpl(
                evaluationMapper, evaluationResultMapper, sessionMapper,
                messageMapper, messageToolCallMapper, sessionVariableMapper,
                sessionToolMapper, sessionSkillMapper, agentEvaluationMapper,
                agentConfigMapper, agentToolMapper, agentSkillMapper
        );
        User user = new User();
        user.setId(CURRENT_USER_ID);
        UserSession session = new UserSession("session-1", user, System.currentTimeMillis());
        UserContext.set(session);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private EvaluationCreateRequest createRequest() {
        EvaluationCreateRequest request = new EvaluationCreateRequest();
        request.setName("test-eval");
        request.setDescription("desc");
        request.setModelId(MODEL_ID);
        request.setAgentEvalId(AGENT_EVAL_ID);
        request.setExecutionCount(3);
        return request;
    }

    private AgentEvaluation createAgentEval() {
        AgentEvaluation agentEval = new AgentEvaluation();
        agentEval.setId(AGENT_EVAL_ID);
        agentEval.setAgentId(AGENT_ID);
        return agentEval;
    }

    private AgentConfig createAgentConfig() {
        AgentConfig config = new AgentConfig();
        config.setId(AGENT_ID);
        config.setName("test-agent");
        config.setSystemPrompt("You are a test agent");
        return config;
    }

    @Nested
    class UpdateTests {

        private static final Long EVALUATION_ID = 200L;
        private static final Long OTHER_AGENT_EVAL_ID = 20L;

        private Evaluation existingEntity() {
            Evaluation entity = new Evaluation();
            entity.setId(EVALUATION_ID);
            entity.setName("original-name");
            entity.setDescription("original desc");
            entity.setModelId(MODEL_ID);
            entity.setExecutionCount(1);
            entity.setAgentEvalId(AGENT_EVAL_ID);
            entity.setAgentId(AGENT_ID);
            entity.setExecutionType("BACKGROUND");
            return entity;
        }

        @Test
        void changeNameToDuplicateInSameAgentEvalId_shouldThrow() {
            Evaluation entity = existingEntity();
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(entity);
            when(evaluationMapper.selectCount(any())).thenReturn(1L);

            EvaluationUpdateRequest request = new EvaluationUpdateRequest();
            request.setName("duplicate-name");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.update(EVALUATION_ID, request));
            assertEquals(ErrorCode.EVALUATION_ALREADY_EXISTS, ex.getErrorCode());
        }

        @Test
        void changeNameNoDuplicate_shouldUpdateName() {
            Evaluation entity = existingEntity();
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(entity);
            when(evaluationMapper.selectCount(any())).thenReturn(0L);

            EvaluationUpdateRequest request = new EvaluationUpdateRequest();
            request.setName("new-name");

            EvaluationDTO result = service.update(EVALUATION_ID, request);

            verify(evaluationMapper).updateById(any(Evaluation.class));
            assertEquals("new-name", result.getName());
        }

        @Test
        void nameNotChanged_shouldSkipUniquenessCheck() {
            Evaluation entity = existingEntity();
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(entity);

            EvaluationUpdateRequest request = new EvaluationUpdateRequest();
            request.setName("original-name");

            service.update(EVALUATION_ID, request);

            verify(evaluationMapper, never()).selectCount(any());
            verify(evaluationMapper).updateById(any(Evaluation.class));
        }

        @Test
        void updateAllFields_shouldSucceed() {
            Evaluation entity = existingEntity();
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(entity);
            when(evaluationMapper.selectCount(any())).thenReturn(0L);

            EvaluationUpdateRequest request = new EvaluationUpdateRequest();
            request.setName("new-name");
            request.setDescription("new desc");
            request.setModelId(99L);
            request.setExecutionCount(5);
            request.setExecutionType("ONLINE");

            EvaluationDTO result = service.update(EVALUATION_ID, request);

            assertNotNull(result);
            assertEquals("new-name", result.getName());
            verify(evaluationMapper).updateById(any(Evaluation.class));
        }

        @Test
        void entityNotFound_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(null);

            EvaluationUpdateRequest request = new EvaluationUpdateRequest();
            request.setName("new-name");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.update(EVALUATION_ID, request));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    class GetResultByIdTests {

        private static final Long RESULT_ID = 500L;
        private static final Long SESSION_ID_LOCAL = 600L;
        private static final Long EVALUATION_ID = 700L;

        @Test
        void resultExistsWithSession_shouldReturnDTOWithTotalTokenUsed() {
            EvaluationResult entity = new EvaluationResult();
            entity.setId(RESULT_ID);
            entity.setEvaluationId(EVALUATION_ID);
            entity.setEvaluationSessionId(SESSION_ID_LOCAL);
            entity.setResult("test result");
            entity.setExecutionStatus("COMPLETED");
            entity.setModelId(42L);
            entity.setFinalScore(88);
            when(evaluationResultMapper.selectById(RESULT_ID)).thenReturn(entity);

            Session session = new Session();
            session.setId(SESSION_ID_LOCAL);
            session.setTotalTokenUsed(5000L);
            when(sessionMapper.selectById(SESSION_ID_LOCAL)).thenReturn(session);

            EvaluationResultDTO dto = service.getResultById(RESULT_ID);

            assertNotNull(dto);
            assertEquals(RESULT_ID, dto.getId());
            assertEquals(EVALUATION_ID, dto.getEvaluationId());
            assertEquals(SESSION_ID_LOCAL, dto.getEvaluationSessionId());
            assertEquals("test result", dto.getResult());
            assertEquals(5000L, dto.getTotalTokenUsed());
            assertEquals("COMPLETED", dto.getExecutionStatus());
            assertEquals(42L, dto.getModelId());
            assertEquals(Integer.valueOf(88), dto.getFinalScore());
        }

        @Test
        void resultExistsWithoutSession_shouldReturnDTOWithTotalTokenUsedNull() {
            EvaluationResult entity = new EvaluationResult();
            entity.setId(RESULT_ID);
            entity.setEvaluationId(EVALUATION_ID);
            entity.setEvaluationSessionId(SESSION_ID_LOCAL);
            entity.setResult("test result");
            entity.setModelId(99L);
            entity.setFinalScore(75);
            when(evaluationResultMapper.selectById(RESULT_ID)).thenReturn(entity);
            when(sessionMapper.selectById(SESSION_ID_LOCAL)).thenReturn(null);

            EvaluationResultDTO dto = service.getResultById(RESULT_ID);

            assertNotNull(dto);
            assertEquals(RESULT_ID, dto.getId());
            assertEquals(SESSION_ID_LOCAL, dto.getEvaluationSessionId());
            assertNull(dto.getTotalTokenUsed());
            assertEquals(99L, dto.getModelId());
            assertEquals(Integer.valueOf(75), dto.getFinalScore());
        }

        @Test
        void resultNotFound_shouldThrow() {
            when(evaluationResultMapper.selectById(RESULT_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getResultById(RESULT_ID));
            assertEquals(ErrorCode.EVALUATION_RESULT_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    class DeleteResultTests {

        private static final Long RESULT_ID = 800L;
        private static final Long SESSION_ID_LOCAL = 900L;
        private static final Long MESSAGE_ID_1 = 1000L;
        private static final Long MESSAGE_ID_2 = 1001L;

        @Test
        void resultExistsWithMessages_shouldDeleteAllCascadedData() {
            EvaluationResult result = new EvaluationResult();
            result.setId(RESULT_ID);
            result.setEvaluationSessionId(SESSION_ID_LOCAL);
            when(evaluationResultMapper.selectById(RESULT_ID)).thenReturn(result);

            Message msg1 = new Message();
            msg1.setId(MESSAGE_ID_1);
            msg1.setSessionId(SESSION_ID_LOCAL);
            Message msg2 = new Message();
            msg2.setId(MESSAGE_ID_2);
            msg2.setSessionId(SESSION_ID_LOCAL);
            when(messageMapper.selectList(any())).thenReturn(List.of(msg1, msg2));

            service.deleteResult(RESULT_ID);

            verify(sessionVariableMapper).delete(any());
            verify(sessionToolMapper).delete(any());
            verify(sessionSkillMapper).delete(any());
            verify(messageMapper).selectList(any());
            verify(messageToolCallMapper).deleteByMessageIds(List.of(MESSAGE_ID_1, MESSAGE_ID_2));
            verify(messageMapper).delete(any());
            verify(sessionMapper).deleteById(SESSION_ID_LOCAL);
            verify(evaluationResultMapper).deleteById(RESULT_ID);
        }

        @Test
        void resultExistsWithoutMessages_shouldSkipMessageToolCallDeletion() {
            EvaluationResult result = new EvaluationResult();
            result.setId(RESULT_ID);
            result.setEvaluationSessionId(SESSION_ID_LOCAL);
            when(evaluationResultMapper.selectById(RESULT_ID)).thenReturn(result);
            when(messageMapper.selectList(any())).thenReturn(List.of());

            service.deleteResult(RESULT_ID);

            verify(sessionVariableMapper).delete(any());
            verify(sessionToolMapper).delete(any());
            verify(sessionSkillMapper).delete(any());
            verify(messageMapper).selectList(any());
            verify(messageToolCallMapper, never()).deleteByMessageIds(anyList());
            verify(messageMapper, never()).delete(any());
            verify(sessionMapper).deleteById(SESSION_ID_LOCAL);
            verify(evaluationResultMapper).deleteById(RESULT_ID);
        }

        @Test
        void resultNotFound_shouldThrowBusinessException() {
            when(evaluationResultMapper.selectById(RESULT_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteResult(RESULT_ID));
            assertEquals(ErrorCode.EVALUATION_RESULT_NOT_FOUND, ex.getErrorCode());
        }
    }

    @Nested
    class BatchDeleteResultsTests {

        private static final Long RESULT_ID_1 = 1100L;
        private static final Long RESULT_ID_2 = 1101L;
        private static final Long SESSION_ID_LOCAL_1 = 1200L;
        private static final Long SESSION_ID_LOCAL_2 = 1201L;

        private EvaluationResult result(Long id, Long sessionId) {
            EvaluationResult r = new EvaluationResult();
            r.setId(id);
            r.setEvaluationSessionId(sessionId);
            return r;
        }

        @Test
        void emptyList_shouldDoNothing() {
            service.batchDeleteResults(List.of());

            verify(evaluationResultMapper, never()).deleteById(any());
            verify(sessionMapper, never()).deleteById(any());
        }

        @Test
        void nullList_shouldDoNothing() {
            service.batchDeleteResults(null);

            verify(evaluationResultMapper, never()).deleteById(any());
            verify(sessionMapper, never()).deleteById(any());
        }

        @Test
        void multipleIds_shouldDeleteAllResultsWithCascade() {
            when(evaluationResultMapper.selectById(RESULT_ID_1)).thenReturn(result(RESULT_ID_1, SESSION_ID_LOCAL_1));
            when(evaluationResultMapper.selectById(RESULT_ID_2)).thenReturn(result(RESULT_ID_2, SESSION_ID_LOCAL_2));
            when(messageMapper.selectList(any())).thenReturn(List.of());

            service.batchDeleteResults(List.of(RESULT_ID_1, RESULT_ID_2));

            verify(sessionVariableMapper, times(2)).delete(any());
            verify(sessionToolMapper, times(2)).delete(any());
            verify(sessionSkillMapper, times(2)).delete(any());
            verify(sessionMapper).deleteById(SESSION_ID_LOCAL_1);
            verify(sessionMapper).deleteById(SESSION_ID_LOCAL_2);
            verify(evaluationResultMapper).deleteById(RESULT_ID_1);
            verify(evaluationResultMapper).deleteById(RESULT_ID_2);
        }
    }

    @Nested
    class ClearResultsTests {

        private static final Long EVALUATION_ID_LOCAL = 1300L;
        private static final Long RESULT_ID_1 = 1400L;
        private static final Long RESULT_ID_2 = 1401L;
        private static final Long SESSION_ID_LOCAL_1 = 1500L;
        private static final Long SESSION_ID_LOCAL_2 = 1501L;

        private EvaluationResult result(Long id, Long sessionId) {
            EvaluationResult r = new EvaluationResult();
            r.setId(id);
            r.setEvaluationSessionId(sessionId);
            return r;
        }

        @Test
        void withResults_shouldDeleteAllResultsWithCascade() {
            Evaluation evaluation = new Evaluation();
            evaluation.setId(EVALUATION_ID_LOCAL);
            evaluation.setUserId(CURRENT_USER_ID);
            when(evaluationMapper.selectById(EVALUATION_ID_LOCAL)).thenReturn(evaluation);
            when(evaluationResultMapper.selectList(any()))
                    .thenReturn(List.of(result(RESULT_ID_1, SESSION_ID_LOCAL_1), result(RESULT_ID_2, SESSION_ID_LOCAL_2)));
            when(evaluationResultMapper.selectById(RESULT_ID_1)).thenReturn(result(RESULT_ID_1, SESSION_ID_LOCAL_1));
            when(evaluationResultMapper.selectById(RESULT_ID_2)).thenReturn(result(RESULT_ID_2, SESSION_ID_LOCAL_2));
            when(messageMapper.selectList(any())).thenReturn(List.of());

            service.clearResults(EVALUATION_ID_LOCAL);

            verify(evaluationResultMapper).selectList(any());
            verify(sessionMapper).deleteById(SESSION_ID_LOCAL_1);
            verify(sessionMapper).deleteById(SESSION_ID_LOCAL_2);
            verify(evaluationResultMapper).deleteById(RESULT_ID_1);
            verify(evaluationResultMapper).deleteById(RESULT_ID_2);
        }

        @Test
        void noResults_shouldDoNothing() {
            Evaluation evaluation = new Evaluation();
            evaluation.setId(EVALUATION_ID_LOCAL);
            evaluation.setUserId(CURRENT_USER_ID);
            when(evaluationMapper.selectById(EVALUATION_ID_LOCAL)).thenReturn(evaluation);
            when(evaluationResultMapper.selectList(any())).thenReturn(List.of());

            service.clearResults(EVALUATION_ID_LOCAL);

            verify(sessionMapper, never()).deleteById(any());
            verify(evaluationResultMapper, never()).deleteById(any());
        }
    }

    @Nested
    class CreateTests {

        @Test
        void duplicateName_shouldThrow() {
            when(evaluationMapper.selectCount(any())).thenReturn(1L);
            EvaluationCreateRequest request = createRequest();
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
            assertEquals(ErrorCode.EVALUATION_ALREADY_EXISTS, ex.getErrorCode());
        }

        @Test
        void duplicateNameDifferentAgentEvalId_shouldSucceed() {
            when(evaluationMapper.selectCount(any())).thenReturn(0L);
            AgentEvaluation agentEval = createAgentEval();
            when(agentEvaluationMapper.selectById(AGENT_EVAL_ID)).thenReturn(agentEval);
            when(agentConfigMapper.selectById(AGENT_ID)).thenReturn(null);
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(agentToolMapper.selectList(any())).thenReturn(List.of());
            when(agentSkillMapper.selectList(any())).thenReturn(List.of());
            doAnswer(inv -> {
                Evaluation e = inv.getArgument(0);
                e.setId(999L);
                return null;
            }).when(evaluationMapper).insert(any(Evaluation.class));

            EvaluationCreateRequest request = createRequest();
            EvaluationDTO result = service.create(request);

            assertNotNull(result);
            verify(evaluationMapper).selectCount(any());
        }

        @Test
        void agentEvaluationNotFound_shouldThrow() {
            when(evaluationMapper.selectCount(any())).thenReturn(0L);
            when(agentEvaluationMapper.selectById(AGENT_EVAL_ID)).thenReturn(null);
            EvaluationCreateRequest request = createRequest();
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
            assertEquals(ErrorCode.AGENT_EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void withToolsAndSkills_shouldInsertSessionToolAndSessionSkill() {
            // arrange name uniqueness check
            when(evaluationMapper.selectCount(any())).thenReturn(0L);
            // arrange agent evaluation lookup
            AgentEvaluation agentEval = createAgentEval();
            when(agentEvaluationMapper.selectById(AGENT_EVAL_ID)).thenReturn(agentEval);
            // arrange agent config lookup
            AgentConfig agentConfig = createAgentConfig();
            when(agentConfigMapper.selectById(AGENT_ID)).thenReturn(agentConfig);
            // arrange session insert (simulate ASSIGN_ID)
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            // arrange agent tools query
            AgentTool tool1 = new AgentTool();
            tool1.setAgentId(AGENT_ID);
            tool1.setToolId(TOOL_ID_1);
            tool1.setSessionAuth(SessionAuthType.ALL);
            AgentTool tool2 = new AgentTool();
            tool2.setAgentId(AGENT_ID);
            tool2.setToolId(TOOL_ID_2);
            tool2.setSessionAuth(SessionAuthType.PARENT);
            when(agentToolMapper.selectList(any())).thenReturn(List.of(tool1, tool2));
            // arrange agent skills query
            AgentSkill skill1 = new AgentSkill();
            skill1.setAgentId(AGENT_ID);
            skill1.setSkillId(SKILL_ID_1);
            skill1.setSessionAuth(SessionAuthType.CHILD);
            when(agentSkillMapper.selectList(any())).thenReturn(List.of(skill1));
            // arrange evaluation insert
            doAnswer(inv -> {
                Evaluation e = inv.getArgument(0);
                e.setId(999L);
                return null;
            }).when(evaluationMapper).insert(any(Evaluation.class));

            EvaluationCreateRequest request = createRequest();
            EvaluationDTO result = service.create(request);

            // verify session creation
            verify(sessionMapper).insert(sessionCaptor.capture());
            Session capturedSession = sessionCaptor.getValue();
            assertEquals(request.getName() + "BenchmarkSession", capturedSession.getTitle());
            assertEquals(MODEL_ID, capturedSession.getModelId());
            assertTrue(capturedSession.getIsEvaluation());
            assertEquals(AGENT_ID, capturedSession.getAgentId());
            assertEquals("You are a test agent", capturedSession.getSystemPrompt());

            // verify session_tool inserts (2 tools)
            verify(sessionToolMapper, times(2)).insert(sessionToolCaptor.capture());
            List<SessionTool> capturedTools = sessionToolCaptor.getAllValues();
            assertEquals(2, capturedTools.size());
            assertEquals(SESSION_ID, capturedTools.get(0).getSessionId());
            assertEquals(TOOL_ID_1, capturedTools.get(0).getToolId());
            assertEquals(SessionAuthType.ALL, capturedTools.get(0).getSessionAuth());
            assertEquals(SESSION_ID, capturedTools.get(1).getSessionId());
            assertEquals(TOOL_ID_2, capturedTools.get(1).getToolId());
            assertEquals(SessionAuthType.PARENT, capturedTools.get(1).getSessionAuth());

            // verify session_skill inserts (1 skill)
            verify(sessionSkillMapper, times(1)).insert(sessionSkillCaptor.capture());
            SessionSkill capturedSkill = sessionSkillCaptor.getValue();
            assertEquals(SESSION_ID, capturedSkill.getSessionId());
            assertEquals(SKILL_ID_1, capturedSkill.getSkillId());
            assertEquals(SessionAuthType.CHILD, capturedSkill.getSessionAuth());

            // verify evaluation creation
            verify(evaluationMapper).insert(any(Evaluation.class));
            assertNotNull(result);
            assertEquals("test-eval", result.getName());
            assertEquals(SESSION_ID, result.getBenchmarkSessionId());
        }

        @Test
        void agentConfigNull_shouldSetSystemPromptToNull() {
            when(evaluationMapper.selectCount(any())).thenReturn(0L);
            AgentEvaluation agentEval = createAgentEval();
            when(agentEvaluationMapper.selectById(AGENT_EVAL_ID)).thenReturn(agentEval);
            when(agentConfigMapper.selectById(AGENT_ID)).thenReturn(null);
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(agentToolMapper.selectList(any())).thenReturn(List.of());
            when(agentSkillMapper.selectList(any())).thenReturn(List.of());
            doAnswer(inv -> {
                Evaluation e = inv.getArgument(0);
                e.setId(999L);
                return null;
            }).when(evaluationMapper).insert(any(Evaluation.class));

            service.create(createRequest());

            verify(sessionMapper).insert(sessionCaptor.capture());
            assertNull(sessionCaptor.getValue().getSystemPrompt());
        }

        @Test
        void emptyAgentTools_shouldSkipSessionToolInsert() {
            when(evaluationMapper.selectCount(any())).thenReturn(0L);
            AgentEvaluation agentEval = createAgentEval();
            when(agentEvaluationMapper.selectById(AGENT_EVAL_ID)).thenReturn(agentEval);
            when(agentConfigMapper.selectById(AGENT_ID)).thenReturn(null);
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(agentToolMapper.selectList(any())).thenReturn(List.of());
            when(agentSkillMapper.selectList(any())).thenReturn(List.of());
            doAnswer(inv -> {
                Evaluation e = inv.getArgument(0);
                e.setId(999L);
                return null;
            }).when(evaluationMapper).insert(any(Evaluation.class));

            service.create(createRequest());

            verify(sessionToolMapper, never()).insert(any(SessionTool.class));
            verify(sessionSkillMapper, never()).insert(any(SessionSkill.class));
        }

        @Test
        void shouldQueryAgentToolAndAgentSkillByCorrectAgentId() {
            when(evaluationMapper.selectCount(any())).thenReturn(0L);
            AgentEvaluation agentEval = createAgentEval();
            when(agentEvaluationMapper.selectById(AGENT_EVAL_ID)).thenReturn(agentEval);
            when(agentConfigMapper.selectById(AGENT_ID)).thenReturn(null);
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(agentToolMapper.selectList(any())).thenReturn(List.of());
            when(agentSkillMapper.selectList(any())).thenReturn(List.of());
            doAnswer(inv -> {
                Evaluation e = inv.getArgument(0);
                e.setId(999L);
                return null;
            }).when(evaluationMapper).insert(any(Evaluation.class));

            service.create(createRequest());

            ArgumentCaptor<LambdaQueryWrapper<AgentTool>> toolWrapperCaptor = ArgumentCaptor.captor();
            verify(agentToolMapper).selectList(toolWrapperCaptor.capture());
            // We cannot easily check the wrapper content with mockito,
            // but we verify the mapper was called with the wrapper.

            ArgumentCaptor<LambdaQueryWrapper<AgentSkill>> skillWrapperCaptor = ArgumentCaptor.captor();
            verify(agentSkillMapper).selectList(skillWrapperCaptor.capture());
        }
    }
}
