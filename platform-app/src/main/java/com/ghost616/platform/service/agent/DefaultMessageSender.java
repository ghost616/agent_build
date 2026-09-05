package com.ghost616.platform.service.agent;

import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.sendmessage.MessageDefinition;
import com.ghost616.agentbase.sendmessage.MessageName;
import com.ghost616.agentbase.sendmessage.MessageSender;
import com.ghost616.agentbase.sendmessage.SendUserMessage;
import com.ghost616.platform.session.EvaluationExecutionContext;
import com.ghost616.platform.websocket.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * agent-base {@link MessageSender} 默认实现（Spring Bean）。
 *
 * <p>收到消息后按消息类型（{@link MessageDefinition#getMessageName()}）分发到不同业务流程。
 * 当前实现 SEND_USER_MESSAGE 流程：
 * 普通流程在独立线程中经 {@link WebSocketPushService} 通过 WebSocket 推送到前端
 * （提交异步任务前通过 {@link ThreadVariableHandler#wrap()} 捕获当前线程用户/评估执行上下文快照，
 * 异步线程 {@link ThreadVariableWrapper#apply()} 恢复、finally 清理，防止线程复用导致串号）。
 * 评估拦截仅针对 {@link SendUserMessage}（headless 无 WS 前端）：评估后台执行时不推送，
 * 将 {@link SendUserMessage} 追加到当前线程 {@link EvaluationExecutionContext} 待处理列表
 * （追加语义，同一驱动窗口内多条并发/连续产生的消息均保留不丢），
 * 供 {@code AsyncEvaluationExecutor} 消息驱动循环按产生顺序逐条消费；其余所有情况（非评估、或评估中的
 * 非 SendUserMessage 消息）一律走下方统一异步分发通道，与普通路径行为一致——未来
 * MessageSender/dispatch 新增消息类型时普通与评估两条路径自动保持一致，无需再同步评估分支。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMessageSender implements MessageSender {

    private final WebSocketPushService pushService;
    private final ThreadVariableHandler threadVariableHandler;

    @Override
    public void send(MessageDefinition message) {
        if (message == null) {
            return;
        }
        // 评估拦截仅针对 SendUserMessage：评估后台执行 headless 无 WS 前端，其语义为「用户消息已
        // 落库、需后端驱动对应会话继续执行」，故不推送、追加写入评估执行上下文待处理列表供
        // AsyncEvaluationExecutor 消息驱动循环消费。标记判断与写入必须在发送线程同步完成（send
        // 可能由工具异步线程等跨线程点触发），避免异步分发线程读不到评估执行标记导致事件丢失。
        // 其余所有情况（非评估、或评估中的非 SendUserMessage 消息）一律走下方统一异步分发通道，
        // 与普通路径行为一致（dispatch 对未支持类型仅 log.debug），未来新增消息类型无需同步评估分支。
        EvaluationExecutionContext evalContext = EvaluationExecutionContext.get();
        if (evalContext != null && message instanceof SendUserMessage sendUserMessage) {
            evalContext.addPendingSendUserMessage(sendUserMessage);
            return;
        }
        ThreadVariableWrapper wrapper = threadVariableHandler.wrap();
        CompletableFuture.runAsync(() -> {
            try {
                wrapper.apply();
                dispatch(message);
            } finally {
                wrapper.clear();
            }
        });
    }

    /**
     * 按消息类型分发到对应业务流程。
     */
    private void dispatch(MessageDefinition message) {
        try {
            switch (message.getMessageName()) {
                case MessageName.SEND_USER_MESSAGE -> handleSendUserMessage((SendUserMessage) message);
                default -> log.debug("MessageSender 暂不支持的消息类型: {}", message.getMessageName());
            }
        } catch (Exception e) {
            log.warn("MessageSender 分发消息失败, type={}, error={}", message.getMessageName(), e.getMessage(), e);
        }
    }

    /**
     * SEND_USER_MESSAGE 流程：将用户消息推送至前端。
     *
     * <p>推送目标由 {@link WebSocketPushService} 按当前用户会话 ID 定位
     * （用户会话级绑定），推送服务内部对无上下文/无连接场景静默丢弃。</p>
     */
    private void handleSendUserMessage(SendUserMessage message) {
        pushService.pushToSession(message);
    }
}
