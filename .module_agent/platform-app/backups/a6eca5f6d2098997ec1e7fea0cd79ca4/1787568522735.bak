package com.ghost616.platform.service.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.agentbase.dto.model.EmbeddingRequest;
import com.ghost616.agentbase.dto.model.EmbeddingResponse;
import com.ghost616.agentbase.dto.model.ModelConfigData;
import com.ghost616.agentbase.service.model.invoker.ModelInvoker;
import com.ghost616.agentbase.service.model.invoker.ModelInvokerManager;
import com.ghost616.agentinteg.knowledge.SearchType;
import com.ghost616.agentinteg.memory.MemoryQueryProvider;
import com.ghost616.agentinteg.memory.MemoryResult;
import com.ghost616.agentinteg.memory.MessageSeqByRole;
import com.ghost616.agentinteg.memory.SeqRange;
import com.ghost616.platform.entity.AgentConfig;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.ModelConfig;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.model.SessionMemoryDocument;
import com.ghost616.platform.repository.AgentConfigMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.ModelConfigMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.service.search.SessionMemoryESClient;
import com.ghost616.platform.session.UserContextUtil;
import com.ghost616.platform.util.IdConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 记忆查询 Provider 实现，基于 platform-app 的持久化组件与 session_memory 索引提供记忆查询能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryQueryProviderImpl implements MemoryQueryProvider {

    private static final int DEFAULT_TOP_K = 20;

    private final SessionMemoryESClient sessionMemoryESClient;
    private final MessageMapper messageMapper;
    private final SessionMapper sessionMapper;
    private final AgentConfigMapper agentConfigMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ModelInvokerManager modelInvokerManager;

    @Override
    public List<MemoryResult> getMemories(String sessionId, SearchType searchType, String memoryType,
                                          Long startTime, Long endTime, String query) {
        if (sessionId == null || sessionId.isBlank() || searchType == null) {
            return List.of();
        }
        Long userId = UserContextUtil.currentUserIdOrNull();
        String aggregationType = (memoryType == null || memoryType.isBlank()) ? null : memoryType;
        List<SessionMemoryDocument> documents;
        switch (searchType) {
            case VECTOR:
                documents = sessionMemoryESClient.vectorSearch(sessionId, userId, aggregationType, startTime, endTime,
                        embedQuery(sessionId, query), DEFAULT_TOP_K);
                break;
            case FULLTEXT:
                documents = sessionMemoryESClient.fullTextSearch(sessionId, userId, aggregationType, startTime, endTime,
                        query, DEFAULT_TOP_K);
                break;
            case HYBRID:
                documents = sessionMemoryESClient.hybridSearch(sessionId, userId, aggregationType, startTime, endTime,
                        embedQuery(sessionId, query), query, DEFAULT_TOP_K);
                break;
            default:
                return List.of();
        }
        return documents.stream()
                .map(doc -> new MemoryResult(doc.getAggregationText(),
                        doc.getAggregationStartSeq() == null ? 0 : doc.getAggregationStartSeq(),
                        doc.getAggregationEndSeq() == null ? 0 : doc.getAggregationEndSeq(),
                        doc.getAggregationType() != null ? doc.getAggregationType().getCode() : null))
                .toList();
    }

    @Override
    public MessageSeqByRole getMessageSeqsByRole(String sessionId, List<SeqRange> ranges) {
        Long sid = IdConverter.parse(sessionId);
        if (sid == null || ranges == null || ranges.isEmpty()) {
            return new MessageSeqByRole(List.of(), List.of(), List.of());
        }
        Long userId = UserContextUtil.currentUserIdOrNull();
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sid)
                .eq(Message::getRollback, false);
        if (userId != null) {
            wrapper.eq(Message::getUserId, userId);
        }
        wrapper.and(w -> {
            for (int i = 0; i < ranges.size(); i++) {
                SeqRange range = ranges.get(i);
                if (i > 0) {
                    w.or();
                }
                w.ge(Message::getSequenceNum, range.startSeq())
                        .le(Message::getSequenceNum, range.endSeq());
            }
        })
        .orderByAsc(Message::getSequenceNum);
        List<Message> messages = messageMapper.selectList(wrapper);
        Set<Integer> userSeqs = new LinkedHashSet<>();
        Set<Integer> toolSeqs = new LinkedHashSet<>();
        Set<Integer> assistantSeqs = new LinkedHashSet<>();
        for (Message message : messages) {
            if (message.getSequenceNum() == null) {
                continue;
            }
            String role = message.getRole();
            if ("user".equals(role)) {
                userSeqs.add(message.getSequenceNum());
            } else if ("tool".equals(role)) {
                toolSeqs.add(message.getSequenceNum());
            } else if ("assistant".equals(role)) {
                assistantSeqs.add(message.getSequenceNum());
            }
        }
        return new MessageSeqByRole(new ArrayList<>(userSeqs), new ArrayList<>(toolSeqs),
                new ArrayList<>(assistantSeqs));
    }

    private List<Float> embedQuery(String sessionId, String query) {
        Long sid = IdConverter.parse(sessionId);
        if (sid == null) {
            return List.of();
        }
        Session session = sessionMapper.selectById(sid);
        if (session == null || session.getAgentId() == null) {
            return List.of();
        }
        AgentConfig agentConfig = agentConfigMapper.selectById(session.getAgentId());
        if (agentConfig == null || agentConfig.getVectorModelId() == null) {
            return List.of();
        }
        ModelConfig config = modelConfigMapper.selectById(agentConfig.getVectorModelId());
        if (config == null) {
            return List.of();
        }
        ModelInvoker invoker = modelInvokerManager.getInvoker(buildModelConfigData(config));
        EmbeddingResponse response = invoker.embed(EmbeddingRequest.builder()
                .model(config.getModelName())
                .inputList(List.of(query))
                .build());
        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            return List.of();
        }
        EmbeddingResponse.EmbeddingItem item = response.getEmbeddings().get(0);
        return item == null || item.getEmbedding() == null ? List.of() : item.getEmbedding();
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
}
