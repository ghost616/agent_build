package com.ghost616.agentbase.service.agent.invoker;

import java.util.ArrayList;
import java.util.List;

import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolDefinitionsHookResult} 与 {@link ToolDefinitionsHookData} 基本用例测试。
 */
class ToolDefinitionsHookDataTest {

    @Test
    void ToolDefinitionsHookResult_getTools返回构造列表() {
        List<ToolConfigDTO> tools = List.of(
                ToolConfigDTO.builder().name("t1").build(),
                ToolConfigDTO.builder().name("t2").build());
        ToolDefinitionsHookResult result = new ToolDefinitionsHookResult(tools);

        assertSame(tools, result.getTools(), "getTools 应返回构造时传入的列表");
        assertEquals(2, result.getTools().size());
    }

    @Test
    void ToolDefinitionsHookResult_null构造getTools返回null() {
        ToolDefinitionsHookResult result = new ToolDefinitionsHookResult(null);
        assertNull(result.getTools(), "null 构造时 getTools 应返回 null（调用方需 null 安全）");
    }

    @Test
    void ToolDefinitionsHookData_getTools返回构造列表() {
        List<ToolConfigDTO> tools = new ArrayList<>();
        tools.add(ToolConfigDTO.builder().name("t1").build());
        ToolDefinitionsHookData data = new ToolDefinitionsHookData(tools);

        assertSame(tools, data.getTools(), "按现有 HookData 风格直接持有引用");
        assertEquals("t1", data.getTools().get(0).getName());
    }

    @Test
    void ToolDefinitionsHookData_null构造getTools返回null() {
        ToolDefinitionsHookData data = new ToolDefinitionsHookData(null);
        assertNull(data.getTools());
    }
}