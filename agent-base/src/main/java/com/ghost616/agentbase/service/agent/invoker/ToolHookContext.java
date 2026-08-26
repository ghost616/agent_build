package com.ghost616.agentbase.service.agent.invoker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用上下文数据载体，实现 {@link HookData} 空结果类型。
 *
 * <p>封装工具调用的标识、名称、参数与执行结果，作为工具阶段
 * HOOK（BEFORE_TOOL_CALL / AFTER_TOOL_CALL）的数据载体。</p>
 *
 * @author ghost616
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolHookContext implements HookData<EmptyHookResult> {

    private String toolCallId;
    private String toolName;
    private String arguments;
    private String result;
}
