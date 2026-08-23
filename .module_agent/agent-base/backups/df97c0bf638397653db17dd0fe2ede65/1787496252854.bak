package com.ghost616.agentbase.service.agent;

import java.time.LocalDateTime;
import java.util.List;

import com.ghost616.agentbase.dto.model.ImageContent;
import com.ghost616.agentbase.dto.model.ToolInfo;
import com.ghost616.agentbase.dto.model.UsageInfo;

public interface MessageDataProvider {

    String saveMessage(String sessionId, String role, String content, String reasoning,
                       ToolInfo toolInfo, String toolResult, List<ToolCallData> toolCalls,
                       UsageInfo usage, List<WebSearchCallData> webSearchCall, List<CustomToolCallData> customToolCall,
                       String conversationId, List<ImageContent> images);

    List<MessageDTO> getMessages(String sessionId);

    int rollbackToLastUserMessage(String sessionId);

    record ToolCallData(String toolCallId, String toolCallName, String toolCallArguments, String type) {

        public ToolCallData(String toolCallId, String toolCallName, String toolCallArguments) {
            this(toolCallId, toolCallName, toolCallArguments, "function");
        }
    }

    record WebSearchCallData(String itemId, Integer outputIndex, List<WebSearchResultData> results) {
    }

    record WebSearchResultData(String title, String url, String snippet) {
    }

    record CustomToolCallData(String itemId, Integer outputIndex, String input, String output) {
    }

    record MessageDTO(String id, String sessionId, String role, String content, String reasoning,
                      ToolInfo toolInfo, LocalDateTime createTime,
                      String toolResult, List<ToolCallData> toolCalls, UsageInfo usage,
                      Boolean rollback, List<WebSearchCallData> webSearchCall, List<CustomToolCallData> customToolCall,
                      String conversationId, List<ImageContent> images) {
    }
}
