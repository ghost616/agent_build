package com.ghost616.agentbase.service.agent.invoker;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.CustomToolCall;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolCallDelta;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.model.WebSearchCall;
import com.ghost616.agentbase.enums.FinishReason;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentContextManager;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.ContextDataProvider;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.agentbase.service.agent.SessionManager;


@Slf4j
public class MessageSavePostHook implements SystemPostHook<ChatChunkHookData, EmptyHookResult> {

    private final AgentComponentRegistry registry;
    private ContextDataProvider contextDataProvider;
    private SessionManager sessionManager;
    private AgentContextManager agentContextManager;
    private ToolCallQueueManager toolCallQueueManager;
    private volatile boolean initialized;

    public MessageSavePostHook(AgentComponentRegistry registry) {
        this.registry = registry;
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    contextDataProvider = registry.getContextDataProvider();
                    sessionManager = registry.getSessionManager();
                    agentContextManager = registry.getAgentContextManager();
                    toolCallQueueManager = registry.getToolCallQueueManager();
                    initialized = true;
                }
            }
        }
    }

    private static class ToolAccumulator {
        final StringBuilder id = new StringBuilder();
        final StringBuilder name = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();
    }

    private static class SessionBuffer {
        final StringBuilder contentBuffer = new StringBuilder();
        final StringBuilder reasoningBuffer = new StringBuilder();
        final ConcurrentHashMap<String, ToolAccumulator> toolCallBuffers = new ConcurrentHashMap<>();
        final List<WebSearchCall> webSearchCalls = new ArrayList<>();
        final List<CustomToolCall> customToolCalls = new ArrayList<>();
    }

    private final ConcurrentHashMap<String, SessionBuffer> buffers = new ConcurrentHashMap<>();

    @Override
    public HookPhase getPhase() {
        return HookPhase.AFTER_MESSAGE_RECEIVE;
    }

    @Override
    public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
        ensureInitialized();
        ChatChunk chunk = data != null ? data.getChatChunk() : null;
        if (chunk == null) {
            return null;
        }
        String sessionId = ctx.getSessionId();

        if (FinishReason.STOP.equals(chunk.getFinishReason())) {
            SessionBuffer sb = buffers.remove(sessionId);
            if (sb == null) {
                return null;
            }
            String content = sb.contentBuffer.toString();
            String reasoning = sb.reasoningBuffer.length() > 0 ? sb.reasoningBuffer.toString() : null;
            List<MessageDataProvider.ToolCallData> toolCalls = null;
            if (!sb.toolCallBuffers.isEmpty()) {
                toolCalls = sb.toolCallBuffers.values().stream()
                        .map(a -> new MessageDataProvider.ToolCallData(
                                a.id.toString(),
                                a.name.toString(),
                                a.arguments.toString()))
                        .collect(Collectors.toList());
            }
            List<MessageDataProvider.WebSearchCallData> webSearchCallData = sb.webSearchCalls.isEmpty() ? null
                    : sb.webSearchCalls.stream().map(this::toWebSearchCallData).collect(Collectors.toList());
            List<MessageDataProvider.CustomToolCallData> customToolCallData = sb.customToolCalls.isEmpty() ? null
                    : sb.customToolCalls.stream().map(this::toCustomToolCallData).collect(Collectors.toList());
            UsageInfo usage = chunk.getUsage();
            log.debug("sessionId={} 保存消息, content={}, reasoning={}, toolCalls数量={}, webSearchCall数量={}, customToolCall数量={}",
                    sessionId, content, reasoning,
                    toolCalls != null ? toolCalls.size() : 0,
                    webSearchCallData != null ? webSearchCallData.size() : 0,
                    customToolCallData != null ? customToolCallData.size() : 0);
            sessionManager.messageSave().sessionId(sessionId).role("assistant").content(content).reasoning(reasoning)
                    .toolCalls(toolCalls).usage(usage).webSearchCall(webSearchCallData)
                    .customToolCall(customToolCallData).userInput(false)
                    .conversationId(ctx.getConversationId()).save();
            String lastResponseId = ctx.getLastResponseId();
            if (lastResponseId != null && !lastResponseId.isEmpty()) {
                contextDataProvider.updateLastResponseId(sessionId, lastResponseId);
            }
            if (toolCalls != null && !toolCalls.isEmpty()) {
                toolCallQueueManager.enqueue(sessionId, toolCalls);
            }
            List<ToolCall> historyToolCalls = null;
            if (!sb.toolCallBuffers.isEmpty()) {
                historyToolCalls = sb.toolCallBuffers.values().stream()
                        .map(a -> ToolCall.builder()
                                .id(a.id.toString())
                                .name(a.name.toString())
                                .arguments(a.arguments.toString())
                                .build())
                        .collect(Collectors.toList());
            }
            agentContextManager.addHistoryEntry(sessionId,
                    new AgentExecutionContext.HistoryEntry(
                            "assistant", content, reasoning, null,
                            LocalDateTime.now(),
                            historyToolCalls != null ? Collections.unmodifiableList(historyToolCalls) : Collections.emptyList(),
                            usage,
                            sb.webSearchCalls.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(sb.webSearchCalls),
                            sb.customToolCalls.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(sb.customToolCalls),
                            null, null));
            return EmptyHookResult.INSTANCE;
        }

        SessionBuffer sb = buffers.computeIfAbsent(sessionId, k -> new SessionBuffer());
        if (chunk.getDelta() != null) {
            sb.contentBuffer.append(chunk.getDelta());
        }
        if (chunk.getReasoning() != null) {
            sb.reasoningBuffer.append(chunk.getReasoning());
        }
        if (chunk.getWebSearchCall() != null) {
            sb.webSearchCalls.add(chunk.getWebSearchCall());
        }
        if (chunk.getCustomToolCall() != null) {
            sb.customToolCalls.add(chunk.getCustomToolCall());
        }
        List<ToolCallDelta> toolCalls = chunk.getToolCalls();
        if (toolCalls != null) {
            for (ToolCallDelta tc : toolCalls) {
                log.debug("sessionId={} ToolCallDelta id={} name={} arguments={}",
                        sessionId, tc.getId(), tc.getName(), tc.getArguments());
                String key;
                if (tc.getIndex() != null) {
                    key = String.valueOf(tc.getIndex());
                } else if (tc.getId() != null) {
                    key = tc.getId();
                } else {
                    continue;
                }
                ToolAccumulator acc = sb.toolCallBuffers.get(key);
                if (acc == null || acc.id.length() == 0) {
                    if (acc == null) {
                        acc = new ToolAccumulator();
                        sb.toolCallBuffers.put(key, acc);
                    }
                    acc.id.append(tc.getId());
                    if (tc.getName() != null) {
                        acc.name.append(tc.getName());
                    }
                    if (tc.getArguments() != null) {
                        acc.arguments.append(tc.getArguments());
                    }
                } else {
                    if (tc.getArguments() != null) {
                        acc.arguments.append(tc.getArguments());
                    }
                }
            }
        }
        return EmptyHookResult.INSTANCE;
    }

    private MessageDataProvider.WebSearchCallData toWebSearchCallData(WebSearchCall call) {
        if (call == null) {
            return null;
        }
        List<MessageDataProvider.WebSearchResultData> results = null;
        if (call.getResults() != null) {
            results = call.getResults().stream()
                    .map(r -> new MessageDataProvider.WebSearchResultData(r.getTitle(), r.getUrl(), r.getSnippet()))
                    .collect(Collectors.toList());
        }
        return new MessageDataProvider.WebSearchCallData(call.getItemId(), call.getOutputIndex(), results);
    }

    private MessageDataProvider.CustomToolCallData toCustomToolCallData(CustomToolCall call) {
        if (call == null) {
            return null;
        }
        return new MessageDataProvider.CustomToolCallData(
                call.getItemId(), call.getOutputIndex(), call.getInput(), call.getOutput());
    }
}
