package com.ghost616.agentbase.service.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.util.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import com.ghost616.agentbase.service.agent.invoker.HookData;
import com.ghost616.agentbase.service.agent.invoker.HookManager;
import com.ghost616.agentbase.service.agent.log.AgentLog;
import com.ghost616.agentbase.service.agent.log.ErrorLogData;
import com.ghost616.agentbase.service.agent.log.HistoryExpandLogData;
import com.ghost616.agentbase.service.agent.log.LogData;
import com.ghost616.agentbase.service.agent.log.ModelCallLogData;
import com.ghost616.agentbase.service.agent.log.RequestEntryLogData;
import com.ghost616.agentbase.service.agent.log.RouteLogData;
import com.ghost616.agentbase.service.agent.log.SkillLoadLogData;
import com.ghost616.agentbase.service.agent.log.StreamEventLogData;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;

import com.ghost616.agentbase.dto.chat.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatChunk;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.dto.model.ToolCall;
import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.AgentErrorCode;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.enums.LogLevel;
import com.ghost616.agentbase.enums.RequestType;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.exception.AgentException;
import com.ghost616.agentbase.service.agent.invoker.HistoryQuerySystemTool;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemToolManager;
import com.ghost616.agentbase.service.agent.invoker.ToolManager;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;

@Slf4j
public class ChatService {

    public static final String TOOL_CONTINUE_MARKER = "[tool_continue]";
    public static final String SEND_USER_MESSAGE_MARKER = "[send_user_message]";

    private static final int DEFAULT_FOLD_INTERVAL = 10;
    private static final String HISTORY_GROUP_PREFIX = "【历史消息组";

    private final AgentComponentRegistry registry;
    private AgentContextManager agentContextManager;
    private SessionManager sessionManager;
    private ModelInvokerManager modelInvokerManager;
    private SystemToolManager systemToolManager;
    private ToolManager toolManager;
    private ChatDataProvider chatDataProvider;

    private HookManager hookManager;
    private volatile boolean initialized;

    public ChatService(AgentComponentRegistry registry) {
        this.registry = registry;
    }

    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    agentContextManager = registry.getAgentContextManager();
                    sessionManager = registry.getSessionManager();
                    modelInvokerManager = registry.getModelInvokerManager();
                    systemToolManager = registry.getSystemToolManager();
                    toolManager = registry.getToolManager();
                    chatDataProvider = registry.getChatDataProvider();
                    hookManager = registry.getHookManager();
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

    public Flux<ServerSentEvent<ChatChunk>> chat(ChatRequest request) {
        ensureInitialized();
        String sessionId = request.getSessionId();
        String content = request.getContent();
        String modelId = request.getModelId();

        AgentContextManager.AgentSessionContext sessionContext =
                agentContextManager.build(sessionId).modelIdOverride(modelId).build();
        AgentExecutionContext context = sessionContext.context();
        AgentExecutionContext.AgentContextMutator contextMutator = sessionContext.mutator();

        boolean isToolContinue = TOOL_CONTINUE_MARKER.equals(content);
        boolean isSendUserMessage = SEND_USER_MESSAGE_MARKER.equals(content);
        String conversationId = request.getConversationId();
        List<ImageContent> images = request.getImages();

        if (!isToolContinue && !isSendUserMessage) {
            contextMutator.resetStopped();
            contextMutator.clearConversationVariables();
            if (context.getParentSessionId() == null
                    && (conversationId == null || conversationId.isBlank())) {
                addLog(ErrorLogData.builder()
                        .logLevel(LogLevel.ERROR)
                        .context(context)
                        .errorCode(AgentErrorCode.PARAM_INVALID.getCode())
                        .message("conversationId 不能为空")
                        .build());
                throw new AgentException(AgentErrorCode.PARAM_INVALID, "conversationId 不能为空");
            }
            contextMutator.setConversationId(conversationId);
            sessionManager.messageSave().sessionId(sessionId).role("user").content(content)
                    .images(images)
                    .userInput(true)
                    .conversationId(context.getConversationId()).save();

            AgentExecutionContext.HistoryEntry userEntry = new AgentExecutionContext.HistoryEntry(
                    "user", content, null, null,
                    LocalDateTime.now(),
                    Collections.emptyList(),
                    null, null, null, images, true);
            contextMutator.addHistoryEntry(userEntry);
        }

        addLog(RequestEntryLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(context)
                .modelId(context.getModelId())
                .content(content)
                .isToolContinue(isToolContinue)
                .build());

        if (modelId != null && !modelId.equals(context.getModelId())) {
            chatDataProvider.updateSessionModelId(sessionId, modelId);
            contextMutator.setModelId(modelId);
        }

        String finalModelId = (modelId != null) ? modelId : context.getModelId();
        ModelConfigData configData = chatDataProvider.getModelConfig(finalModelId);
        if (configData == null) {
            addLog(ErrorLogData.builder()
                    .logLevel(LogLevel.ERROR)
                    .context(context)
                    .errorCode(AgentErrorCode.MODEL_NOT_FOUND.getCode())
                    .message("模型配置不存在: " + finalModelId)
                    .build());
            throw new AgentException(AgentErrorCode.MODEL_NOT_FOUND);
        }

        hookManager.triggerSessionHooks(sessionId, HookPhase.SESSION_START, context, new HookData((ChatChunk) null));
        hookManager.triggerHooks(HookPhase.SESSION_START, context, new HookData((ChatChunk) null));

        String requestType = configData.requestType();
        addLog(RouteLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(context)
                .requestType(requestType)
                .build());
        if (RequestType.RESPONSES.getCode().equals(requestType)) {
            return chatViaResponses(request, context, contextMutator, sessionId, configData);
        }
        if (RequestType.RESPONSES_STATELESS.getCode().equals(requestType)) {
            return chatViaResponsesStateless(request, context, contextMutator, sessionId, configData);
        }
        return chatViaChatCompletions(request, context, contextMutator, sessionId, configData);
    }

