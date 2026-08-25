package com.ghost616.agentbase.service.agent.invoker;


import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;


/**
 * 子会话回调接口，定义子会话消息执行的契约。
 *
 * @author ghost616
 */
@FunctionalInterface
public interface SubSessionCallback {

    /**
     * 执行子会话消息处理。
     *
     * @param ctx         子会话执行上下文，提供上下文能力（如 sendUserMessage 等），可为 null 表示不提供上下文
     * @param sessionId   会话 ID
     * @param userMessage 用户消息内容
     * @param thinking    是否启用思考模式，可为 null 表示使用默认行为
     * @return 执行结果消息
     */
    Message execute(AgentExecutionContext ctx, String sessionId, String userMessage, Boolean thinking);
}
