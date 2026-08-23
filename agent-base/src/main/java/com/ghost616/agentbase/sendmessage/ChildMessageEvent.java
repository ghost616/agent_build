package com.ghost616.agentbase.sendmessage;

import java.util.List;

import com.ghost616.agentbase.dto.model.ImageContent;
import lombok.Getter;

@Getter
public class ChildMessageEvent extends SessionMessage {

    private final String childSessionId;
    private final String content;
    private final String modelId;
    private final Boolean thinking;

    /** 图片列表（图片对象数组；imgId 仅供前端关联，不传给模型） */
    private final List<ImageContent> images;

    public ChildMessageEvent(String sessionId, String childSessionId, String content, String modelId,
                             Boolean thinking, List<ImageContent> images) {
        setSessionId(sessionId);
        this.childSessionId = childSessionId;
        this.content = content;
        this.modelId = modelId;
        this.thinking = thinking;
        this.images = images;
    }

    @Override
    public String getMessageName() {
        return MessageName.CHILD_MESSAGE;
    }
}
