package com.ghost616.agentbase.service.agent;

import com.ghost616.agentbase.dto.model.CustomToolCall;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.dto.model.WebSearchCall;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.sendmessage.ChildCreateSession;
import com.ghost616.agentbase.sendmessage.ConversationIdMessage;
import com.ghost616.agentbase.sendmessage.HistoryMessage;
import com.ghost616.agentbase.sendmessage.MessageSender;
import com.ghost616.agentbase.sendmessage.ChildMessageEvent;
import com.ghost616.agentbase.sendmessage.VariableMessage;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Getter
public class AgentExecutionContext {

    private final String sessionId;
    private final String agentId;
    private final String systemPrompt;
    private String modelId;
    private final String parentSessionId;
    private final Integer recentMessageCount;
    @Getter(AccessLevel.NONE)
    private final List<HistoryEntry> history;
    private final List<ToolConfigDTO> tools;
    @Getter(AccessLevel.NONE)
    private final AgentContextMutator mutator;
    @Getter(AccessLevel.NONE)
    private final Map<String, String> sessionVariables;
    @Getter(AccessLevel.NONE)
    private final Map<String, String> conversationVariables;
    private final List<SkillConfigDTO> skills;
    private final String projectDir;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    @Getter(AccessLevel.NONE)
    private final List<ChildSession> childSessions;

    /** 最近一次模型响应的 ID（Responses API 有状态续接时作为 previousResponseId） */
    private String lastResponseId;

    /** 对话 ID（会话归属的对话标识，子会话通过 mutator 委托获取父会话的对话 ID） */
    private String conversationId;

