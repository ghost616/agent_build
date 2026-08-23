package com.ghost616.platform.service.evaluation;

import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.agentbase.enums.RequestType;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.agentbase.service.agent.ChatDataProvider;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import com.ghost616.platform.entity.Evaluation;
import com.ghost616.platform.entity.EvaluationResult;
import com.ghost616.platform.repository.EvaluationMapper;
import com.ghost616.platform.repository.EvaluationResultMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

@ExtendWith(MockitoExtension.class)
class EvaluationResultGenerateServiceTest {

    @Mock
    private EvaluationMapper evaluationMapper;
    @Mock
    private EvaluationResultMapper evaluationResultMapper;
    @Mock
    private MessageDataProvider messageDataProvider;
    @Mock
    private ChatDataProvider chatDataProvider;
    @Mock
    private ModelInvokerManager modelInvokerManager;
    @Mock
    private ModelInvoker modelInvoker;

    private EvaluationResultGenerateService service;

    private static final Long EVALUATION_ID = 1L;
    private static final Long EXECUTION_SESSION_ID = 200L;
    private static final Long BENCHMARK_SESSION_ID = 100L;
    private static final Long MODEL_ID = 10L;

    @Captor
    private ArgumentCaptor<EvaluationResult> resultCaptor;
    @Captor
    private ArgumentCaptor<ChatRequest> chatRequestCaptor;

    @BeforeEach
    void setUp() {
        service = new EvaluationResultGenerateService(
                evaluationMapper, evaluationResultMapper, messageDataProvider,
                chatDataProvider, modelInvokerManager
        );
    }

    private Evaluation createEvaluation(Long benchmarkSessionId) {
        Evaluation evaluation = new Evaluation();
        evaluation.setId(EVALUATION_ID);
        evaluation.setBenchmarkSessionId(benchmarkSessionId);
        evaluation.setModelId(MODEL_ID);
        return evaluation;
    }

    private MessageDataProvider.MessageDTO createMessage(String role, String content) {
        return new MessageDataProvider.MessageDTO(
                "1", "sessionId", role, content,
                null, null, null, null, null, null, null, null, null, null, null, null
        );
    }

    private MessageDataProvider.MessageDTO createMessageWithToolCalls(String role, String content,
                                                                       List<MessageDataProvider.ToolCallData> toolCalls) {
        return new MessageDataProvider.MessageDTO(
                "1", "sessionId", role, content,
                null, null, null, null, toolCalls, null, null, null, null, null, null, null
        );
    }

    private MessageDataProvider.MessageDTO createMessageWithToolResult(String role, String content,
                                                                        String toolResult) {
        return new MessageDataProvider.MessageDTO(
                "1", "sessionId", role, content,
                null, null, null, toolResult, null, null, null, null, null, null, null, null
        );
    }

    private MessageDataProvider.MessageDTO createMessageWithToolCallsAndResult(String role, String content,
                                                                                List<MessageDataProvider.ToolCallData> toolCalls,
                                                                                String toolResult) {
        return new MessageDataProvider.MessageDTO(
                "1", "sessionId", role, content,
                null, null, null, toolResult, toolCalls, null, null, null, null, null, null, null
        );
    }

    private String invokeBuildJudgeMessages(List<MessageDataProvider.MessageDTO> benchmark,
                                             List<MessageDataProvider.MessageDTO> execution) throws Exception {
        Method method = EvaluationResultGenerateService.class.getDeclaredMethod(
                "buildJudgeMessages", List.class, List.class);
        method.setAccessible(true);
        List<Message> messages = (List<Message>) method.invoke(service, benchmark, execution);
        return messages.get(0).getContent();
    }

