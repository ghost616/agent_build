package com.ghost616.platform.service.evaluation;

import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.sendmessage.SendUserMessage;
import com.ghost616.agentbase.service.agent.AgentMessageProxy;
import com.ghost616.agentbase.service.agent.ChatService;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.dto.evaluation.EvaluationExecutionStatusDTO;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.session.EvaluationExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AsyncEvaluationExecutor 单元测试。
 *
 * <p>覆盖：generateResultAsync 状态流转；executeAsync 消息驱动循环——单层子会话、
 * 多级嵌套、子→父 sendParentMessage 回传续接、子会话失败回填后父链路继续不卡死、
 * 顶层消息失败 FAILED 语义、主/子全链完成才 COMPLETED 且 generate 被调用、
 * 评估执行上下文生命周期清理；drain 消息驱动语义——驱动 content 使用
 * {@link ChatService#SEND_USER_MESSAGE_MARKER}（而非槽位原文，避免重复持久化与模型输入污染）、
 * 槽位取出即清、空槽快速退出、达驱动上限安全退出且槽位无残留。</p>
 */
@ExtendWith(MockitoExtension.class)
class AsyncEvaluationExecutorTest {

    /** 与 AsyncEvaluationExecutor.MAX_DRIVEN_MESSAGES_PER_STEP 保持一致（上限值变更时同步更新） */
    private static final int MAX_DRIVEN_MESSAGES_PER_STEP = 100;

    @Mock
    private AgentMessageProxy agentMessageProxy;
    @Mock
    private EvaluationResultGenerateService evaluationResultGenerateService;

    private AsyncEvaluationExecutor executor;

    private static final Long EVALUATION_ID = 1L;
    private static final Long EXECUTION_SESSION_ID = 200L;
    private static final String STATUS_KEY = "1:200";

    @BeforeEach
    void setUp() {
        executor = new AsyncEvaluationExecutor(agentMessageProxy, evaluationResultGenerateService);
    }

    @AfterEach
    void tearDown() {
        EvaluationExecutionContext.clear();
    }

    private MessageDataProvider.MessageDTO userMsg(String content) {
        return new MessageDataProvider.MessageDTO(
                "1", String.valueOf(EXECUTION_SESSION_ID), "user", content,
                null, null, null, null, null, null, null, null, null, null, null, Boolean.TRUE);
    }

    private Session newExecutionSession() {
        Session executionSession = new Session();
        executionSession.setId(EXECUTION_SESSION_ID);
        executionSession.setThinking(Boolean.TRUE);
        return executionSession;
    }

    private Map<String, EvaluationExecutionStatusDTO> newStatusMap() {
        return new ConcurrentHashMap<>();
    }

    private SendUserMessage newSendMessage(String targetSessionId, String content) {
        return new SendUserMessage(targetSessionId, content, "conv-1", List.of("main-1"));
    }

    @Nested
    class GenerateResultAsyncTests {

        @Test
        void successfulGenerate_shouldUpdateStatusToCompleted() {
            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();

            executor.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID, statusMap, null);

            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
            EvaluationExecutionStatusDTO status = statusMap.get(STATUS_KEY);
            assertNotNull(status);
            assertEquals(EVALUATION_ID, status.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, status.getExecutionSessionId());
            assertEquals("COMPLETED", status.getStatus());
            assertEquals(1, status.getCurrentStep());
            assertEquals(1, status.getTotalSteps());
        }

        @Test
        void generateThrows_shouldUpdateStatusToFailed() {
            doThrow(new RuntimeException("generate failed"))
                    .when(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();

            executor.generateResultAsync(EVALUATION_ID, EXECUTION_SESSION_ID, statusMap, null);

            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
            EvaluationExecutionStatusDTO status = statusMap.get(STATUS_KEY);
            assertNotNull(status);
            assertEquals(EVALUATION_ID, status.getEvaluationId());
            assertEquals(EXECUTION_SESSION_ID, status.getExecutionSessionId());
            assertEquals("FAILED", status.getStatus());
            assertEquals(1, status.getCurrentStep());
            assertEquals(1, status.getTotalSteps());
        }
    }

    @Nested
    class ExecuteAsyncTests {

        @Test
        void successfulExecution_emptyMessages_shouldCompleteAndGenerate() {
            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();

            executor.executeAsync(EVALUATION_ID, newExecutionSession(), List.of(), statusMap, null);

            EvaluationExecutionStatusDTO status = statusMap.get(String.valueOf(EVALUATION_ID));
            assertNotNull(status);
            assertEquals("COMPLETED", status.getStatus());
            assertEquals(EVALUATION_ID, status.getEvaluationId());
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
            verify(agentMessageProxy, never()).sendUserMessageToSession(anyString(), anyString(), any(), any());
        }

        @Test
        void singleLevelSubSession_shouldDrivePendingMessage() {
            // 主会话执行期间产生一条发往子会话的 SendUserMessage（WEBSOCKET 打开方式），
            // 返回后消息驱动循环应以 SEND_USER_MESSAGE_MARKER 驱动该子会话完整执行
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(
                        newSendMessage("300", "child-task"));
                return Message.builder().role("assistant").content("main-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq(String.valueOf(EXECUTION_SESSION_ID)), eq("q1"), isNull(), eq(Boolean.TRUE));

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            InOrder inOrder = inOrder(agentMessageProxy, evaluationResultGenerateService);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession("200", "q1", null, Boolean.TRUE);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "300", ChatService.SEND_USER_MESSAGE_MARKER, null, null);
            inOrder.verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);

            EvaluationExecutionStatusDTO status = statusMap.get(String.valueOf(EVALUATION_ID));
            assertEquals("COMPLETED", status.getStatus());
            assertEquals(1, status.getCurrentStep());
            assertEquals(1, status.getTotalSteps());
        }

        @Test
        void multiLevelNestedSubSession_shouldDriveAllLevels() {
            // 主会话 → 子会话 A（300）→ 嵌套子会话 B（301），消息驱动循环逐级消费
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("300", "to-A"));
                return Message.builder().role("assistant").content("main-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("301", "to-B"));
                return Message.builder().role("assistant").content("A-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("300"), eq(ChatService.SEND_USER_MESSAGE_MARKER), isNull(), isNull());

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            InOrder inOrder = inOrder(agentMessageProxy);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession("200", "q1", null, Boolean.TRUE);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "300", ChatService.SEND_USER_MESSAGE_MARKER, null, null);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "301", ChatService.SEND_USER_MESSAGE_MARKER, null, null);

            assertEquals("COMPLETED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
        }

        @Test
        void parentBackflow_shouldContinueParentChain() {
            // 子会话完成回传（sendParentMessage）：向父会话（=执行主会话）写入待处理消息，
            // 驱动循环应以 SEND_USER_MESSAGE_MARKER + 执行会话 thinking 继续父会话链路
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("300", "to-A"));
                return Message.builder().role("assistant").content("main-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("200", "child-final"));
                return Message.builder().role("assistant").content("A-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("300"), eq(ChatService.SEND_USER_MESSAGE_MARKER), isNull(), isNull());

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            InOrder inOrder = inOrder(agentMessageProxy);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession("200", "q1", null, Boolean.TRUE);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "300", ChatService.SEND_USER_MESSAGE_MARKER, null, null);
            // 子→父回传续接：目标为执行主会话，content 为 marker、沿用执行会话 thinking
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "200", ChatService.SEND_USER_MESSAGE_MARKER, null, Boolean.TRUE);

            assertEquals("COMPLETED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
        }

        @Test
        void subSessionDriveFailure_shouldNotBreakEvaluation() {
            // 消息驱动子会话失败：记录日志回填占位，父链路继续，整体评估 COMPLETED
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("300", "to-A"));
                return Message.builder().role("assistant").content("main-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));
            doThrow(new RuntimeException("child boom"))
                    .when(agentMessageProxy).sendUserMessageToSession(
                            eq("300"), eq(ChatService.SEND_USER_MESSAGE_MARKER), isNull(), isNull());

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            assertEquals("COMPLETED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
        }

        @Test
        void topLevelMessageFailure_shouldMarkFailed() {
            doThrow(new RuntimeException("main boom"))
                    .when(agentMessageProxy).sendUserMessageToSession(
                            eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            assertEquals("FAILED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
            verify(evaluationResultGenerateService, never()).generate(anyLong(), anyLong());
        }

        @Test
        void completedStatusSetBeforeGenerate() {
            // 主/子全链执行完成才置 COMPLETED：generate 被调用时状态应为 COMPLETED
            final Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            doAnswer(inv -> {
                EvaluationExecutionStatusDTO current = statusMap.get(String.valueOf(EVALUATION_ID));
                assertNotNull(current);
                assertEquals("COMPLETED", current.getStatus());
                return null;
            }).when(evaluationResultGenerateService).generate(anyLong(), anyLong());

            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            assertEquals("COMPLETED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
        }

        @Test
        void evaluationContextClearedAfterExecution() {
            executor.executeAsync(EVALUATION_ID, newExecutionSession(), List.of(), newStatusMap(), null);
            assertNull(EvaluationExecutionContext.get(), "executeAsync 结束后应清理评估执行上下文");
        }

        @Test
        void multipleBaseMessages_shouldExecuteSequentially() {
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("300", "c1"));
                return null;
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("301", "c2"));
                return null;
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q2"), isNull(), eq(Boolean.TRUE));

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1"), userMsg("q2")), statusMap, null);

            InOrder inOrder = inOrder(agentMessageProxy);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession("200", "q1", null, Boolean.TRUE);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "300", ChatService.SEND_USER_MESSAGE_MARKER, null, null);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession("200", "q2", null, Boolean.TRUE);
            inOrder.verify(agentMessageProxy).sendUserMessageToSession(
                    "301", ChatService.SEND_USER_MESSAGE_MARKER, null, null);

            EvaluationExecutionStatusDTO status = statusMap.get(String.valueOf(EVALUATION_ID));
            assertEquals("COMPLETED", status.getStatus());
            assertEquals(2, status.getCurrentStep());
            assertEquals(2, status.getTotalSteps());
        }

        @Test
        void drivenMessage_shouldUseMarkerContentNotRawSlotContent() {
            // drain 驱动目标会话的 content 使用 ChatService.SEND_USER_MESSAGE_MARKER（而非槽位原文）：
            // 槽位消息原文在发事件前已以 userInput=false 落库并进入历史，marker 驱动 ChatService
            // 不重复保存用户消息/不加入历史，避免重复持久化与污染模型输入
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(
                        newSendMessage("300", "raw-child-input"));
                return Message.builder().role("assistant").content("main-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
            verify(agentMessageProxy).sendUserMessageToSession(
                    eq("300"), contentCaptor.capture(), isNull(), isNull());
            assertEquals(ChatService.SEND_USER_MESSAGE_MARKER, contentCaptor.getValue(),
                    "驱动目标会话的 content 应为 SEND_USER_MESSAGE_MARKER");
            assertNotEquals("raw-child-input", contentCaptor.getValue(),
                    "驱动 content 不应为槽位消息原文");
        }

        @Test
        void emptySlot_shouldExitDrainQuickly() {
            // 主会话执行未产生待处理 SendUserMessage：drain 空槽快速退出，无额外驱动调用
            doAnswer(inv -> Message.builder().role("assistant").content("main-done").build())
                    .when(agentMessageProxy).sendUserMessageToSession(
                            eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            verify(agentMessageProxy, times(1)).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));
            verify(agentMessageProxy, never()).sendUserMessageToSession(
                    eq("300"), anyString(), isNull(), isNull());
            assertEquals("COMPLETED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
        }

        @Test
        void driveLimitReached_shouldExitSafelyAndClearSlot() {
            // 单条基准消息驱动子链路达到上限 MAX_DRIVEN_MESSAGES_PER_STEP：
            // 循环达上限安全退出、整体评估不中断；每次驱动消息在调用前已从槽位取出即清，
            // 最后一次驱动（达上限退出时）槽位无残留
            doAnswer(inv -> {
                EvaluationExecutionContext.get().setPendingSendUserMessage(newSendMessage("300", "chain-1"));
                return Message.builder().role("assistant").content("main-done").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("200"), eq("q1"), isNull(), eq(Boolean.TRUE));

            AtomicInteger drivenCount = new AtomicInteger(0);
            AtomicBoolean slotEmptyWhenDriving = new AtomicBoolean(true);
            AtomicBoolean slotEmptyAtLimitExit = new AtomicBoolean(true);
            doAnswer(inv -> {
                // 取出的消息在驱动调用前已被清空：驱动执行期间槽位应为空（无残留）
                if (EvaluationExecutionContext.get().getAndClearPendingSendUserMessage() != null) {
                    slotEmptyWhenDriving.set(false);
                }
                int n = drivenCount.incrementAndGet();
                if (n < MAX_DRIVEN_MESSAGES_PER_STEP) {
                    // 前 N-1 次驱动各自产生下一条待处理消息，最后一次不再产生 → 达上限退出时槽位为空
                    EvaluationExecutionContext.get().setPendingSendUserMessage(
                            newSendMessage("300", "chain-" + (n + 1)));
                } else if (EvaluationExecutionContext.get().getAndClearPendingSendUserMessage() != null) {
                    // 最后一次驱动：槽位应已清空且不再写入，达上限退出后无残留
                    slotEmptyAtLimitExit.set(false);
                }
                return Message.builder().role("assistant").content("driven").build();
            }).when(agentMessageProxy).sendUserMessageToSession(
                    eq("300"), eq(ChatService.SEND_USER_MESSAGE_MARKER), isNull(), isNull());

            Map<String, EvaluationExecutionStatusDTO> statusMap = newStatusMap();
            executor.executeAsync(EVALUATION_ID, newExecutionSession(),
                    List.of(userMsg("q1")), statusMap, null);

            // 子链路驱动恰好执行到上限，随后安全退出（无异常、不 FAILED）
            verify(agentMessageProxy, times(MAX_DRIVEN_MESSAGES_PER_STEP)).sendUserMessageToSession(
                    eq("300"), eq(ChatService.SEND_USER_MESSAGE_MARKER), isNull(), isNull());
            assertEquals(MAX_DRIVEN_MESSAGES_PER_STEP, drivenCount.get());
            assertTrue(slotEmptyWhenDriving.get(), "每次驱动执行期间槽位应已清空（取出即清，无残留）");
            assertTrue(slotEmptyAtLimitExit.get(), "达驱动上限退出时槽位应已清空、无残留");
            assertEquals("COMPLETED", statusMap.get(String.valueOf(EVALUATION_ID)).getStatus());
            verify(evaluationResultGenerateService).generate(EVALUATION_ID, EXECUTION_SESSION_ID);
        }
    }
}