    public AgentExecutionContext(String sessionId, String agentId, String systemPrompt, String modelId,
                                  Integer recentMessageCount,
                                 List<HistoryEntry> history, List<ToolConfigDTO> tools,
                                 List<SkillConfigDTO> skills,
                                 AgentContextMutator mutator,
                                  Map<String, String> sessionVariables,
                                  Map<String, String> conversationVariables,
                                  String parentSessionId, String projectDir, List<ChildSession> childSessions,
                                  String conversationId) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.systemPrompt = systemPrompt;
        this.modelId = modelId;
        this.parentSessionId = parentSessionId;
        this.recentMessageCount = recentMessageCount;
        this.history = history;
        this.tools = tools;
        this.skills = skills;
        this.mutator = mutator;
        this.sessionVariables = sessionVariables;
        this.conversationVariables = conversationVariables;
        this.projectDir = projectDir;
        // 共享外部传入的 childSessions 引用（而非拷贝），使 mutator 原地更新（refreshChildSessions/createChildSession）
        // 能同步反映到持有同一引用的 AgentContextData.childSessions()
        this.childSessions = childSessions != null ? childSessions : new ArrayList<>();
        this.conversationId = conversationId;
        this.mutator.bind(this);
    }

    public String getConversationId() {
        if (parentSessionId != null) {
            return mutator.getConversationId();
        }
        return conversationId;
    }

    public List<ChildSession> getChildSessions() {
        return Collections.unmodifiableList(childSessions);
    }

    public String createChildSession(String sessionName, String description, String modelId,
                                      List<String> toolIds, List<String> skillIds, String prompt) {
        String childSessionId = mutator.createChildSession(sessionName, description, modelId, toolIds, skillIds, prompt);
        if (childSessionId != null) {
            childSessions.add(new ChildSession(childSessionId, sessionName, description, modelId));
        }
        return childSessionId;
    }

    public void sendUserMessage(String childSessionId, String content, String modelId, Boolean thinking) {
        sendUserMessage(childSessionId, content, modelId, thinking, null);
    }

    /**
     * 向子会话发送用户消息（支持图片对象数组）。
     *
     * @param childSessionId 子会话 ID
     * @param content        用户消息内容
     * @param modelId        模型 ID（可为 null）
     * @param thinking       是否启用思考模式（可为 null）
     * @param images         图片列表（可为 null/空，imgId 仅供前端关联，不传给模型）
     */
    public void sendUserMessage(String childSessionId, String content, String modelId, Boolean thinking,
                                List<ImageContent> images) {
        mutator.sendUserMessage(childSessionId, content, modelId, thinking, images);
    }

    /**
     * 向父会话发送用户消息（子会话 → 父会话）。
     * 无父会话或回调未注入时静默忽略。
     *
     * @param content 要发送的消息内容
     */
    public void sendParentMessage(String content) {
        mutator.sendParentMessage(content);
    }

    public record ChildSession(String sessionId, String sessionName, String description, String modelId) {
    }

    public List<HistoryEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void putSessionVariable(String key, String value) {
        if (parentSessionId != null) {
            mutator.putSessionVariable(key, value);
            return;
        }
        sessionVariables.put(key, value);
        mutator.putSessionVariable(key, value);
    }

    public void putConversationVariable(String key, String value) {
        if (parentSessionId != null) {
            mutator.putConversationVariable(key, value);
            return;
        }
        conversationVariables.put(key, value);
        mutator.putConversationVariable(key, value);
    }

    public String getSessionVariable(String key) {
        if (parentSessionId != null) {
            return mutator.getSessionVariable(key);
        }
        return sessionVariables.get(key);
    }

    public String getConversationVariable(String key) {
        if (parentSessionId != null) {
            return mutator.getConversationVariable(key);
        }
        return conversationVariables.get(key);
    }

    public void removeSessionVariable(String key) {
        if (parentSessionId != null) {
            mutator.removeSessionVariable(key);
            return;
        }
        sessionVariables.remove(key);
        mutator.removeSessionVariable(key);
    }

    public void removeConversationVariable(String key) {
        if (parentSessionId != null) {
            mutator.removeConversationVariable(key);
            return;
        }
        conversationVariables.remove(key);
        mutator.removeConversationVariable(key);
    }

    public Set<String> getSessionVariableKeys() {
        if (parentSessionId != null) {
            return mutator.getSessionVariableKeys();
        }
        return sessionVariables.keySet();
    }

    public Set<String> getConversationVariableKeys() {
        if (parentSessionId != null) {
            return mutator.getConversationVariableKeys();
        }
        return conversationVariables.keySet();
    }

    /**
     * 判断当前会话是否为主会话。
     *
     * @return 若 parentSessionId 为 null 则返回 true（主会话），否则返回 false（子会话）
     */
    public boolean isMainSession() {
        return parentSessionId == null;
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public record HistoryEntry(String role, String content, String reasoning, ToolInfo toolInfo,
                               LocalDateTime createTime, List<ToolCall> toolCalls,
                               UsageInfo usage, List<WebSearchCall> webSearchCall,
                               List<CustomToolCall> customToolCall, List<ImageContent> images) {
    }

    public static class AgentContextMutator {
        private AgentExecutionContext context;
        BiConsumer<String, String> sessionVarPutCallback;
        Consumer<String> sessionVarRemoveCallback;
        BiConsumer<String, String> conversationVarPutCallback;
        Consumer<String> conversationVarRemoveCallback;
        Function<String, String> getSessionVarCallback;
        Function<String, String> getConversationVarCallback;
        Supplier<Set<String>> getSessionVarKeysCallback;
        Supplier<Set<String>> getConversationVarKeysCallback;
        CreateChildSessionCallback createChildSessionCallback;
        SendUserMessageCallback sendUserMessageCallback;
        SendParentMessageCallback sendParentMessageCallback;
        Supplier<String> conversationIdSupplier;
        private MessageSender messageSender;

        @FunctionalInterface
        public interface CreateChildSessionCallback {
            String create(String parentSessionId, String sessionName, String description, String modelId,
                          List<String> toolIds, List<String> skillIds, String prompt);
        }

        @FunctionalInterface
        public interface SendUserMessageCallback {
            void send(String childSessionId, String content, String modelId, Boolean thinking,
                      List<ImageContent> images);
        }

        @FunctionalInterface
        public interface SendParentMessageCallback {
            void send(String parentSessionId, String content, String conversationId);
        }

        public void bind(AgentExecutionContext context) {
            this.context = context;
        }

        public void setModelId(String modelId) {
            context.modelId = modelId;
        }

        public void addHistoryEntry(HistoryEntry entry) {
            context.history.add(entry);
            if (messageSender != null) {
                messageSender.send(new HistoryMessage(context.sessionId, entry));
            }
        }

        public void putSessionVariable(String key, String value) {
            if (sessionVarPutCallback != null) {
                sessionVarPutCallback.accept(key, value);
            }
            if (messageSender != null) {
                messageSender.send(new VariableMessage(context.sessionId, "SESSION", key, value, "PUT"));
            }
        }

        public void removeSessionVariable(String key) {
            if (sessionVarRemoveCallback != null) {
                sessionVarRemoveCallback.accept(key);
            }
            if (messageSender != null) {
                messageSender.send(new VariableMessage(context.sessionId, "SESSION", key, null, "REMOVE"));
            }
        }

        public void putConversationVariable(String key, String value) {
            if (conversationVarPutCallback != null) {
                conversationVarPutCallback.accept(key, value);
            }
            if (messageSender != null) {
                messageSender.send(new VariableMessage(context.sessionId, "CONVERSATION", key, value, "PUT"));
            }
        }

        public void removeConversationVariable(String key) {
            if (conversationVarRemoveCallback != null) {
                conversationVarRemoveCallback.accept(key);
            }
            if (messageSender != null) {
                messageSender.send(new VariableMessage(context.sessionId, "CONVERSATION", key, null, "REMOVE"));
            }
        }

        public String getSessionVariable(String key) {
            if (getSessionVarCallback != null) {
                return getSessionVarCallback.apply(key);
            }
            return null;
        }

        public String getConversationVariable(String key) {
            if (getConversationVarCallback != null) {
                return getConversationVarCallback.apply(key);
            }
            return null;
        }

        public Set<String> getSessionVariableKeys() {
            if (getSessionVarKeysCallback != null) {
                return getSessionVarKeysCallback.get();
            }
            return Collections.emptySet();
        }

        public Set<String> getConversationVariableKeys() {
            if (getConversationVarKeysCallback != null) {
                return getConversationVarKeysCallback.get();
            }
            return Collections.emptySet();
        }

        public void refreshHistory(List<HistoryEntry> history) {
            context.history.clear();
            context.history.addAll(history);
        }

        public void refreshSessionVariables(Map<String, String> vars) {
            context.sessionVariables.clear();
            if (vars != null) {
                context.sessionVariables.putAll(vars);
            }
        }

        public void refreshConversationVariables(Map<String, String> vars) {
            context.conversationVariables.clear();
            if (vars != null) {
                context.conversationVariables.putAll(vars);
            }
        }

        public void refreshChildSessions(List<ChildSession> children) {
            context.childSessions.clear();
            if (children != null) {
                context.childSessions.addAll(children);
            }
        }

        public void clearConversationVariables() {
            if (context != null) {
                context.conversationVariables.clear();
            }
        }

        public void setStopped() {
            context.stopped.set(true);
        }

        public void resetStopped() {
            context.stopped.set(false);
        }

        public void setLastResponseId(String lastResponseId) {
            context.lastResponseId = lastResponseId;
        }

        public void setConversationId(String conversationId) {
            context.conversationId = conversationId;
            if (messageSender != null) {
                messageSender.send(new ConversationIdMessage(context.sessionId, conversationId));
            }
        }

        public void refreshConversationId(String conversationId) {
            context.conversationId = conversationId;
        }

        public String getConversationId() {
            if (conversationIdSupplier != null) {
                return conversationIdSupplier.get();
            }
            return context.conversationId;
        }

        public String createChildSession(String sessionName, String description, String modelId,
                                           List<String> toolIds, List<String> skillIds, String prompt) {
            if (context.parentSessionId != null) {
                return null;
            }
            if (sessionName == null || sessionName.isBlank()) {
                return null;
            }
            if (modelId == null) {
                modelId = context.modelId;
            }
            String childSessionId = null;
            if (createChildSessionCallback != null) {
                childSessionId = createChildSessionCallback.create(context.sessionId, sessionName, description, modelId,
                        toolIds, skillIds, prompt);
            }
            if (childSessionId != null && messageSender != null) {
                messageSender.send(new ChildCreateSession(context.sessionId,
                        new ChildSession(childSessionId, sessionName, description, modelId)));
            }
            return childSessionId;
        }

        public void sendUserMessage(String childSessionId, String content, String modelId, Boolean thinking) {
            sendUserMessage(childSessionId, content, modelId, thinking, null);
        }

        /**
         * 向子会话发送用户消息（支持图片对象数组）。
         * 先调用 sendUserMessageCallback 回调（可为 null），再发送 ChildMessageEvent 事件（messageSender 非 null 时）。
         *
         * @param childSessionId 子会话 ID
         * @param content        用户消息内容
         * @param modelId        模型 ID（可为 null）
         * @param thinking       是否启用思考模式（可为 null）
         * @param images         图片列表（可为 null/空，imgId 仅供前端关联，不传给模型）
         */
        public void sendUserMessage(String childSessionId, String content, String modelId, Boolean thinking,
                                    List<ImageContent> images) {
            if (sendUserMessageCallback != null) {
                sendUserMessageCallback.send(childSessionId, content, modelId, thinking, images);
            }
            if (messageSender != null) {
                messageSender.send(new ChildMessageEvent(childSessionId, childSessionId, content, modelId, thinking, images));
            }
        }

        /**
         * 向父会话发送用户消息（子会话 → 父会话）。
         * 从 context.parentSessionId 获取父会话 ID：为 null 或空白时静默忽略（debug 日志）；
         * 否则调用 sendParentMessageCallback.send(parentSessionId, content, 动态获取的 conversationId)；
         * callback 为 null 时静默忽略。
         *
         * @param content 要发送的消息内容
         */
        public void sendParentMessage(String content) {
            String parentId = context.parentSessionId;
            if (parentId == null || parentId.isBlank()) {
                log.debug("会话 {} 无父会话，忽略 sendParentMessage", context.sessionId);
                return;
            }
            if (sendParentMessageCallback != null) {
                sendParentMessageCallback.send(parentId, content, getConversationId());
            }
        }

        public void setMessageSender(MessageSender messageSender) {
            this.messageSender = messageSender;
        }
    }
}
