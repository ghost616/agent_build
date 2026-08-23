package com.ghost616.platform.dto.session;

import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;
import com.ghost616.agentbase.service.agent.MessageDataProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话消息 API 传输对象。
 * 字段与 agent-base MessageDataProvider.MessageDTO 保持一致，并保留 sequenceNum，
 * 供 Controller 层对外返回消息列表使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessageDTO {

    private String id;

    private String sessionId;

    private String role;

    private String content;

    private String reasoning;

    private ToolInfo toolInfo;

    private Integer sequenceNum;

    private LocalDateTime createTime;

    private String toolResult;

    private List<MessageDataProvider.ToolCallData> toolCalls;

    private UsageInfo usage;

    private Boolean rollback;

    private List<MessageDataProvider.WebSearchCallData> webSearchCall;

    private List<MessageDataProvider.CustomToolCallData> customToolCall;

    private String conversationId;
}