package com.ghost616.agentbase.dto.chat;

import java.util.List;

import com.ghost616.agentbase.dto.model.ImageContent;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    @NotBlank(message = "content不能为空")
    private String content;

    private String modelId;

    private Boolean thinking;

    /** 上一轮响应的 ID（Responses API 多轮续接时透传给模型请求） */
    private String previousResponseId;

    /** 对话 ID（非必填，父会话发起聊天时用于标记对话归属） */
    private String conversationId;

    /** 请求级图片列表（图片对象数组；imgId 仅供前端关联，不传给模型。控制器暂不暴露） */
    private List<ImageContent> images;
}
