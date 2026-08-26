package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.dto.model.ChatChunk;
import lombok.Data;

/**
 * {@link ChatChunk} 数据载体，实现 {@link HookData} 空结果类型。
 *
 * <p>替代原 {@code new HookData(ChatChunk)} 用法，封装流式聊天块供 HOOK 消费。</p>
 *
 * @author ghost616
 */
@Data
public class ChatChunkHookData implements HookData<EmptyHookResult> {

    private final ChatChunk chatChunk;

    public ChatChunkHookData(ChatChunk chatChunk) {
        this.chatChunk = chatChunk;
    }

    /**
     * 获取封装的聊天块。
     *
     * @return 聊天块，可为 null
     */
    public ChatChunk getChatChunk() {
        return chatChunk;
    }
}
