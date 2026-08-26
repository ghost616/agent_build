package com.ghost616.agentinteg.subsession;

/**
 * 子会话结果回传 Provider 接口，由外部模块（platform-app）提供实现。
 *
 * <p>用于判断指定子会话是否需要向父会话兜底回传执行结果。实现方应综合判定
 * 会话是否确为子会话、其所属主会话的子会话打开方式（如 WEBSOCKET）以及
 * 结果是否已通过其他通道（如前台工具调用）回传等条件。</p>
 */
public interface SubSessionResultProvider {

    /**
     * 判断指定子会话是否需要向父会话发送执行结果。
     *
     * @param sessionId 子会话 ID
     * @return true 表示需要向父会话兜底回传执行结果，false 表示无需回传
     */
    boolean shouldSendResultToParent(String sessionId);
}
