package com.ghost616.platform.controller;

import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.dto.ApiResponse;
import com.ghost616.platform.dto.evaluation.EvaluationExecutionStatusDTO;
import com.ghost616.platform.dto.evaluation.EvaluationSessionCreateResponse;
import com.ghost616.platform.entity.Evaluation;
import com.ghost616.platform.repository.EvaluationMapper;
import com.ghost616.platform.service.evaluation.EvaluationExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationSessionControllerTest {

    @Mock
    private EvaluationExecutionService evaluationExecutionService;
    @Mock
    private EvaluationMapper evaluationMapper;

    private EvaluationSessionController controller;

    private static final Long EVALUATION_ID = 1L;
    private static final Long BENCHMARK_SESSION_ID = 100L;
    private static final Long EXECUTION_SESSION_ID = 200L;

    @BeforeEach
    void setUp() {
        controller = new EvaluationSessionController(
                evaluationExecutionService, evaluationMapper);
    }

    private Evaluation createEvaluation() {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(EVALUATION_ID);
        evaluation.setBenchmarkSessionId(BENCHMARK_SESSION_ID);
        return evaluation;
    }

    private MessageDataProvider.MessageDTO createUserMessage(String content) {
        return createUserMessage(content, null);
    }

    private MessageDataProvider.MessageDTO createUserMessage(String content, Boolean rollback) {
        return new MessageDataProvider.MessageDTO(
                "1", String.valueOf(BENCHMARK_SESSION_ID), "user", content,
                null, null, null, null, null, null, rollback, null, null, null, null, null
        );
    }

    private EvaluationExecutionService.ExecutionSessionContext context(Long sessionId, List<MessageDataProvider.MessageDTO> messages) {
        return new EvaluationExecutionService.ExecutionSessionContext(sessionId, messages);
    }

    @Test
    void evaluationNotFound_shouldThrow() {
        when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.createSession(EVALUATION_ID));
    }

    @Test
    void rollbackTrueMessage_shouldBeFilteredOut() {
        when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation());
        when(evaluationExecutionService.createExecutionSession(any(Evaluation.class)))
                .thenReturn(context(EXECUTION_SESSION_ID, List.of()));

        ApiResponse<EvaluationSessionCreateResponse> response = controller.createSession(EVALUATION_ID);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(EXECUTION_SESSION_ID, response.getData().getSessionId());
        assertTrue(response.getData().getUserMessages().isEmpty());
    }

    @Test
    void rollbackNullMessage_shouldBePreserved() {
        when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation());
        when(evaluationExecutionService.createExecutionSession(any(Evaluation.class)))
                .thenReturn(context(EXECUTION_SESSION_ID, List.of(createUserMessage("normal msg"))));

        ApiResponse<EvaluationSessionCreateResponse> response = controller.createSession(EVALUATION_ID);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().getUserMessages().size());
        assertEquals("normal msg", response.getData().getUserMessages().get(0));
    }

    @Test
    void rollbackFalseMessage_shouldBePreserved() {
        when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation());
        when(evaluationExecutionService.createExecutionSession(any(Evaluation.class)))
                .thenReturn(context(EXECUTION_SESSION_ID, List.of(createUserMessage("not rollback", false))));

        ApiResponse<EvaluationSessionCreateResponse> response = controller.createSession(EVALUATION_ID);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getData().getUserMessages().size());
        assertEquals("not rollback", response.getData().getUserMessages().get(0));
    }

    @Test
    void mixedMessages_shouldOnlyKeepNonRollbackUserMessages() {
        when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation());
        when(evaluationExecutionService.createExecutionSession(any(Evaluation.class)))
                .thenReturn(context(EXECUTION_SESSION_ID, List.of(
                        createUserMessage("keep me", false),
                        createUserMessage("rollback", true),
                        createUserMessage("keep me too", null)
                )));

        ApiResponse<EvaluationSessionCreateResponse> response = controller.createSession(EVALUATION_ID);

        assertTrue(response.isSuccess());
        assertEquals(3, response.getData().getUserMessages().size());
        assertEquals("keep me", response.getData().getUserMessages().get(0));
        assertEquals("rollback", response.getData().getUserMessages().get(1));
        assertEquals("keep me too", response.getData().getUserMessages().get(2));
    }

    @Test
    void generateResult_shouldReturnRunningStatus() {
        EvaluationExecutionStatusDTO status = EvaluationExecutionStatusDTO.builder()
                .evaluationId(EVALUATION_ID)
                .executionSessionId(EXECUTION_SESSION_ID)
                .status("RUNNING")
                .build();
        when(evaluationExecutionService.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID))
                .thenReturn(status);

        ApiResponse<EvaluationExecutionStatusDTO> response =
                controller.generateResult(EVALUATION_ID, EXECUTION_SESSION_ID);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(EVALUATION_ID, response.getData().getEvaluationId());
        assertEquals(EXECUTION_SESSION_ID, response.getData().getExecutionSessionId());
        assertEquals("RUNNING", response.getData().getStatus());
        verify(evaluationExecutionService).generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID);
    }

    @Test
    void generateStatus_shouldReturnCurrentStatus() {
        EvaluationExecutionStatusDTO status = EvaluationExecutionStatusDTO.builder()
                .evaluationId(EVALUATION_ID)
                .executionSessionId(EXECUTION_SESSION_ID)
                .status("COMPLETED")
                .build();
        when(evaluationExecutionService.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID))
                .thenReturn(status);

        ApiResponse<EvaluationExecutionStatusDTO> response =
                controller.generateStatus(EVALUATION_ID, EXECUTION_SESSION_ID);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals("COMPLETED", response.getData().getStatus());
        verify(evaluationExecutionService).getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID);
    }

    @Test
    void generateStatus_statusNotFound_shouldThrow() {
        when(evaluationExecutionService.getGenerateStatus(EVALUATION_ID, EXECUTION_SESSION_ID))
                .thenThrow(new BusinessException(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.generateStatus(EVALUATION_ID, EXECUTION_SESSION_ID));
        assertEquals(ErrorCode.EVALUATION_EXECUTION_STATUS_NOT_FOUND, ex.getErrorCode());
    }
}
