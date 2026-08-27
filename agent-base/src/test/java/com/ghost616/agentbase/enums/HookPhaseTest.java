package com.ghost616.agentbase.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HookPhaseTest {

    @Test
    void values应包含AFTER_PRE_SYSTEM_PROMPT_BUILD() {
        assertTrue(java.util.Arrays.stream(HookPhase.values())
                        .anyMatch(p -> p == HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD),
                "HookPhase 应包含 AFTER_PRE_SYSTEM_PROMPT_BUILD 枚举值");
    }

    @Test
    void AFTER_PRE_SYSTEM_PROMPT_BUILD可正常取值() {
        assertEquals("AFTER_PRE_SYSTEM_PROMPT_BUILD", HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD.name(),
                "枚举名应为 AFTER_PRE_SYSTEM_PROMPT_BUILD");
        assertEquals("前置系统提示词构建后", HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD.getDescription(),
                "枚举描述应为「前置系统提示词构建后」");
        assertSame(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD,
                HookPhase.valueOf("AFTER_PRE_SYSTEM_PROMPT_BUILD"),
                "valueOf 应按名称取回同一枚举实例");
    }

    @Test
    void values应包含BEFORE_TOOL_DEFINITIONS_BUILD() {
        assertTrue(java.util.Arrays.stream(HookPhase.values())
                        .anyMatch(p -> p == HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD),
                "HookPhase 应包含 BEFORE_TOOL_DEFINITIONS_BUILD 枚举值");
    }

    @Test
    void BEFORE_TOOL_DEFINITIONS_BUILD可正常取值() {
        assertEquals("BEFORE_TOOL_DEFINITIONS_BUILD", HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD.name(),
                "枚举名应为 BEFORE_TOOL_DEFINITIONS_BUILD");
        assertEquals("工具定义构建前", HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD.getDescription(),
                "枚举描述应为「工具定义构建前」");
        assertSame(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD,
                HookPhase.valueOf("BEFORE_TOOL_DEFINITIONS_BUILD"),
                "valueOf 应按名称取回同一枚举实例");
    }
}