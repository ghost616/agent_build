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

    // ==================== 技能列表构建（含描述/不含描述） ====================

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

    // ==================== 主会话过滤 CHILD ====================

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

    private SkillConfigDTO skill(String name, String description, SessionAuthType sessionAuth) {
        return SkillConfigDTO.builder()
                .name(name)
                .description(description)
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
}