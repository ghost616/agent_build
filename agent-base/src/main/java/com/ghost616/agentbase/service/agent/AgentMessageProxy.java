package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.util.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AgentMessageProxy {

    private static final long TOOL_WAIT_TIMEOUT_MS = 60_000;
    private static final long TOOL_POLL_INTERVAL_MS = 200;
    private final ChatService chatService;
    private final ToolExecutionService toolExecutionService;
    private ChatDataCacheManager chatDataCacheManager;

    public AgentMessageProxy(ChatService chatService, ToolExecutionService toolExecutionService) {
        this.chatService = chatService;
        this.toolExecutionService = toolExecutionService;
    }

    public void setChatDataCacheManager(ChatDataCacheManager chatDataCacheManager) {
        this.chatDataCacheManager = chatDataCacheManager;
    }

    public Message sendUserMessage(String childSessionId, String content, String modelId, Boolean thinking) {
        return sendUserMessage(childSessionId, content, modelId, thinking, null);
    }

    /**
     * 向子会话发送用户消息（支持图片对象数组）。
     *
     * @param childSessionId 子会话 ID
     * @param content        用户消息内容
     * @param modelId        模型 ID（可为 null）
     * @param thinking       是否启用思考模式（可为 null）
     * @param images         图片列表（可为 null/空，imgId 仅供前端关联，不传给模型）
     * @return 最终 assistant 回复消息
     */
    public Message sendUserMessage(String childSessionId, String content, String modelId, Boolean thinking,
                                   List<ImageContent> images) {
        ChatRequest request = ChatRequest.builder()
                .sessionId(childSessionId)
                .content(content)
                .modelId(modelId)
                .thinking(thinking)
                .images(images)
                .build();
        return processChat(request);
    }

    /**
     * 向会话发送用户消息，自动生成 24 位 conversationId 标识对话归属。
     *
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @param modelId   模型 ID（可为 null）
     * @param thinking  是否启用思考模式（可为 null 表示默认行为）
     * @return 最终 assistant 回复消息
     */
    public Message sendUserMessageToSession(String sessionId, String content, String modelId, Boolean thinking) {
        return sendUserMessageToSession(sessionId, content, modelId, thinking, null);
    }

    /**
     * 向会话发送用户消息（支持图片对象数组），自动生成 24 位 conversationId 标识对话归属。
     *
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @param modelId   模型 ID（可为 null）
     * @param thinking  是否启用思考模式（可为 null 表示默认行为）
     * @param images    图片列表（可为 null/空，imgId 仅供前端关联，不传给模型）
     * @return 最终 assistant 回复消息
     */
    public Message sendUserMessageToSession(String sessionId, String content, String modelId, Boolean thinking,
                                            List<ImageContent> images) {
        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .content(content)
                .modelId(modelId)
                .thinking(thinking)
                .images(images)
                .conversationId(generateConversationId())
                .build();
        return processChat(request);
    }

    private static String generateConversationId() {
        return String.valueOf(System.currentTimeMillis());
    }

    private Message processChat(ChatRequest request) {
        checkReactorThread();
        if (request.getConversationId() == null || request.getConversationId().isEmpty()) {
            request.setConversationId(generateConversationId());
        }
        Flux<ServerSentEvent<ChatChunk>> flux = chatService.chat(request);

        List<ServerSentEvent<ChatChunk>> events;
        String cacheId = null;
        if (chatDataCacheManager != null) {
            cacheId = chatDataCacheManager.startCache(request.getSessionId(), request.getConversationId());
            final String lambdaCacheId = cacheId;
            events = flux.doOnNext(event -> {
                ChatChunk chunk = event.data();
                if (chunk != null && chunk.getFinishReason() == null) {
                    chatDataCacheManager.appendChunk(lambdaCacheId, chunk);
                }
            }).collectList().block();
        } else {
            events = flux.collectList().block();
        }

        CollectedResult result = collectContent(events);

        Message message;
        if (result.hasToolCalls()) {
            Map<String, Integer> toolCallCounts = new HashMap<>();
            message = processToolCalls(request.getSessionId(), toolCallCounts, cacheId);
        } else {
            message = Message.builder()
                    .role("assistant")
                    .content(result.content())
                    .build();
        }

        if (chatDataCacheManager != null) {
            chatDataCacheManager.appendChunk(cacheId, ChatChunk.builder().finishReason(FinishReason.STOP).build());
        }
        return message;
    }

    private Message processToolCalls(String sessionId, Map<String, Integer> toolCallCounts, String cacheId) {
        while (true) {
            ToolExecutionService.ToolExecutionResult execResult = toolExecutionService.executeTool(sessionId);
            String status = execResult.status();
            if ("empty".equals(status)) {
                break;
            }
            if ("executing".equals(status)) {
                waitForToolCompletion(sessionId, execResult.toolId(), cacheId);
            } else {
                log.warn("sessionId={} 工具执行返回非预期状态: {} toolId={}", sessionId, status, execResult.toolId());
            }

            if (chatDataCacheManager != null && cacheId != null) {
                chatDataCacheManager.appendChunk(cacheId, buildToolResultChunk(sessionId, execResult));
            }

            String toolKey = execResult.toolName() + ":" + execResult.arguments();
            int count = toolCallCounts.merge(toolKey, 1, Integer::sum);
            if (count >= 5) {
                log.warn("sessionId={} 工具 {} 同一参数组合调用次数达到 {}，超过阈值 5，终止", sessionId, toolKey, count);
                return Message.builder()
                        .role("assistant")
                        .content("")
                        .build();
            }

            if (!execResult.hasMore()) {
                break;
            }
        }

        checkReactorThread();
        Flux<ServerSentEvent<ChatChunk>> contFlux = toolExecutionService.continueAfterTools(sessionId);
        List<ServerSentEvent<ChatChunk>> contEvents = contFlux.collectList().block();

        if (chatDataCacheManager != null && cacheId != null) {
            cacheEvents(contEvents, cacheId);
        }

        CollectedResult contResult = collectContent(contEvents);

        if (contResult.hasToolCalls()) {
            return processToolCalls(sessionId, toolCallCounts, cacheId);
        }

        return Message.builder()
                .role("assistant")
                .content(contResult.content())
                .build();
    }

    private void waitForToolCompletion(String sessionId, String toolId, String cacheId) {
        long deadline = System.currentTimeMillis() + TOOL_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            ToolExecutionService.ToolStatusResult status = toolExecutionService.getToolStatus(sessionId, toolId);
            String s = status.status();
            if ("idle".equals(s) || "done".equals(s) || "failed".equals(s)) {
                return;
            }
            if (chatDataCacheManager != null && cacheId != null) {
                chatDataCacheManager.appendChunk(cacheId, ChatChunk.builder().delta("").build());
            }
            try {
                Thread.sleep(TOOL_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("sessionId={} toolId={} 等待超时", sessionId, toolId);
    }

    private static void checkReactorThread() {
        if (Schedulers.isInNonBlockingThread()) {
            throw new IllegalStateException("AgentMessageProxy.block() 不能在 Reactor 非阻塞线程中调用");
        }
    }

    /**
     * 将事件列表中的聊天块追加到缓存，跳过 finishReason 非 null 的结束块。
     *
     * @param events  流式事件列表（可能为 null）
     * @param cacheId 缓存 ID
     */
    private void cacheEvents(List<ServerSentEvent<ChatChunk>> events, String cacheId) {
        if (events == null) {
            return;
        }
        for (ServerSentEvent<ChatChunk> event : events) {
            ChatChunk chunk = event.data();
            if (chunk != null && chunk.getFinishReason() == null) {
                chatDataCacheManager.appendChunk(cacheId, chunk);
            }
        }
    }

    /**
     * 构建工具执行结果缓存块，delta 为 JSON 格式（含 toolName、toolId、arguments、result），与 messageSave 的 toolInfo 和 toolResult 格式一致。
     */
    private ChatChunk buildToolResultChunk(String sessionId, ToolExecutionService.ToolExecutionResult execResult) {
        String result = execResult.message();
        if (execResult.toolId() != null) {
            try {
                ToolExecutionService.ToolStatusResult status =
                        toolExecutionService.getToolStatus(sessionId, execResult.toolId());
                if (status != null && status.result() != null && !status.result().isEmpty()) {
                    result = status.result();
                }
            } catch (Exception e) {
                log.warn("sessionId={} 获取工具执行结果失败 toolId={}", sessionId, execResult.toolId(), e);
            }
        }
        Map<String, String> toolResultMap = new HashMap<>();
        toolResultMap.put("toolName", execResult.toolName());
        toolResultMap.put("toolId", execResult.toolId());
        toolResultMap.put("arguments", execResult.arguments());
        toolResultMap.put("result", result);
        try {
            return ChatChunk.builder().delta(JsonMapper.MAPPER.writeValueAsString(toolResultMap)).build();
        } catch (Exception e) {
            log.warn("sessionId={} 序列化工具执行结果失败 toolId={}", sessionId, execResult.toolId(), e);
            return ChatChunk.builder().delta(execResult.toolName() + (result != null ? ": " + result : "")).build();
        }
    }

    private static CollectedResult collectContent(List<ServerSentEvent<ChatChunk>> events) {
        if (events == null || events.isEmpty()) {
            return new CollectedResult("", false);
        }
        StringBuilder content = new StringBuilder();
        boolean hasToolCalls = false;
        for (ServerSentEvent<ChatChunk> event : events) {
            ChatChunk chunk = event.data();
            if (chunk == null) continue;
            if (chunk.getDelta() != null) {
                content.append(chunk.getDelta());
            }
            if (chunk.getHasToolCalls() != null && chunk.getHasToolCalls()) {
                hasToolCalls = true;
            }
        }
        return new CollectedResult(content.toString(), hasToolCalls);
    }

    private record CollectedResult(String content, boolean hasToolCalls) {
    }
}
