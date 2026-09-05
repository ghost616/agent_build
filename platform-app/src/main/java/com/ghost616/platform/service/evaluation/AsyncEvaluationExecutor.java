package com.ghost616.platform.service.evaluation;

import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.sendmessage.SendUserMessage;
import com.ghost616.agentbase.service.agent.AgentMessageProxy;
import com.ghost616.agentbase.service.agent.ChatDataCacheManager;
import com.ghost616.agentbase.service.agent.ChatService;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.platform.dto.evaluation.EvaluationExecutionStatusDTO;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.session.EvaluationExecutionContext;
import com.ghost616.platform.session.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 评估异步执行器（@Async 入口）。
 *
 * <p>负责 headless 执行评估：逐条重放基准用户消息，期间完整执行主会话 + 子会话全链路
 * （含多级嵌套）。执行入口创建并写入 {@link EvaluationExecutionContext}（评估执行标记），
 * 经 {@link com.ghost616.platform.session.ContextThreadVariableHandler} 传播到工具异步线程、
 * 子会话后台驱动线程与消息分发线程；每条基准用户消息执行后逐条消费上下文待处理列表中的
 * {@link SendUserMessage}（覆盖子→父 sendParentMessage 回传续接与消息驱动型子会话；消费前按
 * 事件原始 conversationId 移除目标会话已存在的缓存条目，避免驱动重入时 DUPLICATE_KEY/追加已结束缓存，
 * 并透传该 conversationId 驱动保持与已落库消息的对话归组一致），
 * 主/子全链执行完成才置 COMPLETED，再生成评估结果。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncEvaluationExecutor {

    /** 单条基准用户消息触发驱动（SendUserMessage 槽位消费）的最大消息数，防止异常会话陷入无限消息循环 */
    private static final int MAX_DRIVEN_MESSAGES_PER_STEP = 100;

    private final AgentMessageProxy agentMessageProxy;
    private final EvaluationResultGenerateService evaluationResultGenerateService;
    private final ChatDataCacheManager chatDataCacheManager;

    /**
     * 异步生成评估结果。任务提交方（{@link EvaluationExecutionService}）通过
     * {@link ThreadVariableWrapper} 传播当前登录用户，此处先恢复用户上下文，
     * 执行结束后在 finally 中清理，避免线程复用导致会话串号。
     *
     * @param evaluationId          评估 ID
     * @param executionSessionId    执行会话 ID
     * @param generateStatusMap     结果生成状态缓存
     * @param threadVariableWrapper 线程变量包装器（提交线程捕获的用户上下文快照，可为 null）
     */
    @Async
    public void generateResultAsync(Long evaluationId, Long executionSessionId,
                                    Map<String, EvaluationExecutionStatusDTO> generateStatusMap,
                                    ThreadVariableWrapper threadVariableWrapper) {
        if (threadVariableWrapper != null) {
            threadVariableWrapper.apply();
        }
        try {
            String statusKey = evaluationId + ":" + executionSessionId;
            try {
                evaluationResultGenerateService.generate(evaluationId, executionSessionId);
                generateStatusMap.put(statusKey, EvaluationExecutionStatusDTO.builder()
                        .evaluationId(evaluationId)
                        .executionSessionId(executionSessionId)
                        .status("COMPLETED")
                        .currentStep(1)
                        .totalSteps(1)
                        .build());
            } catch (Exception e) {
                log.error("评估结果生成异常, evaluationId={}, executionSessionId={}", evaluationId, executionSessionId, e);
                generateStatusMap.put(statusKey, EvaluationExecutionStatusDTO.builder()
                        .evaluationId(evaluationId)
                        .executionSessionId(executionSessionId)
                        .status("FAILED")
                        .currentStep(1)
                        .totalSteps(1)
                        .build());
            }
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 异步执行评估：逐条发送基准用户消息（含完整主/子会话链路）并生成评估结果。
     * 任务提交方通过 {@link ThreadVariableWrapper} 传播当前登录用户，此处先恢复用户上下文；
     * 入口创建评估执行上下文（执行标记），结束/异常 finally 中清理评估标记与上下文。
     *
     * @param evaluationId          评估 ID
     * @param executionSession      执行会话
     * @param userMessages          基准会话用户消息列表
     * @param statusMap             执行状态缓存
     * @param threadVariableWrapper 线程变量包装器（提交线程捕获的用户上下文快照，可为 null）
     */
    @Async
    public void executeAsync(Long evaluationId, Session executionSession,
                             List<MessageDataProvider.MessageDTO> userMessages,
                             Map<String, EvaluationExecutionStatusDTO> statusMap,
                             ThreadVariableWrapper threadVariableWrapper) {
        if (threadVariableWrapper != null) {
            threadVariableWrapper.apply();
        }
        EvaluationExecutionContext executionContext = EvaluationExecutionContext.create();
        EvaluationExecutionContext.set(executionContext);
        try {
            String statusKey = String.valueOf(evaluationId);
            Long executionSessionId = executionSession.getId();

            try {
                int total = userMessages.size();
                for (int i = 0; i < total; i++) {
                    MessageDataProvider.MessageDTO userMsg = userMessages.get(i);
                    String content = userMsg.content() != null ? userMsg.content() : "";

                    statusMap.put(statusKey, EvaluationExecutionStatusDTO.builder()
                            .evaluationId(evaluationId)
                            .executionSessionId(executionSessionId)
                            .status("RUNNING")
                            .currentStep(i + 1)
                            .totalSteps(total)
                            .build());

                    try {
                        // 主会话：headless 执行本条基准用户消息（内部含工具链与阻塞式子会话回调）
                        agentMessageProxy.sendUserMessageToSession(
                                String.valueOf(executionSessionId),
                                content,
                                null,
                                executionSession.getThinking());
                        // 逐条消费评估执行上下文待处理列表中的 SendUserMessage
                        // （WEBSOCKET 型子会话打开/子→父 sendParentMessage 回传续接/消息驱动型子会话）
                        drainPendingSendUserMessages(executionSessionId, executionSession.getThinking());
                        log.debug("评估执行消息处理完成, sessionId={}, step={}/{}", executionSessionId, i + 1, total);
                    } catch (Exception e) {
                        // 顶层基准用户消息自身处理失败：保持既有 FAILED 语义
                        log.error("评估执行用户消息处理失败, sessionId={}, step={}/{}", executionSessionId, i + 1, total, e);
                        statusMap.put(statusKey, EvaluationExecutionStatusDTO.builder()
                                .evaluationId(evaluationId)
                                .executionSessionId(executionSessionId)
                                .status("FAILED")
                                .currentStep(i + 1)
                                .totalSteps(total)
                                .build());
                        return;
                    }
                }

                // 主/子全链执行完成才置 COMPLETED，随后生成评估结果
                statusMap.put(statusKey, EvaluationExecutionStatusDTO.builder()
                        .evaluationId(evaluationId)
                        .executionSessionId(executionSessionId)
                        .status("COMPLETED")
                        .currentStep(total)
                        .totalSteps(total)
                        .build());
                evaluationResultGenerateService.generate(evaluationId, executionSessionId);

            } catch (Exception e) {
                log.error("评估执行异常, evaluationId={}", evaluationId, e);
                statusMap.put(statusKey, EvaluationExecutionStatusDTO.builder()
                        .evaluationId(evaluationId)
                        .executionSessionId(executionSessionId)
                        .status("FAILED")
                        .build());
            }
        } finally {
            EvaluationExecutionContext.clear();
            UserContext.clear();
        }
    }

    /**
     * 逐条消费评估执行上下文待处理列表中的 {@link SendUserMessage}，直至列表为空。
     *
     * <p>每条消息按其目标 sessionId（子会话或父会话）执行 headless 对话/工具链，驱动内容统一使用
     * {@link ChatService#SEND_USER_MESSAGE_MARKER}，与前端 WS 触发的 streamChildReply/continueMainChat
     * 约定一致：列表消息原文在发送 SEND_USER_MESSAGE 事件前已由
     * AgentContextManager.sendUserMessage/sendParentMessage 以 userInput=false 落库并进入会话上下文历史，
     * 以 marker 驱动 ChatService 走「不保存用户消息、不加入历史」分支，
     * 直接按会话既有上下文触发模型执行，避免重复持久化与污染模型输入（影响回滚/记忆/评估结果）。
     * 目标为执行主会话时沿用执行会话 thinking，其余子链路目标 thinking 由会话自身配置决定（传 null）；
     * 执行期间新产生的 SendUserMessage 继续追加进入列表，逐条 poll 至无待处理项。</p>
     *
     * <p>每条待处理消息驱动前，按其携带的事件原始 conversationId 检查目标会话是否已存在缓存条目：
     * 存在则先 {@link ChatDataCacheManager#removeCache} 移除（该缓存通常是同对话先前驱动/前端流程遗留、
     * 已以 STOP 结束或仍存活），避免驱动重入时 startCache 抛 DUPLICATE_KEY 或对已结束缓存追加；
     * 随后以 {@link AgentMessageProxy#sendUserMessage} 透传该 conversationId 驱动（替代每次新生成
     * conversationId 的 sendUserMessageToSession），使驱动请求归入与已落库消息一致的对话；
     * conversationId 为 null/空时透传 null，由 processChat 自动生成时间戳 conversationId 兜底。
     * 子链路执行失败仅记录日志、不中断整体评估。</p>
     *
     * @param executionSessionId 执行主会话 ID
     * @param executionThinking  执行会话 thinking（透传执行会话配置）
     */
    private void drainPendingSendUserMessages(Long executionSessionId, Boolean executionThinking) {
        EvaluationExecutionContext executionContext = EvaluationExecutionContext.get();
        if (executionContext == null) {
            return;
        }
        int driven = 0;
        while (driven < MAX_DRIVEN_MESSAGES_PER_STEP) {
            // pollNextPendingSendUserMessage 取出即移除：空列表、达上限退出与异常路径均不残留列表项
            SendUserMessage pending = executionContext.pollNextPendingSendUserMessage();
            if (pending == null) {
                return;
            }
            driven++;
            String targetSessionId = pending.getSessionId();
            try {
                Boolean thinking = String.valueOf(executionSessionId).equals(targetSessionId)
                        ? executionThinking : null;
                String conversationId = pending.getConversationId();
                removeExistingDrivenCache(targetSessionId, conversationId);
                agentMessageProxy.sendUserMessage(
                        targetSessionId, ChatService.SEND_USER_MESSAGE_MARKER, null, thinking, conversationId);
                log.debug("评估后台驱动待处理消息完成, targetSessionId={}, conversationId={}",
                        targetSessionId, conversationId);
            } catch (Exception e) {
                // 子会话（含嵌套任一层）驱动失败：回填日志并继续，父链路不中断
                log.error("评估后台驱动待处理消息失败, targetSessionId={}, 不中断整体评估", targetSessionId, e);
            }
        }
        log.warn("评估后台单条基准消息驱动子链路消息数达到上限 {}, 停止本次驱动", MAX_DRIVEN_MESSAGES_PER_STEP);
    }

    /**
     * 驱动前移除目标会话在指定 conversationId 下已存在的缓存条目。
     *
     * <p>仅当 conversationId 非空且 {@link ChatDataCacheManager#getCacheId} 命中时执行
     * {@link ChatDataCacheManager#removeCache}；无缓存条目时不做任何操作，避免驱动重入时
     * startCache 抛 DUPLICATE_KEY 或对已结束缓存追加（缓存键结构/收尾语义不变）。</p>
     *
     * @param targetSessionId 驱动目标会话 ID
     * @param conversationId  待驱动消息携带的事件原始 conversationId（可为 null/空）
     */
    private void removeExistingDrivenCache(String targetSessionId, String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }
        String cacheId = chatDataCacheManager.getCacheId(targetSessionId, conversationId);
        if (cacheId != null) {
            log.debug("评估后台驱动前移除既有缓存, sessionId={}, conversationId={}, cacheId={}",
                    targetSessionId, conversationId, cacheId);
            chatDataCacheManager.removeCache(cacheId);
        }
    }
}
