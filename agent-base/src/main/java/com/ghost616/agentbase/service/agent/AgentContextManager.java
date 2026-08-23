package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.model.CustomToolCall;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.model.WebSearchCall;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.McpExpandedToolDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.enums.ToolType;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.sendmessage.ChildCreateSession;
import com.ghost616.agentbase.sendmessage.ConversationIdMessage;
import com.ghost616.agentbase.sendmessage.HistoryMessage;
import com.ghost616.agentbase.sendmessage.MessageSender;
import com.ghost616.agentbase.sendmessage.SendUserMessage;
import com.ghost616.agentbase.sendmessage.VariableMessage;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.CacheRemoveLogData;
import com.ghost616.agentbase.service.agent.log.ChildSessionLogData;
import com.ghost616.agentbase.service.agent.log.ContextBuildLogData;
import com.ghost616.agentbase.service.agent.log.HandleMessageLogData;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.RefreshLogData;
import com.ghost616.agentbase.service.agent.log.SendMessageLogData;
import com.ghost616.agentbase.service.agent.log.SendParentMessageLogData;
import com.ghost616.agentbase.service.agent.log.SessionErrorLogData;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AgentContextManager {

    private final AgentComponentRegistry registry;
    private ContextDataProvider dataProvider;
    private SessionManager sessionManager;
    private ToolManager toolManager;
    private AgentMessageProxy agentMessageProxy;

    private final ConcurrentHashMap<String, AgentSessionContext> cache = new ConcurrentHashMap<>();
    private volatile boolean initialized;

    public AgentContextManager(AgentComponentRegistry registry) {
        this.registry = registry;
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    dataProvider = registry.getContextDataProvider();
                    sessionManager = registry.getSessionManager();
                    toolManager = registry.getToolManager();
                    initialized = true;
                }
            }
        }
    }

    public void setAgentMessageProxy(AgentMessageProxy agentMessageProxy) {
        this.agentMessageProxy = agentMessageProxy;
    }

    private void addLog(LogData logData) {
        AgentLog agentLog = registry.getAgentLog();
        if (agentLog != null) {
            try {
                agentLog.addLog(logData);
            } catch (Exception e) {
                log.warn("记录智能体日志失败: {}", e.getMessage(), e);
            }
        }
    }

    public Builder build(String sessionId) {
        ensureInitialized();
        return new Builder(sessionId);
    }

    public class Builder {
        private final String sessionId;
        private String modelIdOverride;

        private Builder(String sessionId) {
            this.sessionId = sessionId;
        }

        public Builder modelIdOverride(String modelId) {
            this.modelIdOverride = modelId;
            return this;
        }

        public AgentSessionContext build() {
            AgentSessionContext cached = cache.get(sessionId);
            if (cached != null) {
                // 请求未显式指定模型（modelIdOverride 为 null，如工具续接 TOOL_CONTINUE_MARKER、子会话执行请求）：
                // 直接复用缓存条目，沿用当前会话上下文（含 conversationId、modelId），不参与一致性比较、不触发重建
                if (modelIdOverride == null) {
                    return cached;
                }
                // 请求显式指定模型且与缓存条目一致：直接复用
                if (Objects.equals(cached.modelIdOverride, modelIdOverride)) {
                    return cached;
                }
                // 缓存条目存在但请求显式指定的 modelId 与条目不一致（如 get() 轻量构建写入的无 override 条目）：
                // 移除旧条目后重新构建，保证请求指定的 modelId 生效，避免回退为会话默认模型
                cache.remove(sessionId, cached);
            }
            AgentSessionContext created = doBuild();
            AgentSessionContext raced = cache.putIfAbsent(sessionId, created);
            if (raced != null && !Objects.equals(raced.modelIdOverride, modelIdOverride)) {
                // 并发下其他线程写入了不同 override 的条目：替换为本次 override 的上下文
                cache.remove(sessionId, raced);
                cache.put(sessionId, created);
                return created;
            }
            return raced != null ? raced : created;
        }

        /**
         * 轻量构建：仅加载 AgentContextData 并做防御性拷贝，
         * 生成 context 为 null 的 AgentSessionContext，AgentExecutionContext 由懒构建产生。
         */
        private AgentSessionContext doBuild() {
            ContextDataProvider.AgentContextData ctxData = dataProvider.loadAgentContext(sessionId);
            if (ctxData == null) {
                addLog(SessionErrorLogData.builder()
                        .logLevel(LogLevel.ERROR)
                        .sessionId(sessionId)
                        .errorCode(AgentErrorCode.SESSION_NOT_FOUND.getCode())
                        .message("会话上下文构建失败: 会话未找到: " + sessionId)
                        .build());
                throw new AgentException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            return new AgentSessionContext(AgentContextManager.this, sessionId,
                    defensiveCopy(ctxData), modelIdOverride);
        }
    }

    /**
     * 防御性拷贝：将 dataProvider.loadAgentContext 返回的 AgentContextData 中的
     * sessionVariables/skills/childSessions 三个集合分别拷贝为可变副本（new HashMap/new ArrayList），
     * 隔离数据源返回的不可变集合（List.of/Stream.toList/Map.of），
     * 供轻量构建存入 AgentSessionContext 并供懒构建共享引用。
     */
    private ContextDataProvider.AgentContextData defensiveCopy(ContextDataProvider.AgentContextData source) {
        Map<String, String> sessionVariables = source.sessionVariables() == null
                ? new HashMap<>() : new HashMap<>(source.sessionVariables());
        List<SkillConfigDTO> skills = source.skills() == null
                ? new ArrayList<>() : new ArrayList<>(source.skills());
        List<AgentExecutionContext.ChildSession> childSessions = source.childSessions() == null
                ? new ArrayList<>() : new ArrayList<>(source.childSessions());
        return new ContextDataProvider.AgentContextData(
                source.agentId(), source.systemPrompt(), source.defaultModelId(),
                source.recentMessageCount(), skills, sessionVariables,
                source.parentSessionId(), childSessions,
                source.lastResponseId(), source.conversationId());
    }

    /**
     * 懒构建 AgentExecutionContext：基于 AgentSessionContext 持有的 AgentContextData，
     * 加载 messages/history、会话工具、skills MCP 展开、父会话解析与 mutator 回调注入。
     * sessionVariables/skills/childSessions 直接共享 AgentContextData 中的引用（不再重复拷贝），
     * 保持 mutator 原地更新机制（refreshSessionVariables/refreshChildSessions 等 clear+putAll/addAll）
     * 使 AgentContextData 与 context 同步。仅由 AgentSessionContext.context() 首次访问且为 null 时调用。
     */
    private AgentExecutionContext buildExecutionContext(AgentSessionContext sessionCtx) {
        String sessionId = sessionCtx.sessionId;
        ContextDataProvider.AgentContextData ctxData = sessionCtx.agentContextData;

        String agentId = ctxData.agentId();
        String systemPrompt = ctxData.systemPrompt();
        if (systemPrompt == null) {
            systemPrompt = "";
        }

        String effectiveModelId = (sessionCtx.modelIdOverride != null)
                ? sessionCtx.modelIdOverride : ctxData.defaultModelId();

        boolean isSubSession = ctxData.parentSessionId() != null;
        List<ToolConfigDTO> tools = toolManager.getSessionTools(sessionId, isSubSession).stream()
                .map(ToolManager.ToolSessionObject::toolConfig)
                .toList();

        List<SkillConfigDTO> skills = ctxData.skills();

        for (SkillConfigDTO skill : skills) {
            if (skill.getSkillTools() != null) {
                List<ToolConfigDTO> expandedTools = new ArrayList<>();
                for (ToolConfigDTO tool : skill.getSkillTools()) {
                    if (tool.getToolType() == ToolType.MCP_HTTP && !(tool instanceof McpExpandedToolDTO)) {
                        if (!isSubSession && tool.getSessionAuth() == SessionAuthType.CHILD) {
                            tool.setSessionAuth(SessionAuthType.PARENT);
                            expandedTools.add(tool);
                        } else {
                            List<? extends ToolConfigDTO> mcpTools = toolManager.expandMcpTools(tool);
                            for (ToolConfigDTO mcpTool : mcpTools) {
                                mcpTool.setSessionAuth(SessionAuthType.PARENT);
                            }
                            expandedTools.addAll(mcpTools);
                        }
                    } else {
                        tool.setSessionAuth(SessionAuthType.PARENT);
                        expandedTools.add(tool);
                    }
                }
                skill.setSkillTools(expandedTools);
            }
        }

        List<MessageDataProvider.MessageDTO> messages = sessionManager.getMessages(sessionId);
        List<AgentExecutionContext.HistoryEntry> history = convertMessagesToHistory(messages);

        AgentExecutionContext.AgentContextMutator mutator = new AgentExecutionContext.AgentContextMutator();

        String parentSessionId = ctxData.parentSessionId();
        AgentSessionContext parentCtx = null;
        if (parentSessionId != null) {
            parentCtx = cache.get(parentSessionId);
            if (parentCtx == null) {
                parentCtx = build(parentSessionId).build();
            }
        }

        String conversationId = null;
        if (parentSessionId != null && parentCtx != null) {
            conversationId = parentCtx.context().getConversationId();
        }

        AgentExecutionContext context = new AgentExecutionContext(
                sessionId, agentId, systemPrompt, effectiveModelId,
                ctxData.recentMessageCount(),
                history, tools, skills, mutator,
                ctxData.sessionVariables(), new HashMap<>(),
                parentSessionId, System.getProperty("user.dir"), ctxData.childSessions(),
                conversationId);

        injectVariableCallbacks(mutator, sessionId, parentSessionId, parentCtx);
        mutator.setMessageSender(registry.getMessageSender());
        if (ctxData.lastResponseId() != null && !ctxData.lastResponseId().isEmpty()) {
            mutator.setLastResponseId(ctxData.lastResponseId());
        }

        addLog(ContextBuildLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(sessionId)
                .agentId(agentId)
                .modelId(effectiveModelId)
                .toolCount(tools.size())
                .historyCount(history.size())
                .isSubSession(isSubSession)
                .cacheHit(false)
                .sessionVariables(new HashMap<>(ctxData.sessionVariables()))
                .build());

        sessionCtx.mutator = mutator;
        return context;
    }

    private void injectVariableCallbacks(AgentExecutionContext.AgentContextMutator mutator,
                                          String sessionId, String parentSessionId,
                                          AgentSessionContext parentCtx) {
        if (parentSessionId != null && parentCtx != null) {
            AgentExecutionContext parentContext = parentCtx.context();
            mutator.sessionVarPutCallback = parentContext::putSessionVariable;
            mutator.sessionVarRemoveCallback = parentContext::removeSessionVariable;
            mutator.conversationVarPutCallback = parentContext::putConversationVariable;
            mutator.conversationVarRemoveCallback = parentContext::removeConversationVariable;
            mutator.getSessionVarCallback = parentContext::getSessionVariable;
            mutator.getConversationVarCallback = parentContext::getConversationVariable;
            mutator.getSessionVarKeysCallback = parentContext::getSessionVariableKeys;
            mutator.getConversationVarKeysCallback = parentContext::getConversationVariableKeys;
            mutator.conversationIdSupplier = parentContext::getConversationId;
        } else {
            mutator.sessionVarPutCallback = (key, value) ->
                    dataProvider.saveSessionVariable(sessionId, key, value);
            mutator.sessionVarRemoveCallback = (key) ->
                    dataProvider.deleteSessionVariable(sessionId, key);
            mutator.conversationVarPutCallback = (key, value) ->
                    dataProvider.saveSessionVariable(sessionId, key, value);
            mutator.conversationVarRemoveCallback = (key) ->
                    dataProvider.deleteSessionVariable(sessionId, key);
        }
        mutator.createChildSessionCallback = (psId, sessionName, description, modelId,
                                                toolIds, skillIds, prompt) ->
                createChildSession(psId, sessionName, description, modelId,
                        toolIds, skillIds, prompt, mutator.getConversationId());
        mutator.sendUserMessageCallback = (childSessionId, content, modelId, thinking, images) ->
                sendUserMessage(sessionId, childSessionId, content, modelId, thinking,
                        mutator.getConversationId(), images);
        mutator.sendParentMessageCallback = (parentId, content, conversationId) ->
                sendParentMessage(sessionId, parentId, content, conversationId);
    }

    private String createChildSession(String parentSessionId, String sessionName, String description, String modelId,
                                       List<String> toolIds, List<String> skillIds, String prompt, String conversationId) {
        String childSessionId = dataProvider.createChildSession(parentSessionId, sessionName, description, modelId, toolIds, skillIds, prompt);
        addLog(ChildSessionLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(parentSessionId)
                .conversationId(conversationId)
                .childSessionId(childSessionId)
                .sessionName(sessionName)
                .description(description)
                .modelId(modelId)
                .toolIds(toolIds)
                .skillIds(skillIds)
                .prompt(prompt)
                .build());
        return childSessionId;
    }

    private void sendUserMessage(String parentSessionId, String childSessionId, String content, String modelId,
                                 Boolean thinking, String conversationId, List<ImageContent> images) {
        sessionManager.messageSave().sessionId(childSessionId).role("user").content(content)
                .images(images)
                .conversationId(conversationId).save();

        addHistoryEntry(childSessionId, new AgentExecutionContext.HistoryEntry(
                "user", content, null, null,
                LocalDateTime.now(),
                Collections.emptyList(),
                null, null, null, images));

        addLog(SendMessageLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(parentSessionId)
                .conversationId(conversationId)
                .childSessionId(childSessionId)
                .content(content)
                .modelId(modelId)
                .thinking(thinking)
                .build());

        sendSendUserMessage(childSessionId, content, conversationId);
    }

    /**
     * 子会话向父会话发送用户消息（与 sendUserMessage 对称）：
     * 在父会话下持久化 user 消息、更新父会话缓存历史、推送 SendUserMessage 事件并记录日志。
     *
     * @param sessionId      当前会话 ID（调用方子会话）
     * @param parentSessionId 父会话 ID（消息保存与发送目标）
     * @param content        要发送的消息内容
     * @param conversationId 对话 ID
     */
    private void sendParentMessage(String sessionId, String parentSessionId, String content, String conversationId) {
        sessionManager.messageSave().sessionId(parentSessionId).role("user").content(content)
                .conversationId(conversationId).save();

        addHistoryEntry(parentSessionId, new AgentExecutionContext.HistoryEntry(
                "user", content, null, null,
                LocalDateTime.now(),
                Collections.emptyList(),
                null, null, null, null));

        addLog(SendParentMessageLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(sessionId)
                .parentSessionId(parentSessionId)
                .conversationId(conversationId)
                .content(content)
                .build());

        sendSendUserMessage(parentSessionId, content, conversationId);
    }

    private void sendSendUserMessage(String childSessionId, String content, String conversationId) {
        MessageSender messageSender = registry.getMessageSender();
        if (messageSender == null) {
            return;
        }
        List<String> parentSessionIds = resolveParentSessionIds(childSessionId);
        SendUserMessage message = new SendUserMessage(childSessionId, content, conversationId, parentSessionIds);
        try {
            messageSender.send(message);
        } catch (Exception e) {
            log.warn("发送 SendUserMessage 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建父会话链：沿 parentSessionId 逐级向上解析，
     * 收集直接父会话 → ... → 主会话（无父会话的根）的完整有序列表，
     * 第一个元素为直接父会话 ID，最后一个元素为主会话 ID。
     */
    private List<String> resolveParentSessionIds(String sessionId) {
        List<String> parentIds = new ArrayList<>();
        String current = sessionId;
        while (current != null) {
            ContextDataProvider.AgentContextData data = dataProvider.loadAgentContext(current);
            if (data == null || data.parentSessionId() == null) {
                break;
            }
            current = data.parentSessionId();
            parentIds.add(current);
        }
        return parentIds;
    }

    /**
     * 获取会话上下文：缓存未命中时执行轻量构建（加载 AgentContextData + 防御性拷贝后放入缓存，
     * 不构建 AgentExecutionContext），替代原来直接返回 null；会话不存在时返回 null。
     */
    public AgentSessionContext get(String sessionId) {
        ensureInitialized();
        AgentSessionContext cached = cache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        ContextDataProvider.AgentContextData ctxData = dataProvider.loadAgentContext(sessionId);
        if (ctxData == null) {
            return null;
        }
        AgentSessionContext created = new AgentSessionContext(this, sessionId, defensiveCopy(ctxData), null);
        AgentSessionContext raced = cache.putIfAbsent(sessionId, created);
        return raced != null ? raced : created;
    }

    public void addHistoryEntry(String sessionId, AgentExecutionContext.HistoryEntry entry) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(sessionId);
        if (ctx != null) {
            ctx.mutator().addHistoryEntry(entry);
        }
    }

    public void remove(String sessionId) {
        cache.remove(sessionId);
        addLog(CacheRemoveLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(sessionId)
                .build());
    }

    private List<AgentExecutionContext.HistoryEntry> convertMessagesToHistory(List<MessageDataProvider.MessageDTO> messages) {
        List<AgentExecutionContext.HistoryEntry> history = new ArrayList<>();
        for (MessageDataProvider.MessageDTO msg : messages) {
            List<ToolCall> toolCalls;
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                toolCalls = msg.toolCalls().stream()
                        .map(tc -> ToolCall.builder()
                                .id(tc.toolCallId())
                                .name(tc.toolCallName())
                                .arguments(tc.toolCallArguments())
                                .build())
                        .toList();
            } else {
                toolCalls = Collections.emptyList();
            }
            history.add(new AgentExecutionContext.HistoryEntry(
                    msg.role(), msg.content(), msg.reasoning(), msg.toolInfo(),
                    msg.createTime(), Collections.unmodifiableList(toolCalls),
                    msg.usage(), toWebSearchCall(msg.webSearchCall()), toCustomToolCall(msg.customToolCall()),
                    msg.images()));
        }
        return history;
    }

    private List<WebSearchCall> toWebSearchCall(List<MessageDataProvider.WebSearchCallData> dataList) {
        if (dataList == null) {
            return null;
        }
        return dataList.stream()
                .map(data -> {
                    if (data == null) {
                        return null;
                    }
                    List<WebSearchCall.WebSearchResult> results = null;
                    if (data.results() != null) {
                        results = data.results().stream()
                                .map(r -> WebSearchCall.WebSearchResult.builder()
                                        .title(r.title())
                                        .url(r.url())
                                        .snippet(r.snippet())
                                        .build())
                                .toList();
                    }
                    return WebSearchCall.builder()
                            .itemId(data.itemId())
                            .outputIndex(data.outputIndex())
                            .results(results)
                            .build();
                })
                .toList();
    }

    private List<CustomToolCall> toCustomToolCall(List<MessageDataProvider.CustomToolCallData> dataList) {
        if (dataList == null) {
            return null;
        }
        return dataList.stream()
                .map(data -> {
                    if (data == null) {
                        return null;
                    }
                    return CustomToolCall.builder()
                            .itemId(data.itemId())
                            .outputIndex(data.outputIndex())
                            .input(data.input())
                            .output(data.output())
                            .build();
                })
                .toList();
    }

    public void refreshHistory(String sessionId) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(sessionId);
        if (ctx == null) {
            return;
        }
        List<MessageDataProvider.MessageDTO> messages = dataProvider.getLatestMessages(sessionId);
        List<AgentExecutionContext.HistoryEntry> history = convertMessagesToHistory(messages);
        ctx.mutator().refreshHistory(history);
        addLog(RefreshLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(ctx.context())
                .refreshTarget("HISTORY")
                .build());
    }

    public void refreshSessionVariables(String sessionId) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(sessionId);
        if (ctx == null) {
            return;
        }
        Map<String, String> vars = dataProvider.getLatestSessionVariables(sessionId);
        ctx.mutator().refreshSessionVariables(vars);
        addLog(RefreshLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(ctx.context())
                .refreshTarget("SESSION_VARIABLES")
                .build());
    }

    public void refreshConversationVariables(String sessionId) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(sessionId);
        if (ctx == null) {
            return;
        }
        Map<String, String> vars = dataProvider.getLatestConversationVariables(sessionId);
        ctx.mutator().refreshConversationVariables(vars);
        addLog(RefreshLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(ctx.context())
                .refreshTarget("CONVERSATION_VARIABLES")
                .build());
    }

    public void refreshChildSessions(String sessionId) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(sessionId);
        if (ctx == null) {
            return;
        }
        List<AgentExecutionContext.ChildSession> children = dataProvider.getLatestChildSessions(sessionId);
        ctx.mutator().refreshChildSessions(children);
        addLog(RefreshLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(ctx.context())
                .refreshTarget("CHILD_SESSIONS")
                .build());
    }

    public void handleChildCreateSession(ChildCreateSession message) {
        ensureInitialized();
        List<String> parentIds = message.getParentSessionIds();
        String parentSessionId = (parentIds != null && !parentIds.isEmpty()) ? parentIds.get(0) : message.getSessionId();
        AgentSessionContext ctx = cache.get(parentSessionId);
        if (ctx != null) {
            List<AgentExecutionContext.ChildSession> current = ctx.context().getChildSessions();
            List<AgentExecutionContext.ChildSession> updated = new ArrayList<>(current);
            updated.add(message.getChildSession());
            ctx.mutator().refreshChildSessions(updated);
            addLog(HandleMessageLogData.builder()
                    .logLevel(LogLevel.INFO)
                    .context(ctx.context())
                    .sessionMessage(message)
                    .build());
        }
    }

    public void handleHistoryMessage(HistoryMessage message) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(message.getSessionId());
        if (ctx != null) {
            List<AgentExecutionContext.HistoryEntry> current = ctx.context().getHistory();
            List<AgentExecutionContext.HistoryEntry> updated = new ArrayList<>(current);
            updated.add(message.getHistoryEntry());
            ctx.mutator().refreshHistory(updated);
            addLog(HandleMessageLogData.builder()
                    .logLevel(LogLevel.INFO)
                    .context(ctx.context())
                    .sessionMessage(message)
                    .build());
        }
    }

    public void handleVariableMessage(VariableMessage message) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(message.getSessionId());
        if (ctx != null) {
            if ("SESSION".equals(message.getScope())) {
                Map<String, String> current = new HashMap<>();
                for (String key : ctx.context().getSessionVariableKeys()) {
                    current.put(key, ctx.context().getSessionVariable(key));
                }
                if ("PUT".equals(message.getOperation())) {
                    current.put(message.getKey(), message.getValue());
                } else {
                    current.remove(message.getKey());
                }
                ctx.mutator().refreshSessionVariables(current);
            } else {
                Map<String, String> current = new HashMap<>();
                for (String key : ctx.context().getConversationVariableKeys()) {
                    current.put(key, ctx.context().getConversationVariable(key));
                }
                if ("PUT".equals(message.getOperation())) {
                    current.put(message.getKey(), message.getValue());
                } else {
                    current.remove(message.getKey());
                }
                ctx.mutator().refreshConversationVariables(current);
            }
            addLog(HandleMessageLogData.builder()
                    .logLevel(LogLevel.INFO)
                    .context(ctx.context())
                    .sessionMessage(message)
                    .build());
        }
    }

    public void handleConversationIdMessage(ConversationIdMessage message) {
        ensureInitialized();
        AgentSessionContext ctx = cache.get(message.getSessionId());
        if (ctx != null) {
            ctx.mutator().refreshConversationId(message.getConversationId());
            addLog(HandleMessageLogData.builder()
                    .logLevel(LogLevel.INFO)
                    .context(ctx.context())
                    .sessionMessage(message)
                    .build());
        }
    }

    /**
     * 会话上下文缓存条目。record 改为 class：
     * - agentContextData：保存防御性拷贝后的会话基本数据（轻量构建时填充，懒构建共享其集合引用）
     * - context：AgentExecutionContext，可空；首次 context() 访问且为 null 时基于 agentContextData 触发懒构建
     * - mutator：随懒构建创建，mutator() 访问时确保 context 已构建
     * - toolInvoking：工具调用中标记
     */
    public static class AgentSessionContext {
        private final AgentContextManager manager;
        private final String sessionId;
        private final ContextDataProvider.AgentContextData agentContextData;
        private final String modelIdOverride;
        private volatile AgentExecutionContext context;
        private volatile AgentExecutionContext.AgentContextMutator mutator;
        private final AtomicBoolean toolInvoking;

        /**
         * 轻量构建构造器：仅承载防御性拷贝后的 AgentContextData，context 为 null（懒构建）。
         */
        AgentSessionContext(AgentContextManager manager, String sessionId,
                            ContextDataProvider.AgentContextData agentContextData,
                            String modelIdOverride) {
            this.manager = manager;
            this.sessionId = sessionId;
            this.agentContextData = agentContextData;
            this.modelIdOverride = modelIdOverride;
            this.context = null;
            this.mutator = null;
            this.toolInvoking = new AtomicBoolean(false);
        }

        /**
         * 预构建构造器：直接注入已构建的 context/mutator（测试或外部集成使用），
         * agentContextData 为 null，context() 直接返回已构建实例，不触发懒构建。
         */
        public AgentSessionContext(AgentExecutionContext context,
                                   AgentExecutionContext.AgentContextMutator mutator,
                                   AtomicBoolean toolInvoking) {
            this.manager = null;
            this.sessionId = context != null ? context.getSessionId() : null;
            this.agentContextData = null;
            this.modelIdOverride = null;
            this.context = context;
            this.mutator = mutator;
            this.toolInvoking = toolInvoking != null ? toolInvoking : new AtomicBoolean(false);
        }

        /**
         * 获取 AgentExecutionContext；首次访问且为 null 时触发懒构建（仅构建一次，双重检查锁）。
         */
        public AgentExecutionContext context() {
            AgentExecutionContext result = context;
            if (result == null) {
                synchronized (this) {
                    result = context;
                    if (result == null) {
                        result = manager.buildExecutionContext(this);
                        context = result;
                    }
                }
            }
            return result;
        }

        /**
         * 获取 AgentContextMutator；context 未构建时先触发懒构建。
         */
        public AgentExecutionContext.AgentContextMutator mutator() {
            context();
            return mutator;
        }

        /**
         * 会话基本数据（防御性拷贝后的 AgentContextData）；context 未构建时也可直接读取。
         */
        public ContextDataProvider.AgentContextData agentContextData() {
            return agentContextData;
        }

        public AtomicBoolean toolInvoking() {
            return toolInvoking;
        }
    }
}
