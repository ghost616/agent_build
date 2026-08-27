package com.ghost616.agentbase.service.agent.invoker;

/**
 * 前置系统提示词构建 HOOK 执行结果。
 *
 * <p>由 {@link HookPhase#AFTER_PRE_SYSTEM_PROMPT_BUILD} 阶段的 HOOK 返回，
 * 携带一段额外的前置系统提示词文本，供 ChatService 注入到系统消息链
 * （chatViaChatCompletions 构建 role=system 消息；Responses 系列拼入 instructions）。</p>
 *
 * @author ghost616
 */
public class SystemPromptHookResult implements HookResult {

    private final String systemPrompt;

    public SystemPromptHookResult(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    /**
     * 获取 HOOK 提供的系统提示词文本。
     *
     * @return 系统提示词文本
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
}