    private Flux<ServerSentEvent<ChatChunk>> chatViaChatCompletions(
            ChatRequest request,
            AgentExecutionContext context,
            AgentExecutionContext.AgentContextMutator contextMutator,
            String sessionId,
            ModelConfigData configData) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.builder()
                .role("system")
                .content(context.getSystemPrompt() != null ? context.getSystemPrompt() : "")
                .build());

        String preSystemPrompt = chatDataProvider.getPreSystemPrompt(sessionId);
        if (preSystemPrompt != null && !preSystemPrompt.isBlank()) {
            messages.add(1, Message.builder()
                    .role("system")
                    .content(preSystemPrompt)
                    .build());
        }

        List<ToolDefinition> toolDefinitions = systemToolManager.getToolDefinitions();
        ContextSystemInfo systemInfo = buildContextSystemInfo(context, toolDefinitions);
        messages.addAll(systemInfo.systemMessages());

        for (AgentExecutionContext.HistoryEntry entry : context.getHistory()) {
            messages.add(buildMessageFromEntry(entry));
        }

        FoldResult foldResult = filterAndFold(messages, context);
        messages = foldResult.messages();

        List<Message> postMessages = new ArrayList<>();
        collectLoadedSkillMessages(postMessages, systemInfo.loadedSkillMessages());
        collectAnchorMessages(postMessages, foldResult.anchorMessages());
        collectPostSystemPrompt(postMessages, chatDataProvider.getPostSystemPrompt(sessionId));
        messages = insertPostSystemMessages(messages, postMessages);

        ModelInvoker invoker = modelInvokerManager.getInvoker(configData);

        List<ToolDefinition> tools = buildToolDefinitions(context, invoker, toolDefinitions, systemInfo.filteredLoadedSkills());

        com.ghost616.agentbase.dto.model.ChatRequest chatRequest =
                com.ghost616.agentbase.dto.model.ChatRequest.builder()
                        .messages(messages)
                        .tools(tools)
                        .thinking(request.getThinking())
                        .builtinTools(toolManager.getBuiltinTools(configData.id()))
                        .build();

        addLog(ModelCallLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(context)
                .messageCount(messages.size())
                .toolCount(tools.size())
                .toolNames(tools.stream().map(ToolDefinition::getName).collect(Collectors.toList()))
                .thinking(request.getThinking())
                .build());

        Flux<ChatChunk> stream = invoker.invokeStream(chatRequest);

        return toSseStream(stream, context, contextMutator, sessionId);
    }

    private Flux<ServerSentEvent<ChatChunk>> chatViaResponses(
            ChatRequest request,
            AgentExecutionContext context,
            AgentExecutionContext.AgentContextMutator contextMutator,
            String sessionId,
            ModelConfigData configData) {
        List<ToolDefinition> toolDefinitions = systemToolManager.getToolDefinitions();
        ContextSystemInfo systemInfo = buildContextSystemInfo(context, toolDefinitions);

        List<Message> input = buildIncrementalMessages(context);
        FoldResult foldResult = filterAndFold(input, context);
        input = foldResult.messages();

        String instructions = buildInstructions(context, systemInfo, systemInfo.loadedSkillMessages(), foldResult.anchorMessages(),
                chatDataProvider.getPreSystemPrompt(sessionId), chatDataProvider.getPostSystemPrompt(sessionId));

        ModelInvoker invoker = modelInvokerManager.getInvoker(configData);

        List<ToolDefinition> tools = buildToolDefinitions(context, invoker, toolDefinitions, systemInfo.filteredLoadedSkills());

        String previousResponseId = context.getLastResponseId() != null
                ? context.getLastResponseId()
                : request.getPreviousResponseId();

        com.ghost616.agentbase.dto.model.ChatRequest chatRequest =
                com.ghost616.agentbase.dto.model.ChatRequest.builder()
                        .messages(input)
                        .instructions(instructions)
                        .previousResponseId(previousResponseId)
                        .tools(tools)
                        .thinking(request.getThinking())
                        .builtinTools(toolManager.getBuiltinTools(configData.id()))
                        .build();

        Flux<ChatChunk> stream = invoker.invokeStream(chatRequest);

        return toSseStream(stream, context, contextMutator, sessionId);
    }

    private Flux<ServerSentEvent<ChatChunk>> chatViaResponsesStateless(
            ChatRequest request,
            AgentExecutionContext context,
            AgentExecutionContext.AgentContextMutator contextMutator,
            String sessionId,
            ModelConfigData configData) {
        List<ToolDefinition> toolDefinitions = systemToolManager.getToolDefinitions();
        ContextSystemInfo systemInfo = buildContextSystemInfo(context, toolDefinitions);

        List<Message> input = buildFullMessages(context);
        FoldResult foldResult = filterAndFold(input, context);
        input = foldResult.messages();

        String instructions = buildInstructions(context, systemInfo, systemInfo.loadedSkillMessages(), foldResult.anchorMessages(),
                chatDataProvider.getPreSystemPrompt(sessionId), chatDataProvider.getPostSystemPrompt(sessionId));

        ModelInvoker invoker = modelInvokerManager.getInvoker(configData);

        List<ToolDefinition> tools = buildToolDefinitions(context, invoker, toolDefinitions, systemInfo.filteredLoadedSkills());

        com.ghost616.agentbase.dto.model.ChatRequest chatRequest =
                com.ghost616.agentbase.dto.model.ChatRequest.builder()
                        .messages(input)
                        .instructions(instructions)
                        .tools(tools)
                        .thinking(request.getThinking())
                        .builtinTools(toolManager.getBuiltinTools(configData.id()))
                        .build();

        Flux<ChatChunk> stream = invoker.invokeStream(chatRequest);

        return toSseStream(stream, context, contextMutator, sessionId);
    }

    private String buildInstructions(
            AgentExecutionContext context,
            ContextSystemInfo systemInfo,
            List<Message> loadedSkillMessages,
            List<Message> anchorMessages,
            String preSystemPrompt,
            String postSystemPrompt) {
        StringBuilder instructions = new StringBuilder();
        String systemPrompt = context.getSystemPrompt();
        if (systemPrompt != null) {
            instructions.append(systemPrompt);
        }
        if (preSystemPrompt != null && !preSystemPrompt.isBlank()) {
            if (instructions.length() > 0) {
                instructions.append("\n\n");
            }
            instructions.append(preSystemPrompt);
        }
        for (Message systemMessage : systemInfo.systemMessages()) {
            String content = systemMessage.getContent();
            if (content != null && !content.isEmpty()) {
                if (instructions.length() > 0) {
                    instructions.append("\n\n");
                }
                instructions.append(content);
            }
        }
        for (Message skillMessage : loadedSkillMessages) {
            String content = skillMessage.getContent();
            if (content != null && !content.isEmpty()) {
                if (instructions.length() > 0) {
                    instructions.append("\n\n");
                }
                instructions.append(content);
            }
        }
        for (Message anchorMessage : anchorMessages) {
            String content = anchorMessage.getContent();
            if (content != null && !content.isEmpty()) {
                if (instructions.length() > 0) {
                    instructions.append("\n\n");
                }
                instructions.append(content);
            }
        }
        if (postSystemPrompt != null && !postSystemPrompt.isBlank()) {
            if (instructions.length() > 0) {
                instructions.append("\n\n");
            }
            instructions.append(postSystemPrompt);
        }
        return instructions.toString();
    }

    private Message buildMessageFromEntry(AgentExecutionContext.HistoryEntry entry) {
        Message.MessageBuilder builder = Message.builder()
                .role(entry.role())
                .content(entry.content());
        if (entry.toolCalls() != null && !entry.toolCalls().isEmpty()) {
            builder.toolCalls(entry.toolCalls());
        }
        if (entry.reasoning() != null && !entry.reasoning().isEmpty()
                && entry.toolCalls() != null && !entry.toolCalls().isEmpty()) {
            builder.reasoning(entry.reasoning());
        }
        if (entry.toolInfo() != null) {
            builder.toolInfo(entry.toolInfo());
        }
        if (entry.images() != null && !entry.images().isEmpty()) {
            builder.images(entry.images());
        }
        builder.userInput(entry.userInput());
        return builder.build();
    }

    private List<Message> buildFullMessages(AgentExecutionContext context) {
        List<Message> input = new ArrayList<>();
        for (AgentExecutionContext.HistoryEntry entry : context.getHistory()) {
            if ("system".equals(entry.role())) {
                continue;
            }
            input.add(buildMessageFromEntry(entry));
        }
        return input;
    }

    private List<Message> buildIncrementalMessages(AgentExecutionContext context) {
        List<AgentExecutionContext.HistoryEntry> history = context.getHistory();
        int startIndex = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) {
                startIndex = i;
                break;
            }
        }
        List<Message> input = new ArrayList<>();
        for (int i = startIndex; i < history.size(); i++) {
            AgentExecutionContext.HistoryEntry entry = history.get(i);
            if ("system".equals(entry.role())) {
                continue;
            }
            input.add(buildMessageFromEntry(entry));
        }
        return input;
    }

    private FoldResult filterAndFold(List<Message> messages, AgentExecutionContext context) {
        messages = messages.stream()
                .filter(m -> (m.getContent() != null && !m.getContent().isEmpty()) || (m.getToolCalls() != null && !m.getToolCalls().isEmpty()))
                .collect(Collectors.toList());
        return foldMessageGroups(messages, context);
    }

    private ContextSystemInfo buildContextSystemInfo(AgentExecutionContext context, List<ToolDefinition> toolDefinitions) {
        List<Message> systemMessages = new ArrayList<>();
        List<Message> loadedSkillMessages = new ArrayList<>();
        List<SkillConfigDTO> skills = context.getSkills();
        boolean hasLoadSkillsTool = toolDefinitions.stream()
                .anyMatch(def -> LoadSkillsSystemTool.FULL_TOOL_NAME.equals(def.getName()));

        List<SkillConfigDTO> filteredLoadedSkills = new ArrayList<>();

        if (hasLoadSkillsTool) {
            List<SkillConfigDTO> availableSkills = new ArrayList<>();
            if (skills != null) {
                for (SkillConfigDTO skill : skills) {
                    if (context.isMainSession() && skill.getSessionAuth() == SessionAuthType.CHILD) {
                        continue;
                    }
                    availableSkills.add(skill);
                }
            }
            if (!availableSkills.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("以下是可用的技能（SKILL）列表（技能本身不是工具，需先加载再使用其关联的工具）：\n");
                for (SkillConfigDTO skill : availableSkills) {
                    sb.append("- ").append(skill.getName());
                    if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
                        sb.append(": ").append(skill.getDescription());
                    }
                    sb.append("\n");
                }
                sb.append("\n请使用 ").append(LoadSkillsSystemTool.FULL_TOOL_NAME).append(" 系统工具加载所需技能。加载后，该技能的关联工具将变为可用，届时再调用具体工具。禁止直接以技能名称作为工具调用。");
                systemMessages.add(Message.builder()
                        .role("system")
                        .content(sb.toString())
                        .build());
            }

            List<SkillConfigDTO> loadedSkills = parseLoadedSkills(context, skills);
            for (SkillConfigDTO skill : loadedSkills) {
                if (context.isMainSession() && skill.getSessionAuth() == SessionAuthType.CHILD) {
                    continue;
                }
                filteredLoadedSkills.add(skill);
            }
            if (!filteredLoadedSkills.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("以下技能已加载，请按照其提示词指导执行任务：\n\n");
                for (SkillConfigDTO skill : filteredLoadedSkills) {
                    sb.append("## ").append(skill.getName()).append("\n");
                    if (skill.getPrompt() != null && !skill.getPrompt().isEmpty()) {
                        sb.append(skill.getPrompt()).append("\n\n");
                    }
                }
                loadedSkillMessages.add(Message.builder()
                        .role("system")
                        .content(sb.toString())
                        .build());
                addLog(SkillLoadLogData.builder()
                        .logLevel(LogLevel.INFO)
                        .context(context)
                        .skillNames(filteredLoadedSkills.stream()
                                .map(SkillConfigDTO::getName)
                                .collect(Collectors.toList()))
                        .skillCount(filteredLoadedSkills.size())
                        .build());
            }
        }

        if (context.isMainSession()) {
            List<ToolConfigDTO> childOnlyTools = new ArrayList<>();
            List<ToolConfigDTO> allAuthTools = new ArrayList<>();
            for (ToolConfigDTO t : context.getTools()) {
                SessionAuthType auth = t.getSessionAuth();
                if (auth == SessionAuthType.CHILD) {
                    childOnlyTools.add(t);
                } else if (auth == null || auth == SessionAuthType.ALL) {
                    allAuthTools.add(t);
                }
            }
            List<SkillConfigDTO> childOnlySkills = new ArrayList<>();
            List<SkillConfigDTO> allAuthSkills = new ArrayList<>();
            if (skills != null) {
                for (SkillConfigDTO s : skills) {
                    SessionAuthType auth = s.getSessionAuth();
                    if (auth == SessionAuthType.CHILD) {
                        childOnlySkills.add(s);
                    } else if (auth == null || auth == SessionAuthType.ALL) {
                        allAuthSkills.add(s);
                    }
                }
            }
            if (!childOnlyTools.isEmpty() || !allAuthTools.isEmpty() || !childOnlySkills.isEmpty() || !allAuthSkills.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("以下为子会话相关的工具/技能权限说明。标注【仅子会话可用】的项不可在当前会话直接调用，需通过子会话使用。标注【均可用】的项当前会话可直接调用。\n");
                if (!childOnlyTools.isEmpty()) {
                    sb.append("\n【仅子会话可用】工具：\n");
                    for (ToolConfigDTO t : childOnlyTools) {
                        sb.append("- ").append(t.getName());
                        if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                            sb.append(": ").append(t.getDescription());
                        }
                        sb.append("\n");
                    }
                }
                if (!allAuthTools.isEmpty()) {
                    sb.append("\n【均可用】工具：\n");
                    for (ToolConfigDTO t : allAuthTools) {
                        sb.append("- ").append(t.getName());
                        if (t.getDescription() != null && !t.getDescription().isEmpty()) {
                            sb.append(": ").append(t.getDescription());
                        }
                        sb.append("\n");
                    }
                }
                if (!childOnlySkills.isEmpty()) {
                    sb.append("\n【仅子会话可用】技能：\n");
                    for (SkillConfigDTO s : childOnlySkills) {
                        sb.append("- ").append(s.getName());
                        if (s.getDescription() != null && !s.getDescription().isEmpty()) {
                            sb.append(": ").append(s.getDescription());
                        }
                        sb.append("\n");
                    }
                }
                if (!allAuthSkills.isEmpty()) {
                    sb.append("\n【均可用】技能：\n");
                    for (SkillConfigDTO s : allAuthSkills) {
                        sb.append("- ").append(s.getName());
                        if (s.getDescription() != null && !s.getDescription().isEmpty()) {
                            sb.append(": ").append(s.getDescription());
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n如需使用上述子会话工具/技能，可开启子会话执行任务：创建或复用子会话并通过回调执行用户消息，支持指定工具和技能");
                sb.append("\n子会话执行期间请勿反复调用 callback_sub_session（发消息工具）轮询询问子会话结果，只需等候子会话通过 send_result_to_parent 返回执行结果；如需与子会话多轮交互可等待其返回后再发下一条消息。");
                systemMessages.add(Message.builder()
                        .role("system")
                        .content(sb.toString())
                        .build());
            }
        }

        return new ContextSystemInfo(systemMessages, filteredLoadedSkills, loadedSkillMessages);
    }

    /**
     * 将已加载技能 system 消息收集到统一后置消息列表（null/空列表跳过）。
     */
    private void collectLoadedSkillMessages(List<Message> target, List<Message> loadedSkillMessages) {
        if (loadedSkillMessages == null || loadedSkillMessages.isEmpty()) {
            return;
        }
        target.addAll(loadedSkillMessages);
    }

    /**
     * 将锚点展开 system 消息收集到统一后置消息列表（null/空列表跳过）。
     */
    private void collectAnchorMessages(List<Message> target, List<Message> anchorMessages) {
        if (anchorMessages == null || anchorMessages.isEmpty()) {
            return;
        }
        target.addAll(anchorMessages);
    }

    /**
     * 将会话级后置提示词构建为 system 消息收集到统一后置消息列表（空白字符串跳过）。
     */
    private void collectPostSystemPrompt(List<Message> target, String postSystemPrompt) {
        if (postSystemPrompt == null || postSystemPrompt.isBlank()) {
            return;
        }
        target.add(Message.builder()
                .role("system")
                .content(postSystemPrompt)
                .build());
    }

    /**
     * 将统一后置消息列表插入到最后一条 user 消息之前（无 user 消息时追加到列表末尾），
     * 仅调用一次 findLastUserIndex；postMessages 为 null 或空时直接返回原列表。
     */
    private List<Message> insertPostSystemMessages(List<Message> messages, List<Message> postMessages) {
        if (postMessages == null || postMessages.isEmpty()) {
            return messages;
        }
        List<Message> result = new ArrayList<>(messages);
        int insertIndex = findLastUserIndex(result);
        if (insertIndex < 0) {
            result.addAll(postMessages);
        } else {
            result.addAll(insertIndex, postMessages);
        }
        return result;
    }

    private int findLastUserIndex(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                return i;
            }
        }
        return -1;
    }

    private List<ToolDefinition> buildToolDefinitions(
            AgentExecutionContext context,
            ModelInvoker invoker,
            List<ToolDefinition> toolDefinitions,
            List<SkillConfigDTO> filteredLoadedSkills) {
        Map<String, ToolDefinition> toolMap = new LinkedHashMap<>();
        for (ToolConfigDTO t : context.getTools()) {
            if (context.isMainSession() && SessionAuthType.CHILD == t.getSessionAuth()) {
                continue;
            }
            ToolDefinition def = invoker.toToolDefinition(t);
            toolMap.put(def.getName(), def);
        }
        for (ToolDefinition def : toolDefinitions) {
            toolMap.put(def.getName(), def);
        }
        for (SkillConfigDTO skill : filteredLoadedSkills) {
            if (skill.getSkillTools() != null) {
                for (ToolConfigDTO st : skill.getSkillTools()) {
                    if (context.isMainSession() && SessionAuthType.CHILD == st.getSessionAuth()) {
                        continue;
                    }
                    ToolDefinition def = invoker.toToolDefinition(st);
                    toolMap.put(def.getName(), def);
                }
            }
        }
        return new ArrayList<>(toolMap.values());
    }

    private Flux<ServerSentEvent<ChatChunk>> toSseStream(
            Flux<ChatChunk> stream,
            AgentExecutionContext context,
            AgentExecutionContext.AgentContextMutator contextMutator,
            String sessionId) {
        AtomicBoolean hasToolCalls = new AtomicBoolean(false);

        ThreadVariableHandler threadVariableHandler = registry.getThreadVariableHandler();
        ThreadVariableWrapper threadVariableWrapper = threadVariableHandler != null ? threadVariableHandler.wrap() : null;

        return stream
                .takeWhile(chunk -> !context.isStopped())
                .doOnNext(chunk -> {
                    if (threadVariableWrapper != null) {
                        threadVariableWrapper.apply();
                    }
                    try {
                        if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
                            if (hasToolCalls.compareAndSet(false, true)) {
                                addLog(StreamEventLogData.builder()
                                        .logLevel(LogLevel.INFO)
                                        .context(context)
                                        .eventType("ToolCallDetected")
                                        .hasToolCalls(true)
                                        .build());
                            }
                        }
                        if (chunk.getResponseId() != null) {
                            contextMutator.setLastResponseId(chunk.getResponseId());
                        }
                        if (chunk.getFinishReason() != null) {
                            chunk.setHasToolCalls(hasToolCalls.get());
                            addLog(StreamEventLogData.builder()
                                    .logLevel(LogLevel.INFO)
                                    .context(context)
                                    .eventType("StreamComplete")
                                    .hasToolCalls(hasToolCalls.get())
                                    .build());
                        }
                        hookManager.triggerSessionHooks(sessionId, HookPhase.BEFORE_MESSAGE_SEND, context, new HookData(chunk));
                        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, context, new HookData(chunk));
                        hookManager.executePostHooks(context, new HookData(chunk));
                    } finally {
                        if (threadVariableWrapper != null) {
                            threadVariableWrapper.clear();
                        }
                    }
                })
                .map(chunk -> ServerSentEvent.<ChatChunk>builder()
                        .data(chunk)
                        .build())
                .doOnComplete(() -> {
                    if (threadVariableWrapper != null) {
                        threadVariableWrapper.apply();
                    }
                    try {
                        ChatChunk completeChunk = ChatChunk.builder()
                                .hasToolCalls(hasToolCalls.get())
                                .build();
                        hookManager.triggerSessionHooks(sessionId, HookPhase.AFTER_MESSAGE_RECEIVE, context, new HookData(completeChunk));
                        hookManager.triggerHooks(HookPhase.AFTER_MESSAGE_RECEIVE, context, new HookData(completeChunk));
                        hookManager.executePostHooks(context, new HookData(completeChunk));
                    } finally {
                        if (threadVariableWrapper != null) {
                            threadVariableWrapper.clear();
                        }
                    }
                })
                .doOnCancel(() -> {
                    addLog(StreamEventLogData.builder()
                            .logLevel(LogLevel.INFO)
                            .context(context)
                            .eventType("StreamCancelled")
                            .hasToolCalls(hasToolCalls.get())
                            .build());
                    contextMutator.setStopped();
                });
    }

    private List<SkillConfigDTO> parseLoadedSkills(AgentExecutionContext context, List<SkillConfigDTO> skills) {
        String json = context.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> loadedNames;
        try {
            loadedNames = JsonMapper.MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("解析 _sys_loading_SKILLS 失败: {}", json, e);
            addLog(ErrorLogData.builder()
                    .logLevel(LogLevel.ERROR)
                    .context(context)
                    .errorCode(AgentErrorCode.SYSTEM_ERROR.getCode())
                    .message("解析已加载技能列表失败")
                    .exception(e)
                    .build());
            return List.of();
        }
        if (loadedNames == null || loadedNames.isEmpty()) {
            return List.of();
        }
        Set<String> nameSet = new HashSet<>(loadedNames);
        List<SkillConfigDTO> result = new ArrayList<>();
        if (skills != null) {
            for (SkillConfigDTO skill : skills) {
                if (nameSet.contains(skill.getName())) {
                    result.add(skill);
                }
            }
        }
        return result;
    }

    private FoldResult foldMessageGroups(List<Message> messages, AgentExecutionContext context) {
        Integer recentCount = context.getRecentMessageCount();
        if (recentCount == null || recentCount <= 0) {
            return new FoldResult(messages, List.of());
        }

        Set<Integer> expandedIndices = parseExpandedIndices(
                context.getConversationVariable(HistoryQuerySystemTool.VAR_NAME), context);

        List<Message> prefix = new ArrayList<>();
        int startIndex = 0;
        while (startIndex < messages.size() && "system".equals(messages.get(startIndex).getRole())) {
            prefix.add(messages.get(startIndex));
            startIndex++;
        }

        List<List<Message>> groups = new ArrayList<>();
        int i = startIndex;
        while (i < messages.size()) {
            int groupStart = i;
            i++;
            while (i < messages.size() && !isFoldGroupStart(messages.get(i))) {
                i++;
            }
            List<Message> group = new ArrayList<>();
            for (int j = groupStart; j < i; j++) {
                group.add(messages.get(j));
            }
            groups.add(group);
        }

        if (groups.size() <= recentCount) {
            return new FoldResult(messages, List.of());
        }

        int foldedCount = Math.max(0, (groups.size() - recentCount) / DEFAULT_FOLD_INTERVAL) * DEFAULT_FOLD_INTERVAL;
        if (foldedCount == 0) {
            return new FoldResult(messages, List.of());
        }

        List<Message> result = new ArrayList<>(prefix);

        for (int g = 0; g < groups.size(); g++) {
            if (g < foldedCount) {
                // 折叠区仅保留首条 user 消息的 content 文本（忽略图片，避免图片随折叠历史重复传给模型）
                List<Message> group = groups.get(g);
                Message first = group.get(0);
                result.add(Message.builder()
                        .role(first.getRole())
                        .content(first.getContent())
                        .build());
                result.add(Message.builder()
                        .role("assistant")
                        .content("此为历史消息索引为" + g + "，如果想要展开请调用历史消息工具")
                        .build());
            } else {
                result.addAll(groups.get(g));
            }
        }

        List<Message> anchorMessages = new ArrayList<>();
        List<String> expandedMessages = new ArrayList<>();
        List<Integer> sortedIndices = new ArrayList<>(expandedIndices);
        Collections.sort(sortedIndices);
        for (int index : sortedIndices) {
            if (index >= 0 && index < foldedCount) {
                Message anchor = buildHistoryGroupMessage(groups.get(index), index, context);
                anchorMessages.add(anchor);
                expandedMessages.add(anchor.getContent());
            }
        }

        addLog(HistoryExpandLogData.builder()
                .logLevel(LogLevel.INFO)
                .context(context)
                .foldedCount(foldedCount)
                .expandedMessages(expandedMessages)
                .build());

        return new FoldResult(result, anchorMessages);
    }

    /**
     * 折叠分组点判断：仅 user 角色且 user_input=true（用户真实输入）的 user 消息产生新组；
     * user_input=false（会话间传递，如子会话/父会话推送的 user 消息）不产生新组，归入相邻组。
     *
     * @param message 待判断的消息
     * @return 是否为折叠分组起点
     */
    private boolean isFoldGroupStart(Message message) {
        return "user".equals(message.getRole()) && Boolean.TRUE.equals(message.getUserInput());
    }

    private Message buildHistoryGroupMessage(List<Message> group, int groupIndex, AgentExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append(HISTORY_GROUP_PREFIX).append(groupIndex).append("】完整内容如下：\n");
        for (Message message : group) {
            try {
                sb.append(JsonMapper.MAPPER.writeValueAsString(toHistoryGroupJson(message))).append("\n");
            } catch (Exception e) {
                log.warn("序列化历史组消息失败: {}", e.getMessage());
                addLog(ErrorLogData.builder()
                        .logLevel(LogLevel.WARN)
                        .context(context)
                        .errorCode(AgentErrorCode.SYSTEM_ERROR.getCode())
                        .message("序列化历史组消息失败")
                        .exception(e)
                        .build());
            }
        }
        return Message.builder()
                .role("system")
                .content(sb.toString())
                .build();
    }

    private Map<String, Object> toHistoryGroupJson(Message message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("role", message.getRole());
        if (message.getContent() != null) {
            json.put("content", message.getContent());
        }
        if ("assistant".equals(message.getRole())
                && message.getReasoning() != null && !message.getReasoning().isEmpty()
                && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            json.put("reasoning", message.getReasoning());
        }
        if ("assistant".equals(message.getRole())
                && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            for (ToolCall toolCall : message.getToolCalls()) {
                Map<String, Object> callJson = new LinkedHashMap<>();
                callJson.put("name", toolCall.getName());
                if (toolCall.getId() != null) {
                    callJson.put("id", toolCall.getId());
                }
                if (toolCall.getArguments() != null) {
                    callJson.put("arguments", toolCall.getArguments());
                }
                toolCalls.add(callJson);
            }
            json.put("tool_calls", toolCalls);
        }
        if (message.getToolInfo() != null) {
            Map<String, Object> toolInfo = new LinkedHashMap<>();
            toolInfo.put("name", message.getToolInfo().toolName());
            toolInfo.put("id", message.getToolInfo().toolCallId());
            json.put("tool_info", toolInfo);
        }
        return json;
    }

    private Set<Integer> parseExpandedIndices(String jsonStr, AgentExecutionContext context) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return Collections.emptySet();
        }
        try {
            List<Integer> list = JsonMapper.MAPPER.readValue(
                    jsonStr, new TypeReference<List<Integer>>() {});
            return new HashSet<>(list);
        } catch (Exception e) {
            log.debug("解析 _sys_his_msgs_index 失败: {}", jsonStr, e);
            addLog(ErrorLogData.builder()
                    .logLevel(LogLevel.ERROR)
                    .context(context)
                    .errorCode(AgentErrorCode.SYSTEM_ERROR.getCode())
                    .message("解析历史消息展开索引失败")
                    .exception(e)
                    .build());
            return Collections.emptySet();
        }
    }

    private record ContextSystemInfo(
            List<Message> systemMessages,
            List<SkillConfigDTO> filteredLoadedSkills,
            List<Message> loadedSkillMessages) {
    }

    private record FoldResult(List<Message> messages, List<Message> anchorMessages) {
    }
}
