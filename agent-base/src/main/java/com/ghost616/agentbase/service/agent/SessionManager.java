package com.ghost616.agentbase.service.agent;

import java.util.List;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.MessageQueryLogData;
import com.ghost616.agentbase.service.agent.log.MessageRollbackLogData;
import com.ghost616.agentbase.service.agent.log.MessageSaveLogData;
import com.ghost616.agentbase.service.agent.log.SessionErrorLogData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionManager {

    private final AgentComponentRegistry registry;
    private MessageDataProvider dataProvider;
    private volatile boolean initialized;

    public SessionManager(AgentComponentRegistry registry) {
        this.registry = registry;
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    dataProvider = registry.getMessageDataProvider();
                    initialized = true;
                }
            }
        }
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

    public MessageSaveBuilder messageSave() {
        ensureInitialized();
        return new MessageSaveBuilder();
    }

    public class MessageSaveBuilder {
        private String sessionId;
        private String role;
        private String content;
        private String reasoning;
        private ToolInfo toolInfo;
        private String toolResult;
        private List<MessageDataProvider.ToolCallData> toolCalls;
        private UsageInfo usage;
        private List<MessageDataProvider.WebSearchCallData> webSearchCall;
        private List<MessageDataProvider.CustomToolCallData> customToolCall;
        private String conversationId;
        private List<ImageContent> images;

        private MessageSaveBuilder() {
        }

        public MessageSaveBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public MessageSaveBuilder role(String role) {
            this.role = role;
            return this;
        }

        public MessageSaveBuilder content(String content) {
            this.content = content;
            return this;
        }

        public MessageSaveBuilder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public MessageSaveBuilder toolInfo(ToolInfo toolInfo) {
            this.toolInfo = toolInfo;
            return this;
        }

        public MessageSaveBuilder toolResult(String toolResult) {
            this.toolResult = toolResult;
            return this;
        }

        public MessageSaveBuilder toolCalls(List<MessageDataProvider.ToolCallData> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public MessageSaveBuilder usage(UsageInfo usage) {
            this.usage = usage;
            return this;
        }

        public MessageSaveBuilder webSearchCall(List<MessageDataProvider.WebSearchCallData> webSearchCall) {
            this.webSearchCall = webSearchCall;
            return this;
        }

        public MessageSaveBuilder customToolCall(List<MessageDataProvider.CustomToolCallData> customToolCall) {
            this.customToolCall = customToolCall;
            return this;
        }

        public MessageSaveBuilder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public MessageSaveBuilder images(List<ImageContent> images) {
            this.images = images;
            return this;
        }

        public String save() {
            if (sessionId == null) {
                addLog(SessionErrorLogData.builder()
                        .logLevel(LogLevel.ERROR)
                        .conversationId(conversationId)
                        .errorCode(AgentErrorCode.PARAM_INVALID.getCode())
                        .message("sessionId 不能为空")
                        .build());
                throw new AgentException(AgentErrorCode.PARAM_INVALID, "sessionId 不能为空");
            }
            if (role == null) {
                addLog(SessionErrorLogData.builder()
                        .logLevel(LogLevel.ERROR)
                        .sessionId(sessionId)
                        .conversationId(conversationId)
                        .errorCode(AgentErrorCode.PARAM_INVALID.getCode())
                        .message("role 不能为空")
                        .build());
                throw new AgentException(AgentErrorCode.PARAM_INVALID, "role 不能为空");
            }
            if (content == null) {
                addLog(SessionErrorLogData.builder()
                        .logLevel(LogLevel.ERROR)
                        .sessionId(sessionId)
                        .conversationId(conversationId)
                        .errorCode(AgentErrorCode.PARAM_INVALID.getCode())
                        .message("content 不能为空")
                        .build());
                throw new AgentException(AgentErrorCode.PARAM_INVALID, "content 不能为空");
            }
            String messageId = dataProvider.saveMessage(sessionId, role, content, reasoning,
                    toolInfo, toolResult, toolCalls, usage, webSearchCall, customToolCall, conversationId, images);
            addLog(MessageSaveLogData.builder()
                    .logLevel(LogLevel.INFO)
                    .sessionId(sessionId)
                    .role(role)
                    .messageId(messageId)
                    .content(content)
                    .reasoning(reasoning)
                    .toolInfo(toolInfo)
                    .toolResult(toolResult)
                    .toolCalls(toolCalls)
                    .usage(usage)
                    .webSearchCall(webSearchCall)
                    .customToolCall(customToolCall)
                    .conversationId(conversationId)
                    .build());
            return messageId;
        }
    }

    public List<MessageDataProvider.MessageDTO> getMessages(String sessionId) {
        ensureInitialized();
        List<MessageDataProvider.MessageDTO> messages = dataProvider.getMessages(sessionId);
        addLog(MessageQueryLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(sessionId)
                .messageCount(messages == null ? 0 : messages.size())
                .build());
        return messages;
    }

    public int rollbackToLastUserMessage(String sessionId) {
        ensureInitialized();
        int rollbackCount = dataProvider.rollbackToLastUserMessage(sessionId);
        addLog(MessageRollbackLogData.builder()
                .logLevel(LogLevel.INFO)
                .sessionId(sessionId)
                .rollbackCount(rollbackCount)
                .build());
        return rollbackCount;
    }
}
