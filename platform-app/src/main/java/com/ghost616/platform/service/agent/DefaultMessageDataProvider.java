package com.ghost616.platform.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.platform.enums.ErrorCode;
import com.ghost616.platform.dto.session.SessionMessageDTO;
import com.ghost616.platform.exception.BusinessException;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import com.ghost616.agentbase.service.agent.MessageDataProvider.CustomToolCallData;
import com.ghost616.agentbase.service.agent.MessageDataProvider.MessageDTO;
import com.ghost616.agentbase.service.agent.MessageDataProvider.ToolCallData;
import com.ghost616.agentbase.service.agent.MessageDataProvider.WebSearchCallData;
import com.ghost616.agentbase.util.JsonMapper;
import com.ghost616.platform.entity.Message;
import com.ghost616.platform.entity.MessageImage;
import com.ghost616.platform.entity.MessageToolCall;
import com.ghost616.platform.entity.Session;
import com.ghost616.platform.repository.MessageImageMapper;
import com.ghost616.platform.repository.MessageMapper;
import com.ghost616.platform.repository.MessageToolCallMapper;
import com.ghost616.platform.repository.SessionMapper;
import com.ghost616.platform.session.UserContextUtil;
import com.ghost616.platform.util.IdConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMessageDataProvider implements MessageDataProvider {

    private static final long MEMORY_POINT_CACHE_TTL_MILLIS = 60_000L;
    private static final int MEMORY_POINT_CACHE_MAX_SIZE = 2000;

    private final ConcurrentHashMap<Long, MemoryPointCacheEntry> memoryPointCache = new ConcurrentHashMap<>();

    private final MessageMapper messageMapper;
    private final MessageToolCallMapper messageToolCallMapper;
    private final MessageToolCallService messageToolCallService;
    private final SessionMapper sessionMapper;
    private final MessageImageMapper messageImageMapper;

    private static final class MemoryPointCacheEntry {
        private final Integer memoryPointSequenceNum;
        private long expireAt;

        private MemoryPointCacheEntry(Integer memoryPointSequenceNum, long expireAt) {
            this.memoryPointSequenceNum = memoryPointSequenceNum;
            this.expireAt = expireAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    @Override
    public String saveMessage(String sessionId, String role, String content, String reasoning,
                               ToolInfo toolInfo, String toolResult, List<ToolCallData> toolCalls,
                               UsageInfo usage, List<WebSearchCallData> webSearchCall, List<CustomToolCallData> customToolCall,
                               String conversationId, List<ImageContent> images) {
        Long sid = IdConverter.parse(sessionId);
        Message message = new Message();
        message.setSessionId(sid);
        message.setUserId(UserContextUtil.currentUserIdOrNull());
        message.setRole(role);
        message.setContent(content);
        message.setReasoning(reasoning);
        message.setToolCallId(toolInfo != null ? toolInfo.toolCallId() : null);
        message.setToolResult(toolResult);
        message.setConversationId(conversationId);

        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sid)
                .eq(Message::getRollback, false)
                .orderByDesc(Message::getSequenceNum)
                .last("LIMIT 1");
        Message lastMessage = messageMapper.selectOne(wrapper);
        int sequenceNum = (lastMessage != null) ? lastMessage.getSequenceNum() + 1 : 1;
        message.setSequenceNum(sequenceNum);

        if (usage != null) {
            try {
                String jsonStr = JsonMapper.MAPPER.writeValueAsString(usage);
                message.setTokenUsage(jsonStr);
            } catch (Exception e) {
                log.warn("序列化 UsageInfo 失败", e);
            }
            long addTokens = 0;
            if (usage.getTotalTokens() != null) {
                addTokens = usage.getTotalTokens().longValue();
            } else {
                addTokens = (usage.getPromptTokens() != null ? usage.getPromptTokens().longValue() : 0L)
                          + (usage.getCompletionTokens() != null ? usage.getCompletionTokens().longValue() : 0L);
            }
            if (addTokens > 0) {
                sessionMapper.addTotalTokenUsed(sid, addTokens);
            }
        }

        messageMapper.insert(message);
        Long messageId = message.getId();

        List<MessageToolCall> batchToolCalls = new ArrayList<>();
        if (toolInfo != null && toolInfo.toolCallId() != null && !toolInfo.toolCallId().isEmpty()) {
            MessageToolCall mtc = new MessageToolCall();
            mtc.setMessageId(messageId);
            mtc.setToolCallId(toolInfo.toolCallId());
            mtc.setToolCallName(toolInfo.toolName());
            mtc.setType("tool_result");
            batchToolCalls.add(mtc);
        }
        if (toolCalls != null) {
            for (ToolCallData tc : toolCalls) {
                MessageToolCall mtc = new MessageToolCall();
                mtc.setMessageId(messageId);
                mtc.setToolCallId(tc.toolCallId());
                mtc.setToolCallName(tc.toolCallName());
                mtc.setToolCallArguments(tc.toolCallArguments());
                mtc.setType(tc.type());
                batchToolCalls.add(mtc);
            }
        }
        if (webSearchCall != null) {
            for (WebSearchCallData data : webSearchCall) {
                String json = toJson(data);
                if (json != null) {
                    MessageToolCall mtc = new MessageToolCall();
                    mtc.setMessageId(messageId);
                    mtc.setType("web_search_call");
                    mtc.setWebSearchCall(json);
                    batchToolCalls.add(mtc);
                }
            }
        }
        if (customToolCall != null) {
            for (CustomToolCallData data : customToolCall) {
                String json = toJson(data);
                if (json != null) {
                    MessageToolCall mtc = new MessageToolCall();
                    mtc.setMessageId(messageId);
                    mtc.setType("custom_tool_call");
                    mtc.setCustomToolCall(json);
                    batchToolCalls.add(mtc);
                }
            }
        }
        if (!batchToolCalls.isEmpty()) {
            messageToolCallService.saveBatch(batchToolCalls);
        }

        if (images != null && !images.isEmpty()) {
            for (ImageContent img : images) {
                MessageImage messageImage = new MessageImage();
                messageImage.setMessageId(messageId);
                messageImage.setImgId(img.getImgId());
                messageImage.setImgText(img.getImgText());
                messageImageMapper.insert(messageImage);
            }
        }

        return IdConverter.toString(messageId);
    }

    @Override
    public List<MessageDTO> getMessages(String sessionId) {
        Long sid = IdConverter.parse(sessionId);
        Integer memoryPoint = resolveMemoryPoint(sid);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sid)
                .eq(Message::getRollback, false);
        if (memoryPoint != null) {
            wrapper.ge(Message::getSequenceNum, memoryPoint);
        }
        wrapper.orderByAsc(Message::getSequenceNum);
        List<Message> messages = messageMapper.selectList(wrapper);
        return toMessageDTOs(messages);
    }

    private Integer resolveMemoryPoint(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        MemoryPointCacheEntry entry = memoryPointCache.get(sessionId);
        if (entry != null && !entry.isExpired()) {
            return entry.memoryPointSequenceNum;
        }
        Session session = sessionMapper.selectById(sessionId);
        Integer memoryPoint = session != null ? session.getMemoryPointSequenceNum() : null;
        putMemoryPointCache(sessionId, memoryPoint);
        return memoryPoint;
    }

    private void putMemoryPointCache(Long sessionId, Integer memoryPointSequenceNum) {
        if (memoryPointCache.size() >= MEMORY_POINT_CACHE_MAX_SIZE) {
            memoryPointCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
        memoryPointCache.put(sessionId, new MemoryPointCacheEntry(memoryPointSequenceNum,
                System.currentTimeMillis() + MEMORY_POINT_CACHE_TTL_MILLIS));
    }

    public List<MessageDTO> toMessageDTOs(List<Message> messages) {
        List<MessageDTO> result = new ArrayList<>();
        for (Message msg : messages) {
            LambdaQueryWrapper<MessageToolCall> tcWrapper = new LambdaQueryWrapper<>();
            tcWrapper.eq(MessageToolCall::getMessageId, msg.getId());
            List<MessageToolCall> toolCalls = messageToolCallMapper.selectList(tcWrapper);

            List<ToolCallData> toolCallDataList = new ArrayList<>();
            List<WebSearchCallData> webSearchCallDataList = null;
            List<CustomToolCallData> customToolCallDataList = null;
            for (MessageToolCall tc : toolCalls) {
                String type = tc.getType() != null ? tc.getType() : "function";
                String webJson = tc.getWebSearchCall();
                String customJson = tc.getCustomToolCall();
                if ("web_search_call".equals(type) && webJson != null && !webJson.isEmpty()) {
                    WebSearchCallData data = deserializeWebSearchCall(webJson);
                    if (data != null) {
                        if (webSearchCallDataList == null) {
                            webSearchCallDataList = new ArrayList<>();
                        }
                        webSearchCallDataList.add(data);
                    }
                } else if ("custom_tool_call".equals(type) && customJson != null && !customJson.isEmpty()) {
                    CustomToolCallData data = deserializeCustomToolCall(customJson);
                    if (data != null) {
                        if (customToolCallDataList == null) {
                            customToolCallDataList = new ArrayList<>();
                        }
                        customToolCallDataList.add(data);
                    }
                } else if ("tool_result".equals(type)) {
                    continue;
                } else {
                    toolCallDataList.add(new ToolCallData(tc.getToolCallId(), tc.getToolCallName(),
                            tc.getToolCallArguments(), type));
                }
            }

            UsageInfo usageInfo = null;
            String tokenUsageStr = msg.getTokenUsage();
            if (tokenUsageStr != null && !tokenUsageStr.isEmpty()) {
                try {
                    usageInfo = JsonMapper.MAPPER.readValue(tokenUsageStr, UsageInfo.class);
                } catch (Exception e) {
                    log.warn("反序列化 tokenUsage 失败", e);
                }
            }

            List<ImageContent> images = null;
            LambdaQueryWrapper<MessageImage> imgWrapper = new LambdaQueryWrapper<>();
            imgWrapper.eq(MessageImage::getMessageId, msg.getId());
            List<MessageImage> imageRows = messageImageMapper.selectList(imgWrapper);
            if (imageRows != null && !imageRows.isEmpty()) {
                images = imageRows.stream()
                        .map(mi -> ImageContent.builder()
                                .imgId(mi.getImgId())
                                .imgText(mi.getImgText())
                                .build())
                        .collect(Collectors.toList());
            }

            result.add(new MessageDTO(
                    IdConverter.toString(msg.getId()), IdConverter.toString(msg.getSessionId()), msg.getRole(), msg.getContent(),
                    msg.getReasoning(), buildToolInfo(msg, toolCalls),
                    msg.getCreateTime(), msg.getToolResult(), toolCallDataList, usageInfo,
                    msg.getRollback(), webSearchCallDataList, customToolCallDataList, msg.getConversationId(),
                    images));
        }

        return result;
    }

    /**
     * API 层消息映射：将消息实体转换为对外返回的 SessionMessageDTO 列表。
     * 复用内部 {@link #toMessageDTOs} 的实体解析逻辑（含工具调用/用量等），
     * 再补充 sequenceNum（内部 MessageDTO 不含该字段，从实体读取）。
     *
     * @param messages 消息实体列表
     * @return SessionMessageDTO 列表
     */
    public List<SessionMessageDTO> toSessionMessageDTOs(List<Message> messages) {
        List<MessageDTO> dtos = toMessageDTOs(messages);
        List<SessionMessageDTO> result = new ArrayList<>(dtos.size());
        for (int i = 0; i < dtos.size(); i++) {
            result.add(toSessionMessageDTO(dtos.get(i), messages.get(i).getSequenceNum()));
        }
        return result;
    }

    /**
     * 将内部 MessageDTO 映射为 API 层 SessionMessageDTO（字段一致，补充 sequenceNum）。
     *
     * @param dto          内部 MessageDTO
     * @param sequenceNum  消息序号（来自消息实体）
     * @return SessionMessageDTO
     */
    public SessionMessageDTO toSessionMessageDTO(MessageDTO dto, Integer sequenceNum) {
        return SessionMessageDTO.builder()
                .id(dto.id())
                .sessionId(dto.sessionId())
                .role(dto.role())
                .content(dto.content())
                .reasoning(dto.reasoning())
                .toolInfo(dto.toolInfo())
                .sequenceNum(sequenceNum)
                .createTime(dto.createTime())
                .toolResult(dto.toolResult())
                .toolCalls(dto.toolCalls())
                .usage(dto.usage())
                .rollback(dto.rollback())
                .webSearchCall(dto.webSearchCall())
                .customToolCall(dto.customToolCall())
                .conversationId(dto.conversationId())
                .images(dto.images())
                .build();
    }

    @Override
    public int rollbackToLastUserMessage(String sessionId) {
        Long sid = IdConverter.parse(sessionId);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sid)
                .eq(Message::getRollback, false)
                .eq(Message::getRole, "user")
                .orderByDesc(Message::getSequenceNum)
                .last("LIMIT 1");
        Message lastUserMessage = messageMapper.selectOne(wrapper);
        if (lastUserMessage == null) {
            throw new BusinessException(ErrorCode.SESSION_NO_USER_MESSAGE);
        }

        Integer sequenceNum = lastUserMessage.getSequenceNum();

        LambdaQueryWrapper<Message> idWrapper = new LambdaQueryWrapper<>();
        idWrapper.eq(Message::getSessionId, sid)
                .eq(Message::getRollback, false)
                .ge(Message::getSequenceNum, sequenceNum);
        List<Message> messagesToDelete = messageMapper.selectList(idWrapper);
        List<Long> messageIds = messagesToDelete.stream()
                .map(Message::getId)
                .collect(Collectors.toList());

        if (!messageIds.isEmpty()) {
            messageToolCallMapper.deleteByMessageIds(messageIds);
            messageImageMapper.delete(new LambdaQueryWrapper<MessageImage>()
                    .in(MessageImage::getMessageId, messageIds));
        }

        return messageMapper.rollbackBySessionIdAndGeSequenceNum(sid, sequenceNum);
    }

    private String toJson(Object obj) {
        try {
            return JsonMapper.MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("序列化 webSearchCall/customToolCall 失败", e);
            return null;
        }
    }

    private WebSearchCallData deserializeWebSearchCall(String json) {
        try {
            return JsonMapper.MAPPER.readValue(json, WebSearchCallData.class);
        } catch (Exception e) {
            log.warn("反序列化 webSearchCall 失败", e);
            return null;
        }
    }

    private CustomToolCallData deserializeCustomToolCall(String json) {
        try {
            return JsonMapper.MAPPER.readValue(json, CustomToolCallData.class);
        } catch (Exception e) {
            log.warn("反序列化 customToolCall 失败", e);
            return null;
        }
    }

    private ToolInfo buildToolInfo(Message msg, List<MessageToolCall> toolCalls) {
        String toolCallId = msg.getToolCallId();
        if (toolCallId == null || toolCallId.isEmpty()) {
            return null;
        }
        for (MessageToolCall tc : toolCalls) {
            if (toolCallId.equals(tc.getToolCallId())) {
                return new ToolInfo(tc.getToolCallId(), tc.getToolCallName());
            }
        }
        return new ToolInfo(toolCallId, null);
    }
}