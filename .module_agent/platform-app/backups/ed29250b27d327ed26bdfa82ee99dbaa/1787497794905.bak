package com.ghost616.platform.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.agentbase.core.ThreadVariableHandler;
import com.ghost616.agentbase.core.ThreadVariableWrapper;
import com.ghost616.agentbase.dto.model.ChatRequest;
import com.ghost616.agentbase.dto.model.ChatResponse;
import com.ghost616.agentbase.dto.model.EmbeddingRequest;
import com.ghost616.agentbase.dto.model.EmbeddingResponse;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.dto.memory.MemoryRegenerateStatusDTO;
import com.ghost616.platform.enums.AggregationType;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.platform.model.SessionMemoryDocument;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.service.search.SessionMemoryESClient;
import com.ghost616.platform.session.UserContext;
import com.ghost616.platform.util.IdConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 会话记忆聚合服务：定时将启用记忆功能的智能体会话的新增消息按用户轮次摘要并向量化写入 ES 索引 session_memory。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMemoryService {

    /** 单个会话失败重试次数 */
    private static final int MAX_RETRY = 5;

    /** LLM 主题归类系统提示词 */
    private static final String SYSTEM_TOPIC_CLASSIFY_PROMPT =
            "你是对话主题归类助手。下面列出了若干对话片段，请为每个片段按行指定一个简短的主题标签。"
                    + "仅相邻片段可归为同一主题，不相邻的相同主题片段也必须使用不同标签以保持连续。"
                    + "输出格式为每行一个片段：\"序号. 主题\"，序号必须与输入顺序一致，主题控制在 4 字以内。";

    /** LLM 大组汇总系统提示词 */
    private static final String SYSTEM_GROUP_SUMMARY_PROMPT =
            "你是对话摘要助手。下面是同一主题下的若干对话片段概要，请将其汇总为一段连贯的中文摘要，"
                    + "保留关键事实、用户意图与结论，不超过 300 字。";

    /** LLM 按日全量摘要系统提示词 */
    private static final String SYSTEM_DAILY_SUMMARY_PROMPT =
            "你是对话摘要助手。下面是某会话一天内的全部对话内容，请将其汇总为一段连贯的中文摘要，"
                    + "保留关键事实、用户意图与结论，不超过 300 字。";

    /** 主题归类行格式：序号. 主题（序号 + 点/顿号 + 主题） */
    private static final Pattern TOPIC_LINE_PATTERN = Pattern.compile("^(\\d+)[.、．]\\s*(.*)$");

    private final AgentConfigMapper agentConfigMapper;
    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ModelInvokerManager modelInvokerManager;
    private final SessionMemoryESClient sessionMemoryESClient;
    private final ThreadVariableHandler threadVariableHandler;

    /** 聚合文本重生成状态缓存（key=sessionId） */
    private final Map<Long, MemoryRegenerateStatusDTO> regenerateStatusMap = new ConcurrentHashMap<>();

    /**
     * 读取会话的记忆提示语（session.memory_prompt）。
     *
     * @param sessionId 会话 ID
     * @return 记忆提示语，未设置时返回 null
     */
    public String getMemoryPrompt(Long sessionId) {
        Session session = requireSession(sessionId);
        return session.getMemoryPrompt();
    }

    /**
     * 保存会话的记忆提示语（session.memory_prompt）。
     *
     * @param sessionId 会话 ID
     * @param prompt    记忆提示语
     */
    public void saveMemoryPrompt(Long sessionId, String prompt) {
        Session session = requireSession(sessionId);
        session.setMemoryPrompt(prompt);
        sessionMapper.updateById(session);
    }

    /**
     * 异步重生成指定文档的聚合摘要文本：获取 [startSeq, endSeq] 区间消息，用会话 modelId 对应 LLM 生成新聚合文本，
     * 完成后可通过 {@link #getRegenerateStatus(Long)} 查询结果。prompt 为空时使用现有聚合提示语 SYSTEM_GROUP_SUMMARY_PROMPT 兜底。
     *
     * @param sessionId 会话 ID
     * @param docId     目标 ES 文档 ID
     * @param startSeq  起始消息序号（含）
     * @param endSeq    结束消息序号（含）
     * @param prompt    自定义提示语，可为 null
     * @return 初始重生成状态（RUNNING）
     */
    public MemoryRegenerateStatusDTO regenerateSummary(Long sessionId, String docId,
                                                       Integer startSeq, Integer endSeq, String prompt) {
        Session session = requireSession(sessionId);
        MemoryRegenerateStatusDTO status = MemoryRegenerateStatusDTO.builder()
                .sessionId(sessionId)
                .docId(docId)
                .status("RUNNING")
                .build();
        regenerateStatusMap.put(sessionId, status);
        ThreadVariableWrapper threadVariableWrapper = threadVariableHandler.wrap();
        CompletableFuture.runAsync(() -> {
            if (threadVariableWrapper != null) {
                threadVariableWrapper.apply();
            }
            try {
                doRegenerateSummary(session, status, startSeq, endSeq, prompt);
            } finally {
                UserContext.clear();
            }
        });
        return status;
    }

    /**
     * 查询聚合文本重生成状态。
     *
     * @param sessionId 会话 ID
     * @return 重生成状态，不存在时返回 null
     */
    public MemoryRegenerateStatusDTO getRegenerateStatus(Long sessionId) {
        return regenerateStatusMap.get(sessionId);
    }

    /**
     * 保存聚合文本：用智能体 vectorModelId 对应向量模型重新向量化新聚合文本后，调用 ES 更新指定文档。
     *
     * @param sessionId 会话 ID
     * @param docId     目标 ES 文档 ID
     * @param text      新的聚合摘要文本
     */
    public void saveAggregationText(Long sessionId, String docId, String text) {
        Session session = requireSession(sessionId);
        AgentConfig agentConfig = agentConfigMapper.selectById(session.getAgentId());
        if (agentConfig == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        ModelInvoker embedInvoker = resolveEmbedInvoker(agentConfig);
        if (embedInvoker == null) {
            throw new BusinessException(ErrorCode.AGENT_MEMORY_VECTOR_MODEL_REQUIRED);
        }
        List<Float> vector = embedText(embedInvoker, agentConfig, text);
        if (vector == null || vector.isEmpty()) {
            throw new BusinessException(ErrorCode.AGENT_MEMORY_VECTOR_MODEL_REQUIRED);
        }
        sessionMemoryESClient.updateDocument(docId, text, vector);
    }

    private Session requireSession(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    private void doRegenerateSummary(Session session, MemoryRegenerateStatusDTO status,
                                     Integer startSeq, Integer endSeq, String prompt) {
        try {
            List<Message> messages = queryMessagesBySeqRange(session.getId(), startSeq, endSeq);
            if (messages.isEmpty()) {
                status.setStatus("FAILED");
                status.setError("序号区间内无消息");
                return;
            }
            ModelInvoker llmInvoker = resolveSessionLlmInvoker(session);
            if (llmInvoker == null) {
                status.setStatus("FAILED");
                status.setError("会话 LLM 模型不可用");
                return;
            }
            String content = extractAllContent(messages);
            if (content == null || content.isBlank()) {
                status.setStatus("FAILED");
                status.setError("序号区间内消息内容为空");
                return;
            }
            String systemPrompt = (prompt == null || prompt.isBlank())
                    ? SYSTEM_GROUP_SUMMARY_PROMPT
                    : prompt;
            String summary = invokeLlm(llmInvoker, systemPrompt, content);
            if (summary == null || summary.isBlank()) {
                status.setStatus("FAILED");
                status.setError("LLM 生成的聚合文本为空");
                return;
            }
            status.setAggregationText(summary);
            status.setStatus("COMPLETED");
        } catch (Exception e) {
            status.setStatus("FAILED");
            status.setError(e.getMessage());
            log.warn("聚合文本重生成失败, sessionId={}", session.getId(), e);
        }
    }

    /**
     * 每天凌晨 1 点执行：聚合所有 memoryEnabled=true 的智能体会话的新增消息为记忆文档。
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void aggregateSessionMemories() {
        List<Long> agentIds = queryMemoryEnabledAgentIds();
        if (agentIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getAgentId, agentIds)
                .and(w -> w.isNull(Session::getIsEvaluation).or().ne(Session::getIsEvaluation, true));
        List<Session> sessions = sessionMapper.selectList(sessionWrapper);
        if (sessions.isEmpty()) {
            return;
        }
        Map<Long, AgentConfig> agentMap = loadAgentMap(agentIds);
        for (Session session : sessions) {
            processSessionWithRetry(session, agentMap.get(session.getAgentId()));
        }
    }

    /**
     * 手动触发单个会话的记忆摘要聚合：校验会话与智能体存在且智能体已开启记忆功能后，异步执行聚合。
     *
     * @param sessionId 会话 ID
     */
    public void triggerSessionMemory(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        AgentConfig agentConfig = agentConfigMapper.selectById(session.getAgentId());
        if (agentConfig == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(agentConfig.getMemoryEnabled())) {
            throw new BusinessException(ErrorCode.AGENT_MEMORY_NOT_ENABLED);
        }
        ThreadVariableWrapper threadVariableWrapper = threadVariableHandler.wrap();
        CompletableFuture.runAsync(() -> {
            if (threadVariableWrapper != null) {
                threadVariableWrapper.apply();
            }
            try {
                processSessionWithRetry(session, agentConfig);
            } finally {
                UserContext.clear();
            }
        });
    }

    private void processSessionWithRetry(Session session, AgentConfig agentConfig) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                processSession(session, agentConfig);
                return;
            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_RETRY) {
                    log.warn("会话记忆聚合失败，准备第{}次重试, sessionId={}", attempt + 1, session.getId(), e);
                }
            }
        }
        log.error("会话记忆聚合失败，重试{}次后放弃, sessionId={}", MAX_RETRY, session.getId(), lastError);
    }

    private void processSession(Session session, AgentConfig agentConfig) {
        if (agentConfig == null) {
            log.warn("会话记忆聚合跳过：智能体不存在, sessionId={}, agentId={}", session.getId(), session.getAgentId());
            return;
        }
        if (!Boolean.TRUE.equals(agentConfig.getMemoryEnabled())) {
            return;
        }
        if (agentConfig.getVectorModelId() == null) {
            log.warn("会话记忆聚合跳过：未配置向量模型, sessionId={}", session.getId());
            return;
        }
        processGroupAggregation(session, agentConfig);
        processDailyAggregation(session, agentConfig);
    }

    private void processGroupAggregation(Session session, AgentConfig agentConfig) {
        Integer oldPoint = session.getMemoryPointSequenceNum();
        Integer newPoint = resolveNewMemoryPoint(session.getId(), agentConfig);
        if (newPoint == null) {
            return;
        }
        if (oldPoint != null && newPoint <= oldPoint) {
            return;
        }

        List<Message> newMessages = queryMessagesBetween(session.getId(), oldPoint, newPoint);
        if (newMessages.isEmpty()) {
            return;
        }

        ModelInvoker llmInvoker = resolveLlmInvoker(session, agentConfig);
        if (llmInvoker == null) {
            log.warn("会话记忆聚合跳过：会话 LLM 模型不可用, sessionId={}", session.getId());
            return;
        }
        ModelInvoker embedInvoker = resolveEmbedInvoker(agentConfig);
        if (embedInvoker == null) {
            log.warn("会话记忆聚合跳过：向量模型不可用, sessionId={}", session.getId());
            return;
        }

        List<SessionMemoryDocument> documents = buildMemoryDocuments(
                session.getId(), session.getUserId(), newMessages, llmInvoker, embedInvoker, agentConfig,
                AggregationType.GROUP, null, null);
        if (documents.isEmpty()) {
            return;
        }

        sessionMemoryESClient.batchSave(documents);

        session.setMemoryPointSequenceNum(newPoint);
        sessionMapper.updateById(session);
        Integer archivedEndSeq = documents.get(documents.size() - 1).getAggregationEndSeq();
        log.info("会话记忆聚合完成, sessionId={}, startSeq={}, endSeq={}, memoryPoint={}, memoryCount={}",
                session.getId(), oldPoint == null ? 1 : oldPoint + 1, archivedEndSeq, newPoint, documents.size());
    }

    private void processDailyAggregation(Session session, AgentConfig agentConfig) {
        LocalDate today = LocalDate.now();
        long endTime = toEpochMillis(today.atStartOfDay());
        long startTime = toEpochMillis(today.minusDays(1).atStartOfDay());

        if (sessionMemoryESClient.checkDailyExists(IdConverter.toString(session.getId()), startTime, endTime)) {
            log.info("会话按日记忆已存在，跳过聚合, sessionId={}", session.getId());
            return;
        }

        List<Message> dailyMessages = queryMessagesByTimeRange(session.getId(), startTime, endTime);
        if (dailyMessages.isEmpty()) {
            return;
        }

        ModelInvoker llmInvoker = resolveLlmInvoker(session, agentConfig);
        if (llmInvoker == null) {
            log.warn("会话按日记忆聚合跳过：会话 LLM 模型不可用, sessionId={}", session.getId());
            return;
        }
        ModelInvoker embedInvoker = resolveEmbedInvoker(agentConfig);
        if (embedInvoker == null) {
            log.warn("会话按日记忆聚合跳过：向量模型不可用, sessionId={}", session.getId());
            return;
        }

        SessionMemoryDocument document = buildDailyMemoryDocument(
                session.getId(), session.getUserId(), dailyMessages, llmInvoker, embedInvoker, agentConfig,
                startTime, endTime);
        if (document == null) {
            return;
        }

        sessionMemoryESClient.batchSave(List.of(document));
        log.info("会话按日记忆聚合完成, sessionId={}, startTime={}, endTime={}, memoryCount=1",
                session.getId(), startTime, endTime);
    }

    private List<Long> queryMemoryEnabledAgentIds() {
        LambdaQueryWrapper<AgentConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentConfig::getMemoryEnabled, true);
        return agentConfigMapper.selectList(wrapper).stream()
                .map(AgentConfig::getId)
                .toList();
    }

    private Map<Long, AgentConfig> loadAgentMap(List<Long> agentIds) {
        List<AgentConfig> agents = agentConfigMapper.selectBatchIds(agentIds);
        return agents.stream()
                .collect(Collectors.toMap(AgentConfig::getId, Function.identity(), (a, b) -> a));
    }

    private Integer resolveNewMemoryPoint(Long sessionId, AgentConfig agentConfig) {
        if (agentConfig.getMemoryGroupCount() == null || agentConfig.getMemoryGroupCount() <= 0) {
            return null;
        }
        Long totalGroups = messageMapper.countUserMessages(sessionId);
        if (totalGroups == null) {
            return null;
        }
        int skipGroups = totalGroups.intValue() - agentConfig.getMemoryGroupCount();
        if (skipGroups <= 0) {
            return null;
        }
        return messageMapper.findNthUserSequenceNum(sessionId, skipGroups);
    }

    private List<Message> queryMessagesBySeqRange(Long sessionId, Integer startSeq, Integer endSeq) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getRollback, false)
                .ge(Message::getSequenceNum, startSeq)
                .le(Message::getSequenceNum, endSeq)
                .orderByAsc(Message::getSequenceNum);
        return messageMapper.selectList(wrapper);
    }

    private List<Message> queryMessagesBetween(Long sessionId, Integer oldPoint, Integer newPoint) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getRollback, false)
                .lt(Message::getSequenceNum, newPoint);
        if (oldPoint != null) {
            wrapper.gt(Message::getSequenceNum, oldPoint);
        }
        wrapper.orderByAsc(Message::getSequenceNum);
        return messageMapper.selectList(wrapper);
    }

    private List<Message> queryMessagesByTimeRange(Long sessionId, long startTime, long endTime) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
                .eq(Message::getRollback, false)
                .ge(Message::getCreateTime, toLocalDateTime(startTime))
                .lt(Message::getCreateTime, toLocalDateTime(endTime))
                .orderByAsc(Message::getSequenceNum);
        return messageMapper.selectList(wrapper);
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private ModelInvoker resolveLlmInvoker(Session session, AgentConfig agentConfig) {
        Long modelId = session.getModelId() != null ? session.getModelId() : agentConfig.getModelId();
        if (modelId == null) {
            return null;
        }
        ModelConfig config = modelConfigMapper.selectById(modelId);
        if (config == null) {
            return null;
        }
        return modelInvokerManager.getInvoker(buildModelConfigData(config));
    }

    private ModelInvoker resolveSessionLlmInvoker(Session session) {
        Long modelId = session.getModelId();
        if (modelId == null) {
            return null;
        }
        ModelConfig config = modelConfigMapper.selectById(modelId);
        if (config == null) {
            return null;
        }
        return modelInvokerManager.getInvoker(buildModelConfigData(config));
    }

    private ModelInvoker resolveEmbedInvoker(AgentConfig agentConfig) {
        ModelConfig config = modelConfigMapper.selectById(agentConfig.getVectorModelId());
        if (config == null) {
            return null;
        }
        return modelInvokerManager.getInvoker(buildModelConfigData(config));
    }

    private ModelConfigData buildModelConfigData(ModelConfig config) {
        return new ModelConfigData(
                IdConverter.toString(config.getId()),
                config.getApiKey(),
                config.getBaseUrl(),
                config.getModelName(),
                config.getTemperature(),
                config.getMaxTokens(),
                config.getPlatformType() != null ? config.getPlatformType().name() : null,
                config.getRequestType()
        );
    }

    private List<SessionMemoryDocument> buildMemoryDocuments(Long sessionId, Long userId, List<Message> messages,
                                                             ModelInvoker llmInvoker, ModelInvoker embedInvoker,
                                                             AgentConfig agentConfig, AggregationType aggregationType,
                                                             Long windowStartTime, Long windowEndTime) {
        List<List<Message>> groups = groupByUser(messages);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<GroupSummary> groupSummaries = new ArrayList<>();
        for (List<Message> group : groups) {
            String content = extractGroupContent(group);
            if (content == null || content.isBlank()) {
                continue;
            }
            groupSummaries.add(new GroupSummary(group.get(0).getSequenceNum(),
                    group.get(group.size() - 1).getSequenceNum(),
                    toEpochMillis(group.get(0).getCreateTime()),
                    toEpochMillis(group.get(group.size() - 1).getCreateTime()),
                    content));
        }
        if (groupSummaries.isEmpty()) {
            return List.of();
        }

        List<String> topics = classifyTopics(llmInvoker, groupSummaries);
        List<List<GroupSummary>> topicGroups = mergeByTopic(groupSummaries, topics);
        boolean classificationFailed = topics == null || topics.size() != groupSummaries.size();

        List<SessionMemoryDocument> documents = new ArrayList<>();
        for (List<GroupSummary> topicGroup : topicGroups) {
            String summary = topicGroup.size() == 1 && classificationFailed
                    ? topicGroup.get(0).summary()
                    : summarizeTopicGroup(llmInvoker, topicGroup);
            if (summary == null || summary.isBlank()) {
                continue;
            }
            List<Float> vector = embedText(embedInvoker, agentConfig, summary);
            if (vector == null || vector.isEmpty()) {
                continue;
            }
            documents.add(SessionMemoryDocument.builder()
                    .sessionId(IdConverter.toString(sessionId))
                    .userId(userId)
                    .aggregationType(aggregationType)
                    .aggregationStartSeq(topicGroup.get(0).startSeq())
                    .aggregationEndSeq(topicGroup.get(topicGroup.size() - 1).endSeq())
                    .aggregationStartTime(windowStartTime != null ? windowStartTime : topicGroup.get(0).startTime())
                    .aggregationEndTime(windowEndTime != null ? windowEndTime : topicGroup.get(topicGroup.size() - 1).endTime())
                    .aggregationText(summary)
                    .vector(vector)
                    .build());
        }
        return documents;
    }

    private record GroupSummary(int startSeq, int endSeq, Long startTime, Long endTime, String summary) {}

    /**
     * 构建按日全量摘要文档：将当天全部非空消息内容拼接为全量文本，调用 LLM 生成单条摘要后向量化，
     * 生成唯一一个 aggregationType=DAILY 的 SessionMemoryDocument。
     *
     * @return 生成的文档，若全量文本/摘要/向量为空返回 null
     */
    private SessionMemoryDocument buildDailyMemoryDocument(Long sessionId, Long userId, List<Message> messages,
                                                           ModelInvoker llmInvoker, ModelInvoker embedInvoker,
                                                           AgentConfig agentConfig, long startTime, long endTime) {
        String fullContent = extractAllContent(messages);
        if (fullContent == null || fullContent.isBlank()) {
            return null;
        }
        String summary = summarizeDailyContent(llmInvoker, fullContent);
        if (summary == null || summary.isBlank()) {
            return null;
        }
        List<Float> vector = embedText(embedInvoker, agentConfig, summary);
        if (vector == null || vector.isEmpty()) {
            return null;
        }
        return SessionMemoryDocument.builder()
                .sessionId(IdConverter.toString(sessionId))
                .userId(userId)
                .aggregationType(AggregationType.DAILY)
                .aggregationStartSeq(messages.get(0).getSequenceNum())
                .aggregationEndSeq(messages.get(messages.size() - 1).getSequenceNum())
                .aggregationStartTime(startTime)
                .aggregationEndTime(endTime)
                .aggregationText(summary)
                .vector(vector)
                .build();
    }

    private String extractAllContent(List<Message> messages) {
        StringBuilder content = new StringBuilder();
        for (Message m : messages) {
            String text = m.getContent() != null ? m.getContent() : "";
            if (text.isBlank()) {
                continue;
            }
            content.append(text).append("\n");
        }
        return content.toString().trim();
    }

    private String summarizeDailyContent(ModelInvoker invoker, String fullContent) {
        return invokeLlm(invoker, SYSTEM_DAILY_SUMMARY_PROMPT, fullContent);
    }

    private List<List<Message>> groupByUser(List<Message> messages) {
        List<List<Message>> groups = new ArrayList<>();
        List<Message> current = null;
        for (Message m : messages) {
            if ("user".equals(m.getRole())) {
                current = new ArrayList<>();
                groups.add(current);
            }
            if (current != null) {
                current.add(m);
            }
        }
        return groups;
    }

    private String extractGroupContent(List<Message> group) {
        StringBuilder content = new StringBuilder();
        for (Message m : group) {
            String role = m.getRole() != null ? m.getRole() : "unknown";
            String text = m.getContent() != null ? m.getContent() : "";
            content.append("【").append(role).append("】: ").append(text).append("\n");
        }
        return content.toString().trim();
    }

    private Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private List<String> classifyTopics(ModelInvoker invoker, List<GroupSummary> groupSummaries) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < groupSummaries.size(); i++) {
            GroupSummary gs = groupSummaries.get(i);
            content.append(i + 1).append(". ").append(gs.summary()).append("\n");
        }
        String response = invokeLlm(invoker, SYSTEM_TOPIC_CLASSIFY_PROMPT, content.toString());
        if (response == null || response.isBlank()) {
            return List.of();
        }
        return parseTopics(response, groupSummaries.size());
    }

    private List<String> parseTopics(String response, int expectedCount) {
        Map<Integer, String> topicByIndex = new LinkedHashMap<>();
        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Matcher matcher = TOPIC_LINE_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String topic = matcher.group(2).trim();
            if (topic.isEmpty()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1));
            topicByIndex.put(index, topic);
        }
        if (topicByIndex.size() != expectedCount) {
            return List.of();
        }
        List<String> topics = new ArrayList<>(expectedCount);
        for (int i = 1; i <= expectedCount; i++) {
            String topic = topicByIndex.get(i);
            if (topic == null) {
                return List.of();
            }
            topics.add(topic);
        }
        return topics;
    }

    private List<List<GroupSummary>> mergeByTopic(List<GroupSummary> groupSummaries, List<String> topics) {
        if (topics == null || topics.size() != groupSummaries.size()) {
            return groupSummaries.stream().map(List::of).toList();
        }
        List<List<GroupSummary>> topicGroups = new ArrayList<>();
        List<GroupSummary> current = null;
        String currentTopic = null;
        for (int i = 0; i < groupSummaries.size(); i++) {
            String topic = topics.get(i);
            if (current == null || !topic.equals(currentTopic)) {
                current = new ArrayList<>();
                topicGroups.add(current);
                currentTopic = topic;
            }
            current.add(groupSummaries.get(i));
        }
        return topicGroups;
    }

    private String summarizeTopicGroup(ModelInvoker invoker, List<GroupSummary> topicGroup) {
        StringBuilder content = new StringBuilder();
        for (GroupSummary gs : topicGroup) {
            content.append("【概要】: ").append(gs.summary()).append("\n");
        }
        return invokeLlm(invoker, SYSTEM_GROUP_SUMMARY_PROMPT, content.toString());
    }

    private String invokeLlm(ModelInvoker invoker, String systemPrompt, String userContent) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        com.ghost616.agentbase.dto.model.Message.builder().role("system").content(systemPrompt).build(),
                        com.ghost616.agentbase.dto.model.Message.builder().role("user").content(userContent).build()))
                .build();
        ChatResponse response = invoker.invoke(request);
        return response != null ? response.getContent() : null;
    }

    private List<Float> embedText(ModelInvoker invoker, AgentConfig agentConfig, String text) {
        ModelConfig vectorModel = modelConfigMapper.selectById(agentConfig.getVectorModelId());
        if (vectorModel == null) {
            return null;
        }
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(vectorModel.getModelName())
                .input(text)
                .build();
        EmbeddingResponse response = invoker.embed(request);
        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            return null;
        }
        return response.getEmbeddings().get(0).getEmbedding();
    }
}