    private String invokeAppendMessage(MessageDataProvider.MessageDTO msg) throws Exception {
        Method method = EvaluationResultGenerateService.class.getDeclaredMethod(
                "appendMessage", StringBuilder.class, MessageDataProvider.MessageDTO.class);
        method.setAccessible(true);
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, msg);
        return sb.toString();
    }

    @Nested
    class GenerateTests {

        @Test
        void evaluationNotFound_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void benchmarkSessionIdNull_shouldThrow() {
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(createEvaluation(null));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.EVALUATION_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void normalGeneration_shouldInsertResultWithModelIdAndFinalScore() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi there")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse evalResponse = new ChatResponse();
            evalResponse.setContent("评估结果内容");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(evalResponse)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(evaluationResultMapper).insert(resultCaptor.capture());
            EvaluationResult captured = resultCaptor.getValue();
            assertEquals(EVALUATION_ID, captured.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, captured.getEvaluationSessionId());
            assertEquals("评估结果内容", captured.getResult());
            assertEquals("COMPLETED", captured.getExecutionStatus());
            assertEquals(MODEL_ID, captured.getModelId());
            assertEquals(Integer.valueOf(85), captured.getFinalScore());
        }

        @Test
        void modelConfigNotFound_shouldThrow() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi")));
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.MODEL_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        void systemPrompt_containsPercentScoring() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse response = new ChatResponse();
            response.setContent("ok");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(response)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(modelInvoker, atLeastOnce()).invoke(chatRequestCaptor.capture());
            String systemContent = chatRequestCaptor.getAllValues().get(0).getMessages().get(0).getContent();
            assertTrue(systemContent.contains("百分制（0-100分）"));
            assertTrue(systemContent.contains("相关性（0-25分）"));
            assertTrue(systemContent.contains("准确性（0-35分）"));
            assertTrue(systemContent.contains("完整性（0-40分）"));
        }

        @Test
        void systemPrompt_containsToolErrorCompletenessZero() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse response = new ChatResponse();
            response.setContent("ok");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(response)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(modelInvoker, atLeastOnce()).invoke(chatRequestCaptor.capture());
            String systemContent = chatRequestCaptor.getAllValues().get(0).getMessages().get(0).getContent();
            assertTrue(systemContent.contains("因工具调用错误"));
        }

        @Test
        void messagesWithToolCalls_shouldIncludeInPrompt() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            List<MessageDataProvider.ToolCallData> toolCalls = List.of(
                    new MessageDataProvider.ToolCallData("tc1", "getWeather", "{\"loc\":\"Beijing\"}")
            );
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessageWithToolCalls("assistant", "let me check", toolCalls)));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "thanks")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse response = new ChatResponse();
            response.setContent("ok");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(response)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(modelInvoker, atLeastOnce()).invoke(chatRequestCaptor.capture());
            String systemContent = chatRequestCaptor.getAllValues().get(0).getMessages().get(0).getContent();
            assertTrue(systemContent.contains("工具调用"));
            assertTrue(systemContent.contains("getWeather"));
            assertTrue(systemContent.contains("Beijing"));
        }

        @Test
        void messagesWithToolResult_shouldIncludeInPrompt() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "weather?")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessageWithToolResult("tool", "response", "{\"temp\":25}")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse response = new ChatResponse();
            response.setContent("ok");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(response)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(modelInvoker, atLeastOnce()).invoke(chatRequestCaptor.capture());
            String systemContent = chatRequestCaptor.getAllValues().get(0).getMessages().get(0).getContent();
            assertTrue(systemContent.contains("工具结果"));
            assertTrue(systemContent.contains("temp"));
        }

        @Test
        void modelInvokeThrows_shouldThrow() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            when(modelInvoker.invoke(any(ChatRequest.class))).thenThrow(new RuntimeException("API error"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.EVALUATION_RESULT_GENERATE_ERROR, ex.getErrorCode());
        }

        @Test
        void responsesRequestType_shouldPutJudgeContentInInstructionsAndOnlyUserMessageInMessages() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi there")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.RESPONSES.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse evalResponse = new ChatResponse();
            evalResponse.setContent("评估结果内容");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(evalResponse)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(modelInvoker, atLeastOnce()).invoke(chatRequestCaptor.capture());
            ChatRequest firstRequest = chatRequestCaptor.getAllValues().get(0);
            assertNotNull(firstRequest.getInstructions());
            assertTrue(firstRequest.getInstructions().contains("百分制（0-100分）"), "instructions 应包含评分规则");
            assertTrue(firstRequest.getInstructions().contains("【user】: hello"), "instructions 应包含基准会话消息");
            assertTrue(firstRequest.getInstructions().contains("【assistant】: hi there"), "instructions 应包含执行会话消息");
            assertEquals(1, firstRequest.getMessages().size(), "messages 只应包含 user prompt");
            assertEquals("user", firstRequest.getMessages().get(0).getRole());
            assertEquals("请对执行会话的回复质量进行评估。", firstRequest.getMessages().get(0).getContent());
        }

        @Test
        void responsesStatelessRequestType_shouldPutJudgeContentInInstructionsAndOnlyUserMessageInMessages() throws Exception {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi there")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.RESPONSES_STATELESS.getCode());
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);
            ChatResponse evalResponse = new ChatResponse();
            evalResponse.setContent("评估结果内容");
            ChatResponse scoreResponse = new ChatResponse();
            scoreResponse.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class)))
                    .thenReturn(evalResponse)
                    .thenReturn(scoreResponse);

            service.generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            verify(modelInvoker, atLeastOnce()).invoke(chatRequestCaptor.capture());
            ChatRequest firstRequest = chatRequestCaptor.getAllValues().get(0);
            assertNotNull(firstRequest.getInstructions());
            assertTrue(firstRequest.getInstructions().contains("百分制（0-100分）"));
            assertEquals(1, firstRequest.getMessages().size(), "messages 只应包含 user prompt");
            assertEquals("请对执行会话的回复质量进行评估。", firstRequest.getMessages().get(0).getContent());
        }

        @Test
        void unsupportedRequestType_shouldThrowModelUnsupported() {
            Evaluation evaluation = createEvaluation(BENCHMARK_SESSION_ID);
            when(evaluationMapper.selectById(EVALUATION_ID)).thenReturn(evaluation);
            when(messageDataProvider.getMessages(String.valueOf(BENCHMARK_SESSION_ID)))
                    .thenReturn(List.of(createMessage("user", "hello")));
            when(messageDataProvider.getMessages(String.valueOf(EXECUTION_SESSION_ID)))
                    .thenReturn(List.of(createMessage("assistant", "hi there")));
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform", "unknown");
            when(chatDataProvider.getModelConfig(String.valueOf(MODEL_ID))).thenReturn(configData);
            when(modelInvokerManager.getInvoker(configData)).thenReturn(modelInvoker);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generate(EVALUATION_ID, EXECUTION_SESSION_ID));
            assertEquals(ErrorCode.MODEL_UNSUPPORTED, ex.getErrorCode());
        }
    }

    @Nested
    class BuildJudgeMessagesTests {

        @Test
        void nullBenchmarkMessages_shouldHandleGracefully() throws Exception {
            String result = invokeBuildJudgeMessages(null, List.of(createMessage("user", "hi")));
            assertTrue(result.contains("无基准消息"));
            assertFalse(result.contains("无执行消息"));
        }

        @Test
        void nullExecutionMessages_shouldHandleGracefully() throws Exception {
            String result = invokeBuildJudgeMessages(List.of(createMessage("user", "hi")), null);
            assertTrue(result.contains("无执行消息"));
        }

        @Test
        void emptyMessages_shouldHandleGracefully() throws Exception {
            String result = invokeBuildJudgeMessages(List.of(), List.of());
            assertTrue(result.contains("无基准消息"));
            assertTrue(result.contains("无执行消息"));
        }

        @Test
        void percentScoringRules_arePresentInSystemPrompt() throws Exception {
            String result = invokeBuildJudgeMessages(List.of(createMessage("user", "hi")),
                    List.of(createMessage("assistant", "hello")));
            assertTrue(result.contains("百分制（0-100分）"));
            assertTrue(result.contains("相关性（0-25分）"));
            assertTrue(result.contains("准确性（0-35分）"));
            assertTrue(result.contains("完整性（0-40分）"));
        }

        @Test
        void toolCallTypeAndParamConsistency_mentionedInAccuracyRule() throws Exception {
            String result = invokeBuildJudgeMessages(List.of(createMessage("user", "hi")),
                    List.of(createMessage("assistant", "hello")));
            assertTrue(result.contains("工具调用的类型和参数须与基准会话完全一致"), "准确性维度应提及工具调用类型和参数一致性");
        }

        @Test
        void toolErrorCompletenessZero_mentionedInCompletenessRule() throws Exception {
            String result = invokeBuildJudgeMessages(List.of(createMessage("user", "hi")),
                    List.of(createMessage("assistant", "hello")));
            assertTrue(result.contains("因工具调用错误"));
        }

        @Test
        void appendMessage_withToolCalls_includesToolCallInfo() throws Exception {
            List<MessageDataProvider.ToolCallData> toolCalls = List.of(
                    new MessageDataProvider.ToolCallData("tc1", "getWeather", "{\"loc\":\"Beijing\"}"),
                    new MessageDataProvider.ToolCallData("tc2", "getTime", "{\"tz\":\"UTC\"}")
            );
            MessageDataProvider.MessageDTO msg = createMessageWithToolCalls("assistant", "checking...", toolCalls);
            String result = invokeAppendMessage(msg);
            assertTrue(result.contains("工具调用 1"));
            assertTrue(result.contains("工具调用 2"));
            assertTrue(result.contains("getWeather"));
            assertTrue(result.contains("getTime"));
        }

        @Test
        void appendMessage_withToolResult_includesResultInfo() throws Exception {
            MessageDataProvider.MessageDTO msg = createMessageWithToolResult("tool", "{\"temp\":25}", "成功获取数据");
            String result = invokeAppendMessage(msg);
            assertTrue(result.contains("工具结果"));
            assertTrue(result.contains("成功获取数据"));
        }

        @Test
        void appendMessage_withoutToolCallsOrResult_noExtraOutput() throws Exception {
            MessageDataProvider.MessageDTO msg = createMessage("user", "plain text");
            String result = invokeAppendMessage(msg);
            assertFalse(result.contains("工具调用"));
            assertFalse(result.contains("工具结果"));
        }

        @Test
        void appendMessage_withNullRole_usesUnknown() throws Exception {
            MessageDataProvider.MessageDTO msg = createMessage(null, "test content");
            String result = invokeAppendMessage(msg);
            assertTrue(result.contains("【unknown】"));
        }

        @Test
        void appendMessage_withNullContent_usesEmpty() throws Exception {
            MessageDataProvider.MessageDTO msg = createMessage("user", null);
            String result = invokeAppendMessage(msg);
            assertTrue(result.contains("【user】: "));
            assertFalse(result.contains("null"));
        }
    }

    @Nested
    class ExtractFinalScoreTests {

        private Integer invokeExtractFinalScore(String evaluationResult) throws Exception {
            Method method = EvaluationResultGenerateService.class.getDeclaredMethod(
                    "extractFinalScore", String.class, ModelConfigData.class, ModelInvoker.class);
            method.setAccessible(true);
            ModelConfigData configData = new ModelConfigData("id", "key", "url", "model", 0.5, 100, "platform",
                    RequestType.COMPLETIONS.getCode());
            return (Integer) method.invoke(service, evaluationResult, configData, modelInvoker);
        }

        @Test
        void plainNumericText_shouldReturnNumber() throws Exception {
            ChatResponse response = new ChatResponse();
            response.setContent("85");
            when(modelInvoker.invoke(any(ChatRequest.class))).thenReturn(response);

            Integer result = invokeExtractFinalScore("评估结果很不错，85分");

            assertEquals(Integer.valueOf(85), result);
        }

        @Test
        void textWithNonNumericChars_shouldExtractNumber() throws Exception {
            ChatResponse response = new ChatResponse();
            response.setContent("最终评分：92分");
            when(modelInvoker.invoke(any(ChatRequest.class))).thenReturn(response);

            Integer result = invokeExtractFinalScore("评估表现良好");

            assertEquals(Integer.valueOf(92), result);
        }

        @Test
        void modelInvokeThrows_shouldReturnNull() throws Exception {
            when(modelInvoker.invoke(any(ChatRequest.class))).thenThrow(new RuntimeException("API error"));

            Integer result = invokeExtractFinalScore("评估结果");

            assertNull(result);
        }

        @Test
        void responseContentContainsOnlyNonDigits_shouldReturnNull() throws Exception {
            ChatResponse response = new ChatResponse();
            response.setContent("评分失败");
            when(modelInvoker.invoke(any(ChatRequest.class))).thenReturn(response);

            Integer result = invokeExtractFinalScore("无法评估");

            assertNull(result);
        }
    }
}
