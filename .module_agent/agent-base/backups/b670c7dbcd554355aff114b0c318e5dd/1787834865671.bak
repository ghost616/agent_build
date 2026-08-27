package com.ghost616.agentbase.enums;

/**
 * HOOK 触发生命周期阶段枚举。
 */
public enum HookPhase {

    SESSION_START("会话开始"),
    SESSION_END("会话结束"),
    BEFORE_MESSAGE_SEND("消息发送前"),
    AFTER_MESSAGE_RECEIVE("消息接收后"),
    BEFORE_TOOL_CALL("工具调用前"),
    AFTER_TOOL_CALL("工具调用后");

    private final String description;

    HookPhase(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
