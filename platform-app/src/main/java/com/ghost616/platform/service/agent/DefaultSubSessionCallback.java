package com.ghost616.platform.service.agent;

import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.enums.SubSessionOpenMode;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.AgentMessageProxy;
import com.ghost616.agentbase.service.agent.invoker.SubSessionCallback;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.session.EvaluationExecutionContext;
import com.ghost616.platform.util.IdConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 子会话回调默认实现（@Component），实现 agent-base 的 {@link SubSessionCallback} 接口。
 *
 * <p>execute 根据子会话所属主会话的 subSessionOpenMode 分流：</p>
 * <ul>
 *   <li>WEBSOCKET 模式：先经 {@link SubSessionRunningCache} 检测子会话是否正在执行中——
 *       命中返回错误 JSON「子会话正在运行，请等候」阻止重复触发；未命中记入运行缓存并调用
 *       ctx.sendUserMessage 直接推送，返回提示消息，不阻塞、不写 subSessionDataMap；</li>
 *   <li>TOOL_CALL 模式（默认）/主会话解析失败/配置缺失：先建 SubSessionData 再阻塞等待；
 *       非评估执行保持阻塞等待前端 complete-sub-session 回填；评估后台执行（读取
 *       {@link EvaluationExecutionContext} 评估执行标记）时，阻塞前先异步启动后端驱动——
 *       复用 {@link AgentMessageProxy} Bean 对子会话 headless 执行完整对话/工具链
 *       （嵌套子会话同分支递归驱动），取得最终回复后等价 complete-sub-session 完成 future。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSubSessionCallback implements SubSessionCallback {

    /** 子会话运行中错误返回内容（JSON），父会话工具循环可正常解析，不抛异常 */
    private static final String RUNNING_ERROR_JSON = "{\"status\":\"error\",\"errMsg\":\"子会话正在运行，请等候\"}";

    /** 子会话后台驱动失败/空回复时回填的错误占位 JSON 前缀 */
    private static final String ERROR_PLACEHOLDER_PREFIX = "{\"status\":\"error\",\"errMsg\":\"";

    /** 子会话后台驱动失败/空回复时回填的错误占位 JSON 后缀 */
    private static final String ERROR_PLACEHOLDER_SUFFIX = "\"}";

    private final SessionMapper sessionMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final SubSessionRunningCache subSessionRunningCache;
    /**
     * 通过 ObjectProvider 按需获取 AgentMessageProxy Bean，避免构造器注入环：
     * DefaultSubSessionCallback → AgentMessageProxy(依赖 ChatService Bean) → AgentAssembler → DefaultToolDataProvider → DefaultSubSessionCallback。
     * ObjectProvider 注入本身不触发目标 Bean 实例化，仅在评估子会话驱动时按需获取同一单例。
     */
    private final ObjectProvider<AgentMessageProxy> agentMessageProxyProvider;
    private final ThreadVariableHandler threadVariableHandler;
    private final ExecutorService subSessionEvaluationExecutor;

    private final ConcurrentHashMap<Long, SubSessionData> subSessionDataMap = new ConcurrentHashMap<>();

    @Override
    public Message execute(AgentExecutionContext ctx, String sessionId, String userMessage, Boolean thinking) {
        Long sid = IdConverter.parse(sessionId);
        Session session = sessionMapper.selectById(sid);
        if (session == null || session.getParentSessionId() == null) {
            return null;
        }
        Long parentSessionId = session.getParentSessionId();

        if (ctx != null && isWebSocketMode(session)) {
            // a. 子会话运行缓存检测：正在执行中则返回错误 JSON，不重复发送、不写入缓存
            if (subSessionRunningCache.contains(sid)) {
                return Message.builder()
                        .role("assistant")
                        .content(RUNNING_ERROR_JSON)
                        .build();
            }
            // b. 记录子会话开始执行，再推送消息（评估执行下 ctx.sendUserMessage 事件会被
            //    DefaultMessageSender 拦截进评估执行上下文槽位，由消息驱动循环消费）
            subSessionRunningCache.add(sid);
            ctx.sendUserMessage(sessionId, userMessage, ctx.getModelId(), thinking);
            String sessionName = session.getTitle();
            if (sessionName == null || sessionName.isBlank()) {
                sessionName = sessionId;
            }
            return Message.builder()
                    .role("assistant")
                    .content("已发送消息到子会话" + sessionName + "，请等候子会话返回消息")
                    .build();
        }

        CompletableFuture<Message> messageResult = new CompletableFuture<>();
        SubSessionData data = new SubSessionData(sid, userMessage, thinking, messageResult);
        subSessionDataMap.put(parentSessionId, data);

        try {
            // 评估后台执行：阻塞前先异步启动后端驱动（仿前端 complete-sub-session 全链路），
            // 嵌套子会话驱动任务同样传播评估执行上下文，命中本分支递归驱动
            if (EvaluationExecutionContext.isEvaluation()) {
                startEvaluationDrive(data);
            }
            return messageResult.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SubSession execution interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("SubSession execution failed", e.getCause());
        } finally {
            subSessionDataMap.remove(parentSessionId);
        }
    }

    /**
     * 评估后台执行分支：将子会话完整对话/工具链驱动提交到专用线程池执行。
     *
     * <p>提交前通过 {@link ThreadVariableHandler#wrap()} 捕获当前线程的用户上下文与评估执行
     * 上下文快照，驱动线程内 apply() 恢复、finally 清理，保证嵌套子会话同样命中评估分支且
     * 上下文不串号/泄漏。线程池为无界缓存线程池（每层嵌套驱动独立线程，父线程阻塞等待期间
     * 仍能创建子层任务），避免固定容量线程池造成死锁。提交失败时直接回填错误占位，不阻塞父链路。</p>
     *
     * @param data 子会话数据（含 childSessionId/userMessage/thinking/messageResult）
     */
    private void startEvaluationDrive(SubSessionData data) {
        ThreadVariableWrapper wrapper = threadVariableHandler.wrap();
        try {
            subSessionEvaluationExecutor.execute(() -> {
                try {
                    wrapper.apply();
                    driveSubSession(data);
                } finally {
                    wrapper.clear();
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("评估后台子会话驱动任务提交被拒绝, childSessionId={}", data.getChildSessionId(), e);
            data.getMessageResult().complete(buildErrorPlaceholder("子会话驱动任务提交失败"));
        }
    }

    /**
     * 驱动子会话 headless 执行：复用 {@link AgentMessageProxy} Bean 对子会话发送用户消息并执行
     * 完整对话/工具链（子会话自身的嵌套子会话走同一评估分支递归驱动），取得最终回复后按
     * SessionController.complete-sub-session 等价语义 complete future；驱动失败或空回复时记录日志
     * 并向父会话回填错误占位（JSON），父链路继续执行，不中断整体评估。
     *
     * @param data 子会话数据
     */
    private void driveSubSession(SubSessionData data) {
        Long childSessionId = data.getChildSessionId();
        AgentMessageProxy proxy = agentMessageProxyProvider.getIfAvailable();
        if (proxy == null) {
            log.error("评估后台驱动子会话失败, AgentMessageProxy Bean 未装配, childSessionId={}", childSessionId);
            data.getMessageResult().complete(buildErrorPlaceholder("AgentMessageProxy 未装配"));
            return;
        }
        try {
            Message finalMessage = proxy.sendUserMessageToSession(
                    String.valueOf(childSessionId),
                    data.getUserMessage(),
                    null,
                    data.getThinking());
            if (finalMessage == null || finalMessage.getContent() == null || finalMessage.getContent().isBlank()) {
                log.warn("评估后台驱动子会话返回空回复, childSessionId={}", childSessionId);
                data.getMessageResult().complete(buildErrorPlaceholder("子会话未返回有效回复"));
                return;
            }
            data.getMessageResult().complete(finalMessage);
        } catch (Exception e) {
            log.error("评估后台驱动子会话失败, childSessionId={}, 回填错误占位后父链路继续", childSessionId, e);
            data.getMessageResult().complete(buildErrorPlaceholder(e.getMessage()));
        }
    }

    /**
     * 构建子会话驱动失败回填父会话的错误占位 Message（JSON 风格，参照现有错误返回样式）。
     *
     * @param errorMessage 错误信息（可为 null）
     * @return assistant 角色错误占位消息
     */
    private static Message buildErrorPlaceholder(String errorMessage) {
        String msg = errorMessage != null ? errorMessage : "";
        msg = msg.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
        return Message.builder()
                .role("assistant")
                .content(ERROR_PLACEHOLDER_PREFIX + msg + ERROR_PLACEHOLDER_SUFFIX)
                .build();
    }

    /**
     * 判断子会话是否存在：sessionMapper.selectById 非 null 返回 true。
     * Session 实体 {@code deleted} 字段带 @TableLogic，selectById 自动附加 deleted=0，
     * 已假删（deleted=1）的会话返回 null，因此软删会话被正确判定为不存在。
     */
    @Override
    public boolean exists(String childSessionId) {
        Long sid;
        try {
            sid = IdConverter.parse(childSessionId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (sid == null) {
            return false;
        }
        return sessionMapper.selectById(sid) != null;
    }

    public SubSessionData getSubSessionData(Long parentSessionId) {
        return subSessionDataMap.get(parentSessionId);
    }

    /**
     * 判断子会话所属主会话配置的子会话打开方式是否为 WEBSOCKET。
     * 主会话解析失败或智能体配置缺失时返回 false，由调用方按默认（TOOL_CALL）行为处理。
     */
    private boolean isWebSocketMode(Session childSession) {
        Long mainSessionId = resolveMainSessionId(childSession);
        if (mainSessionId == null) {
            return false;
        }
        Session mainSession = sessionMapper.selectById(mainSessionId);
        if (mainSession == null || mainSession.getAgentId() == null) {
            return false;
        }
        AgentConfig agentConfig = agentConfigMapper.selectById(mainSession.getAgentId());
        return agentConfig != null && SubSessionOpenMode.WEBSOCKET == agentConfig.getSubSessionOpenMode();
    }

    /**
     * 从子会话的 parentSessionId 出发，沿 parentSessionId 链解析主会话（无父会话的根）。
     *
     * @return 主会话 ID；解析失败（会话缺失或出现环）时返回 null
     */
    private Long resolveMainSessionId(Session childSession) {
        Long current = childSession.getParentSessionId();
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            Session session = sessionMapper.selectById(current);
            if (session == null) {
                return null;
            }
            if (session.getParentSessionId() == null) {
                return current;
            }
            current = session.getParentSessionId();
        }
        return null;
    }

    public static class SubSessionData {
        private final Long childSessionId;
        private final String userMessage;
        private final Boolean thinking;
        private final CompletableFuture<Message> messageResult;

        public SubSessionData(Long childSessionId, String userMessage, Boolean thinking, CompletableFuture<Message> messageResult) {
            this.childSessionId = childSessionId;
            this.userMessage = userMessage;
            this.thinking = thinking;
            this.messageResult = messageResult;
        }

        public Long getChildSessionId() {
            return childSessionId;
        }

        public String getUserMessage() {
            return userMessage;
        }

        public Boolean getThinking() {
            return thinking;
        }

        public CompletableFuture<Message> getMessageResult() {
            return messageResult;
        }
    }
}
