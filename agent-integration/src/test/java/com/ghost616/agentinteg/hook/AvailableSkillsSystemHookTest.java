package com.ghost616.agentinteg.hook;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ghost616.agentbase.dto.model.ToolDefinition;
import com.ghost616.agentbase.dto.skill.SkillConfigDTO;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.enums.SessionAuthType;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.invoker.LoadSkillsSystemTool;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookData;
import com.ghost616.agentbase.service.agent.invoker.SystemPromptHookResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link AvailableSkillsSystemHook} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AvailableSkillsSystemHookTest {

    @Mock
    private AgentExecutionContext ctx;

    private AvailableSkillsSystemHook hook;

    @BeforeEach
    void setUp() {
        hook = new AvailableSkillsSystemHook();
    }

    @Test
    void getPhase_返回AFTER_PRE_SYSTEM_PROMPT_BUILD() {
        assertSame(HookPhase.AFTER_PRE_SYSTEM_PROMPT_BUILD, hook.getPhase());
    }

    // ==================== hasLoadSkillsTool 存在/缺失分支 ====================

    @Test
    void execute_工具定义不含load_skills_返回null() {
        SystemPromptHookData data = new SystemPromptHookData(List.of(
                ToolDefinition.builder().name("other_tool").build()));

        assertNull(hook.execute(ctx, data));
    }

    @Test
    void execute_工具定义列表为空_返回null() {
        assertNull(hook.execute(ctx, new SystemPromptHookData(List.of())));
    }

    @Test
    void execute_工具定义列表为null_返回null() {
        assertNull(hook.execute(ctx, new SystemPromptHookData(null)));
    }

    @Test
    void execute_ctx为null_返回null() {
        assertNull(hook.execute(null, dataWithLoadSkills()));
    }

    @Test
    void execute_工具定义包含load_skills_生成提示词() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(skill("s1", "d1", SessionAuthType.ALL)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertNotNull(result);
        assertTrue(result.getSystemPrompt().contains("以下是可用的技能"));
    }

    // ==================== 可用技能列表构建（含描述/不含描述） ====================

    @Test
    void execute_技能含描述_文本断言() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("web_search", "网页搜索技能", SessionAuthType.ALL)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- web_search: 网页搜索技能\n"), result.getSystemPrompt());
    }

    @Test
    void execute_技能不含描述_仅生成name行() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("web_search", null, SessionAuthType.ALL)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- web_search\n"), result.getSystemPrompt());
    }

    @Test
    void execute_技能描述为空白_视为无描述() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("web_search", "   ", SessionAuthType.ALL)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- web_search\n"), result.getSystemPrompt());
    }

    @Test
    void execute_多技能_按序生成多行() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("skill_a", "技能A描述", SessionAuthType.ALL),
                skill("skill_b", null, SessionAuthType.PARENT)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        String prompt = result.getSystemPrompt();
        assertTrue(prompt.contains("- skill_a: 技能A描述\n"));
        assertTrue(prompt.contains("- skill_b\n"));
        assertTrue(prompt.indexOf("- skill_a") < prompt.indexOf("- skill_b"));
    }

    // ==================== 主会话过滤 CHILD（可用技能） ====================

    @Test
    void execute_主会话_过滤CHILD技能() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("child_only", "子会话专用", SessionAuthType.CHILD),
                skill("all_skill", "通用技能", SessionAuthType.ALL)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        String prompt = result.getSystemPrompt();
        assertTrue(prompt.contains("- all_skill: 通用技能\n"));
        assertFalse(prompt.contains("child_only"));
    }

    @Test
    void execute_主会话_全部技能均为CHILD_返回null() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("child1", "d1", SessionAuthType.CHILD),
                skill("child2", "d2", SessionAuthType.CHILD)));

        assertNull(hook.execute(ctx, dataWithLoadSkills()));
    }

    @Test
    void execute_非主会话_不过滤CHILD技能() {
        when(ctx.isMainSession()).thenReturn(false);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("child_only", "子会话专用", SessionAuthType.CHILD),
                skill("parent_only", "父会话专用", SessionAuthType.PARENT)));

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        String prompt = result.getSystemPrompt();
        assertTrue(prompt.contains("- child_only: 子会话专用\n"));
        assertTrue(prompt.contains("- parent_only: 父会话专用\n"));
    }

    // ==================== 空技能 ====================

    @Test
    void execute_技能列表为空_返回null() {
        when(ctx.getSkills()).thenReturn(List.of());

        assertNull(hook.execute(ctx, dataWithLoadSkills()));
    }

    @Test
    void execute_技能列表为null_返回null() {
        when(ctx.getSkills()).thenReturn(null);

        assertNull(hook.execute(ctx, dataWithLoadSkills()));
    }

    // ==================== 已加载技能提示词段 ====================

    @Test
    void execute_两段拼接_可用技能在前已加载技能在后() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("web_search", "网页搜索技能", null, SessionAuthType.ALL),
                skill("data_probe", "数据探查", "执行数据探查并返回结果", SessionAuthType.ALL)));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"data_probe\"]");

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- web_search: 网页搜索技能\n- data_probe: 数据探查\n")
                + "\n\n"
                + expectedLoadedPrompt("## data_probe\n", "执行数据探查并返回结果"), result.getSystemPrompt());
    }

    @Test
    void execute_无可用技能但有已加载技能_仅返回已加载段() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(),
                List.of(skill("data_probe", null, "执行数据探查", SessionAuthType.ALL)));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"data_probe\"]");

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertNotNull(result);
        assertEquals(expectedLoadedPrompt("## data_probe\n", "执行数据探查"), result.getSystemPrompt());
    }

    @Test
    void execute_已加载技能无提示词_仅生成name标题() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("data_probe", "数据探查", null, SessionAuthType.ALL)));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"data_probe\"]");

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- data_probe: 数据探查\n")
                + "\n\n"
                + expectedLoadedPrompt("## data_probe\n", null), result.getSystemPrompt());
    }

    @Test
    void execute_会话变量解析失败_降级仅返回可用列表段() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("web_search", "网页搜索技能", null, SessionAuthType.ALL)));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("not-a-json");

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- web_search: 网页搜索技能\n"), result.getSystemPrompt());
    }

    @Test
    void execute_主会话_已加载CHILD技能被过滤_返回null() {
        when(ctx.isMainSession()).thenReturn(true);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("child_skill", "子技能描述", "子技能提示词", SessionAuthType.CHILD)));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"child_skill\"]");

        assertNull(hook.execute(ctx, dataWithLoadSkills()));
    }

    @Test
    void execute_非主会话_已加载CHILD技能_两段均包含() {
        when(ctx.isMainSession()).thenReturn(false);
        when(ctx.getSkills()).thenReturn(List.of(
                skill("child_skill", "子技能描述", "子技能提示词", SessionAuthType.CHILD)));
        when(ctx.getSessionVariable(LoadSkillsSystemTool.SESSION_KEY)).thenReturn("[\"child_skill\"]");

        SystemPromptHookResult result = hook.execute(ctx, dataWithLoadSkills());

        assertEquals(expectedPrompt("- child_skill: 子技能描述\n")
                + "\n\n"
                + expectedLoadedPrompt("## child_skill\n", "子技能提示词"), result.getSystemPrompt());
    }

    private SkillConfigDTO skill(String name, String description, SessionAuthType sessionAuth) {
        return skill(name, description, null, sessionAuth);
    }

    private SkillConfigDTO skill(String name, String description, String prompt, SessionAuthType sessionAuth) {
        return SkillConfigDTO.builder()
                .name(name)
                .description(description)
                .prompt(prompt)
                .sessionAuth(sessionAuth)
                .build();
    }

    private SystemPromptHookData dataWithLoadSkills() {
        return new SystemPromptHookData(List.of(
                ToolDefinition.builder().name("other_tool").build(),
                ToolDefinition.builder().name(LoadSkillsSystemTool.FULL_TOOL_NAME).build()));
    }

    private String expectedPrompt(String skillLines) {
        return "以下是可用的技能（SKILL）列表（技能本身不是工具，需先加载再使用其关联的工具）：\n"
                + skillLines
                + "\n请使用 " + LoadSkillsSystemTool.FULL_TOOL_NAME
                + " 系统工具加载所需技能。加载后，该技能的关联工具将变为可用，届时再调用具体工具。禁止直接以技能名称作为工具调用。";
    }

    private String expectedLoadedPrompt(String skillHeaderLine, String prompt) {
        return "以下技能已加载，请按照其提示词指导执行任务：\n\n"
                + skillHeaderLine
                + (prompt != null ? prompt + "\n\n" : "");
    }
}