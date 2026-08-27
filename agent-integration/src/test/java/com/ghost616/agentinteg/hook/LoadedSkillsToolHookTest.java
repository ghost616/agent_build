package com.ghost616.agentinteg.hook;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.service.agent.invoker.ToolDefinitionsHookData;
import com.ghost616.agentbase.service.agent.invoker.ToolDefinitionsHookResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * {@link LoadedSkillsToolHook} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoadedSkillsToolHookTest {

    @Mock
    private AgentExecutionContext ctx;

    private LoadedSkillsToolHook hook;

    @BeforeEach
    void setUp() {
        hook = new LoadedSkillsToolHook();
    }

    @Test
    void getPhase_返回BEFORE_TOOL_DEFINITIONS_BUILD() {
        assertSame(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD, hook.getPhase());
    }

    @Test
    void execute_无已加载技能_返回基础列表本身() {
        List<ToolConfigDTO> base = List.of(tool("t1", "base_tool"));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn(null);

        ToolDefinitionsHookResult result = hook.execute(ctx, new ToolDefinitionsHookData(base));

        assertSame(base, result.getTools());
    }

    @Test
    void execute_会话变量缺失_返回基础列表本身() {
        List<ToolConfigDTO> base = List.of(tool("t1", "base_tool"));

        ToolDefinitionsHookResult result = hook.execute(ctx, new ToolDefinitionsHookData(base));

        assertSame(base, result.getTools());
    }

    @Test
    void execute_有已加载技能_追加skillTools() {
        ToolConfigDTO toolA = tool("ta", "tool_a");
        ToolConfigDTO toolB = tool("tb", "tool_b");
        SkillConfigDTO skill = skillWithTools("s1", List.of(toolA, toolB));
        List<ToolConfigDTO> base = List.of(tool("t0", "base_tool"));
        when(ctx.isMainSession()).thenReturn(false);
        when(ctx.getSkills()).thenReturn(List.of(skill));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"s1\"]");

        ToolDefinitionsHookResult result = hook.execute(ctx, new ToolDefinitionsHookData(base));

        assertEquals(List.of(base.get(0), toolA, toolB), result.getTools());
    }

    @Test
    void execute_会话变量解析失败_降级返回基础列表() {
        List<ToolConfigDTO> base = List.of(tool("t1", "base_tool"));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("not-a-json");

        ToolDefinitionsHookResult result = hook.execute(ctx, new ToolDefinitionsHookData(base));

        assertSame(base, result.getTools());
    }

    @Test
    void execute_主会话_已加载CHILD技能_其skillTools不追加() {
        ToolConfigDTO childTool = tool("tc", "child_tool");
        SkillConfigDTO childSkill = SkillConfigDTO.builder()
                .name("child_skill")
                .sessionAuth(SessionAuthType.CHILD)
                .skillTools(List.of(childTool))
                .build();
        List<ToolConfigDTO> base = List.of(tool("t1", "base_tool"));
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(childSkill));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"child_skill\"]");

        ToolDefinitionsHookResult result = hook.execute(ctx, new ToolDefinitionsHookData(base));

        assertSame(base, result.getTools());
    }

    @Test
    void execute_追加工具与基础工具同名_按名称去重保持基础顺序() {
        ToolConfigDTO baseTool = tool("t1", "shared_tool");
        ToolConfigDTO appendedTool = tool("t2", "shared_tool");
        SkillConfigDTO skill = skillWithTools("s1", List.of(appendedTool));
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(skill));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"s1\"]");

        ToolDefinitionsHookResult result = hook.execute(ctx, new ToolDefinitionsHookData(List.of(baseTool)));

        assertEquals(1, result.getTools().size());
        assertSame(baseTool, result.getTools().get(0));
    }

    @Test
    void execute_data为null_返回null工具列表() {
        ToolDefinitionsHookResult result = hook.execute(ctx, null);

        assertNull(result.getTools());
    }

    @Test
    void execute_ctx为null_返回基础列表() {
        List<ToolConfigDTO> base = List.of(tool("t1", "base_tool"));

        ToolDefinitionsHookResult result = hook.execute(null, new ToolDefinitionsHookData(base));

        assertSame(base, result.getTools());
    }

    private ToolConfigDTO tool(String id, String name) {
        return ToolConfigDTO.builder().id(id).name(name).build();
    }

    private SkillConfigDTO skillWithTools(String name, List<ToolConfigDTO> skillTools) {
        return SkillConfigDTO.builder()
                .name(name)
                .sessionAuth(SessionAuthType.ALL)
                .skillTools(skillTools)
                .build();
    }
}