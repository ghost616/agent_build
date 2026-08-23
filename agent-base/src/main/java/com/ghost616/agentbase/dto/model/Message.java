package com.ghost616.agentbase.dto.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /** 消息角色（system/user/assistant/tool） */
    private String role;

    /** 消息内容 */
    private String content;

    /** 工具调用列表（assistant 消息中的 tool_calls） */
    private List<ToolCall> toolCalls;

    /** 工具调用信息（tool 角色消息回传用） */
    private ToolInfo toolInfo;

    /** 推理/思考内容（DeepSeek thinking 模式等） */
    private String reasoning;

    /** 图片列表（消息级，图片对象数组；imgId 仅供前端关联，不传给模型） */
    private List<ImageContent> images;
}
