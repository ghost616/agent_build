package com.ghost616.platform.service.evaluation;

import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.dto.evaluation.EvaluationExecutionStatusDTO;
import com.ghost616.platform.entity.Evaluation;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.repository.EvaluationMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.repository.SessionSkillMapper;
import com.ghost616.platform.repository.SessionToolMapper;
import com.ghost616.platform.service.agent.DefaultChatDataCacheProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationExecutionServiceTest {

    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private SessionToolMapper sessionToolMapper;
    @Mock
    private SessionSkillMapper sessionSkillMapper;
    @Mock
    private MessageDataProvider messageDataProvider;
    @Mock
    private EvaluationResultGenerateService evaluationResultGenerateService;
    @Mock
    private AsyncEvaluationExecutor asyncEvaluationExecutor;
    @Mock
    private DefaultChatDataCacheProvider defaultChatDataCacheProvider;
    @Mock
    private ThreadVariableHandler threadVariableHandler;

    private EvaluationExecutionService service;

    private static final Long EVALUATION_ID = 1L;
    private static final Long BENCHMARK_SESSION_ID = 100L;
    private static final Long EXECUTION_SESSION_ID = 200L;

    @BeforeEach
    void setUp() {
        service = spy(new EvaluationExecutionService(
                evaluationMapper, sessionMapper, sessionToolMapper,
                sessionSkillMapper, messageDataProvider,
                evaluationResultGenerateService, asyncEvaluationExecutor,
                defaultChatDataCacheProvider, threadVariableHandler
        ));
        when(sessionToolMapper.selectList(any())).thenReturn(List.of());
        when(sessionSkillMapper.selectList(any())).thenReturn(List.of());
        when(defaultChatDataCacheProvider.getCacheIdsBySessionId(anyString()))
                .thenReturn(List.of("cache-1"));
        when(defaultChatDataCacheProvider.getMaxChunkIndex(anyString())).thenReturn(1);
    }

    private Evaluation createEvaluation(Long benchmarkSessionId) {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(EVALUATION_ID);
        evaluation.setBenchmarkSessionId(benchmarkSessionId);
        return evaluation;
    }

    private MessageDataProvider.MessageDTO createUserMessage(String content) {
        return createUserMessage(content, null);
    }

    private MessageDataProvider.MessageDTO createUserMessage(String content, Boolean rollback) {
        return new MessageDataProvider.MessageDTO(
                "1", String.valueOf(BENCHMARK_SESSION_ID), "user", content,
                null, null, null, null, null, null, rollback, null, null, null, null
        );
    }

    private MessageDataProvider.MessageDTO createAssistantMessage(String content) {
        return new MessageDataProvider.MessageDTO(
                "2", String.valueOf(BENCHMARK_SESSION_ID), "assistant", content,
                null, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Nested
    class ExecuteTests {

        @Test
        void evaluationNotFound_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void benchmarkSessionIdNull_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation(null));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void noUserMessages_shouldThrow() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createAssistantMessage("hello")));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_BENCHMARK_NO_USER_MESSAGE, ex.getErrorCode());
        }

        @Test
        void normalExecution_shouldReturnStatusDTO() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test message")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);

            assertNotNull(result);
            assertEquals(EVALUATION_ID, result.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, result.getExecutionSessionId());
            assertEquals("PENDING", result.getStatus());
            assertEquals(0, result.getCurrentStep());
            assertEquals(1, result.getTotalSteps());
            verify(defaultChatDataCacheProvider).getCacheIdsBySessionId(String.valueOf(EXECUTION_SESSION_ID));
        }

        @Test
        void polling_cacheAvailable_shouldReturnStatusDTO() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test message")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);

            assertNotNull(result);
            assertEquals("PENDING", result.getStatus());
            verify(defaultChatDataCacheProvider).getCacheIdsBySessionId(String.valueOf(EXECUTION_SESSION_ID));
            verify(defaultChatDataCacheProvider).getMaxChunkIndex("cache-1");
        }

        @Test
        void polling_cacheListEmpty_shouldContinuePollingUntilCacheAvailable() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test message")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(defaultChatDataCacheProvider.getCacheIdsBySessionId(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(), List.of("cache-1"));
            when(defaultChatDataCacheProvider.getMaxChunkIndex("cache-1")).thenReturn(1);

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);

            assertNotNull(result);
            assertEquals("PENDING", result.getStatus());
            verify(defaultChatDataCacheProvider, times(2))
                    .getCacheIdsBySessionId(String.valueOf(EXECUTION_SESSION_ID));
            verify(defaultChatDataCacheProvider).getMaxChunkIndex("cache-1");
        }

        @Test
        void polling_cacheExistsDataNotWritten_shouldKeepPollingUntilDataWritten() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test message")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(defaultChatDataCacheProvider.getMaxChunkIndex("cache-1")).thenReturn(0, 1);

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);

            assertNotNull(result);
            assertEquals("PENDING", result.getStatus());
            verify(defaultChatDataCacheProvider).getCacheIdsBySessionId(String.valueOf(EXECUTION_SESSION_ID));
            verify(defaultChatDataCacheProvider, times(2)).getMaxChunkIndex("cache-1");
        }

        @Test
        void polling_statusFailed_shouldReturnFailedStatus() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test message")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));
            when(defaultChatDataCacheProvider.getCacheIdsBySessionId(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of());
            doAnswer(inv -> {
                Session s = inv.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, EvaluationExecutionStatusDTO> map = inv.getArgument(3);
                map.put(String.valueOf(EVALUATION_ID), EvaluationExecutionStatusDTO.builder()
                        .evaluationId(EVALUATION_ID)
                        .executionSessionId(s.getId())
                        .status("FAILED")
                        .currentStep(1)
                        .totalSteps(1)
                        .build());
                return null;
            }).when(asyncEvaluationExecutor).executeAsync(eq(EVALUATION_ID), any(Session.class), anyList(), anyMap(), any());

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);

            assertNotNull(result);
            assertEquals("FAILED", result.getStatus());
        }

        @Test
        void normalExecution_shouldPassThroughThinkingToExecutionSession() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test message")));
            Session benchmarkSession = createBenchmarkSession();
            benchmarkSession.setThinking(Boolean.TRUE);
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(benchmarkSession);
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            service.execute(EVALUATION_ID);

            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            verify(sessionMapper).insert(captor.capture());
            assertEquals(Boolean.TRUE, captor.getValue().getThinking());
        }

        @Test
        void rollbackTrueMessage_shouldBeFilteredOut() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("rollback msg", true)));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.execute(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_BENCHMARK_NO_USER_MESSAGE, ex.getErrorCode());
        }

        @Test
        void rollbackNullMessage_shouldBePreserved() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("normal msg")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);
            assertEquals(1, result.getTotalSteps());
        }

        @Test
        void rollbackFalseMessage_shouldBePreserved() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("not rollback", false)));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);
            assertEquals(1, result.getTotalSteps());
        }

        @Test
        void mixedMessages_shouldOnlyKeepNonRollbackUserMessages() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(
                            createUserMessage("keep me", false),
                            createUserMessage("rollback", true),
                            createUserMessage("keep me too", null)
                    ));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionStatusDTO result = service.execute(EVALUATION_ID);
            assertEquals(2, result.getTotalSteps());
        }
    }

    @Nested
    class GetStatusTests {

        @Test
        void nonExistentEvaluationId_shouldThrow() {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.getStatus(999L));
            assertEquals(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void existingStatus_shouldReturnStatusDTO() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            service.execute(EVALUATION_ID);

            EvaluationExecutionStatusDTO result = service.getStatus(EVALUATION_ID);
            assertNotNull(result);
            assertEquals(EVALUATION_ID, result.getEvaluationId());
            assertEquals("PENDING", result.getStatus());
        }
    }

    @Nested
    class CreateExecutionSessionTests {

        @Test
        void evaluationNotFound_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.createExecutionSession(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void benchmarkSessionIdNull_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation(null));
            BusinessException ex = assertThrows(BusinessException.class, () -> service.createExecutionSession(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void noUserMessages_shouldThrow() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createAssistantMessage("hello")));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.createExecutionSession(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_BENCHMARK_NO_USER_MESSAGE, ex.getErrorCode());
        }

        @Test
        void normalCreation_shouldReturnSessionId() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("test")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionService.ExecutionSessionContext result = service.createExecutionSession(EVALUATION_ID);
            assertEquals(EXECUTION_SESSION_ID, result.sessionId());
            assertEquals(1, result.userMessages().size());
        }

        @Test
        void rollbackTrueMessage_shouldBeFilteredOut_createSession() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("rollback msg", true)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.createExecutionSession(EVALUATION_ID));
            assertEquals(ErrorCode.EVALUATION_BENCHMARK_NO_USER_MESSAGE, ex.getErrorCode());
        }

        @Test
        void rollbackNullMessage_shouldBePreserved_createSession() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("normal msg")));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionService.ExecutionSessionContext result = service.createExecutionSession(EVALUATION_ID);
            assertEquals(EXECUTION_SESSION_ID, result.sessionId());
            assertEquals(1, result.userMessages().size());
        }

        @Test
        void rollbackFalseMessage_shouldBePreserved_createSession() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createUserMessage("not rollback", false)));
            when(sessionMapper.selectById(BENCHMARK_SESSION_ID)).thenReturn(createBenchmarkSession());
            doAnswer(inv -> {
                Session s = inv.getArgument(0);
                s.setId(EXECUTION_SESSION_ID);
                return null;
            }).when(sessionMapper).insert(any(Session.class));

            EvaluationExecutionService.ExecutionSessionContext result = service.createExecutionSession(EVALUATION_ID);
            assertEquals(EXECUTION_SESSION_ID, result.sessionId());
            assertEquals(1, result.userMessages().size());
        }
    }

    @Nested
    class GenerateResultTests {

        @Test
        void shouldDelegateToGenerateService() {
            service.generateResult(EVALUATION_ID, EXECUTION_SESSION_ID);
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
        }
    }

    @Nested
    class GenerateResultAsyncTests {

        @Test
        void shouldReturnRunningStatusAndDelegateAsync() {
            EvaluationExecutionStatusDTO result = service.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID);

            assertNotNull(result);
            assertEquals(EVALUATION_ID, result.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, result.getExecutionSessionId());
            assertEquals("RUNNING", result.getStatus());
            verify(asyncEvaluationExecutor).generateResultAsync(
                    eq(EVALUATION_ID), eq(EXECUTION_SESSION_ID), anyMap(), any());
        }

        @Test
        void shouldStoreStatusInGenerateStatusMap() {
            service.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID);

            EvaluationExecutionStatusDTO stored = service.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID);
            assertNotNull(stored);
            assertEquals(EVALUATION_ID, stored.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, stored.getExecutionSessionId());
            assertEquals("RUNNING", stored.getStatus());
        }
    }

    @Nested
    class GetGenerateStatusTests {

        @Test
        void nonExistentStatus_shouldThrow() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void existingStatus_shouldReturnStatusDTO() {
            service.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID);

            EvaluationExecutionStatusDTO result = service.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID);
            assertNotNull(result);
            assertEquals(EVALUATION_ID, result.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, result.getExecutionSessionId());
            assertEquals("RUNNING", result.getStatus());
        }
    }

    @Nested
    class CleanupStaleStatusesTests {

        @Test
        void staleGenerateStatus_shouldBeRemoved() throws Exception {
            service.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID);

            java.lang.reflect.Field timestampsField =
                    EvaluationExecutionService.class.getDeclaredField("generateStatusTimestamps");
            timestampsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Long> timestamps =
                    (java.util.Map<String, Long>) timestampsField.get(service);
            String key = EVALUATION_ID + ":" + EXECUTION_SESSION_ID;
            timestamps.put(key, System.currentTimeMillis() - 3_600_001L);

            service.cleanupStaleStatuses();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void freshGenerateStatus_shouldBeKept() throws Exception {
            service.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID);

            service.cleanupStaleStatuses();

            EvaluationExecutionStatusDTO result = service.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID);
            assertNotNull(result);
            assertEquals("RUNNING", result.getStatus());
        }
    }

    private Session createBenchmarkSession() {
        Session session = new Session();
        session.setId(BENCHMARK_SESSION_ID);
        session.setAgentId(50L);
        session.setModelId(1L);
        session.setTitle("benchmark");
        session.setSystemPrompt("You are a test assistant");
        return session;
    }
}
