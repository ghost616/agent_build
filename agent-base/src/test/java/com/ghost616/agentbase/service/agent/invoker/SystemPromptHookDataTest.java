package com.ghost616.agentbase.service.agent.invoker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ghost616.agentbase.dto.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SystemPromptHookData} 防御性深拷贝行为测试。
 */
class SystemPromptHookDataTest {

    private ToolDefinition buildTool(String name, String description) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("required", List.of("q"));
        return ToolDefinition.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
    }

    @Test
    void null输入_视为空列表且不为null() {
        SystemPromptHookData data = new SystemPromptHookData(null);
        assertNotNull(data.getToolDefinitions(), "getToolDefinitions 不应返回 null");
        assertTrue(data.getToolDefinitions().isEmpty(), "null 输入应视为空列表");
    }

    @Test
    void 列表复制_原列表后续修改不影响拷贝() {
        List<ToolDefinition> source = new ArrayList<>();
        source.add(buildTool("t1", "d1"));
        source.add(buildTool("t2", "d2"));

        SystemPromptHookData data = new SystemPromptHookData(source);

        source.clear();
        assertEquals(2, data.getToolDefinitions().size(), "原列表清空不应影响拷贝");
        assertEquals("t1", data.getToolDefinitions().get(0).getName());
        assertEquals("t2", data.getToolDefinitions().get(1).getName());
    }

    @Test
    void 单个元素复制_原ToolDefinition修改不影响拷贝() {
        ToolDefinition original = buildTool("t1", "d1");
        List<ToolDefinition> source = new ArrayList<>();
        source.add(original);

        SystemPromptHookData data = new SystemPromptHookData(source);
        ToolDefinition copied = data.getToolDefinitions().get(0);

        assertNotSame(original, copied, "单个 ToolDefinition 应为新实例（builder 重建）");
        original.setName("t1-modified");
        original.setDescription("d1-modified");
        assertEquals("t1", copied.getName(), "原对象 name 修改不应影响拷贝");
        assertEquals("d1", copied.getDescription(), "原对象 description 修改不应影响拷贝");
    }

    @Test
    void parametersMap拷贝_原Map修改不影响拷贝() {
        ToolDefinition original = buildTool("t1", "d1");
        List<ToolDefinition> source = new ArrayList<>();
        source.add(original);

        SystemPromptHookData data = new SystemPromptHookData(source);
        Map<String, Object> copiedParams = data.getToolDefinitions().get(0).getParameters();

        assertNotSame(original.getParameters(), copiedParams, "parameters Map 应为新 HashMap 实例");
        assertNotNull(copiedParams);
        original.getParameters().put("extra", "value");
        assertEquals(2, copiedParams.size(), "原 Map 新增键不应影响拷贝");
        assertFalse(copiedParams.containsKey("extra"));
        assertEquals("object", copiedParams.get("type"), "拷贝内容应与原 Map 一致");
    }

    @Test
    void 返回列表不可变() {
        List<ToolDefinition> source = new ArrayList<>();
        source.add(buildTool("t1", "d1"));

        SystemPromptHookData data = new SystemPromptHookData(source);
        assertThrows(UnsupportedOperationException.class,
                () -> data.getToolDefinitions().add(buildTool("t2", "d2")),
                "返回列表应为不可变副本");
    }

    @Test
    void SystemPromptHookResult_getSystemPrompt返回构造值() {
        SystemPromptHookResult result = new SystemPromptHookResult("PROMPT_TEXT");
        assertEquals("PROMPT_TEXT", result.getSystemPrompt());
        assertNull(new SystemPromptHookResult(null).getSystemPrompt());
    }
}