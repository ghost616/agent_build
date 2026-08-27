package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.core.AgentComponentRegistry;
import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import com.ghost616.agentbase.service.agent.ChatDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class HookManagerTest {

    private HookManager hookManager;

    @Mock
    private AgentExecutionContext ctx;
    @Mock
    private HookData data;
    @Mock
    private HookInvoker regularHook1;
    @Mock
    private HookInvoker regularHook2;
    @Mock
    private SystemHook systemHook1;
    @Mock
    private SystemHook systemHook2;
    @Mock
    private SystemPostHook postHook1;
    @Mock
    private SystemPostHook postHook2;
    @Mock
    private AgentComponentRegistry registry;
    @Mock
    private ChatDataProvider chatDataProvider;

    /** 真实实现：数据载体类型为 ChatChunkHookData 的普通 HOOK */
    static class ChunkRegularHook implements HookInvoker<ChatChunkHookData, EmptyHookResult> {
        boolean executed;
        EmptyHookResult result = EmptyHookResult.INSTANCE;

        @Override
        public HookPhase getPhase() {
            return HookPhase.BEFORE_MESSAGE_SEND;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext context, ChatChunkHookData data) {
            executed = true;
            return result;
        }
    }

    /** 真实实现：数据载体类型为 ToolHookContext 的系统 HOOK */
    static class ToolSystemHook implements SystemHook<ToolHookContext, EmptyHookResult> {
        boolean executed;

        @Override
        public HookPhase getPhase() {
            return HookPhase.BEFORE_MESSAGE_SEND;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext context, ToolHookContext data) {
            executed = true;
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 原始类型（无泛型信息）实现：数据载体类型无法解析，应放行执行 */
    static class RawHook implements HookInvoker {
        boolean executed;

        @Override
        public HookPhase getPhase() {
            return HookPhase.BEFORE_MESSAGE_SEND;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext context, HookData data) {
            executed = true;
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 测试用自定义结果类型 */
    static class FakeResult implements HookResult {
    }

    @BeforeEach
    void setUp() {
        hookManager = new HookManager(registry);
        when(registry.getChatDataProvider()).thenReturn(chatDataProvider);
        when(regularHook1.getPhase()).thenReturn(HookPhase.BEFORE_MESSAGE_SEND);
        when(regularHook2.getPhase()).thenReturn(HookPhase.BEFORE_MESSAGE_SEND);
        when(systemHook1.getPhase()).thenReturn(HookPhase.BEFORE_MESSAGE_SEND);
        when(systemHook2.getPhase()).thenReturn(HookPhase.BEFORE_MESSAGE_SEND);
        when(systemHook1.getIndex()).thenReturn(10);
        when(systemHook2.getIndex()).thenReturn(5);
        when(postHook1.getIndex()).thenReturn(2);
        when(postHook2.getIndex()).thenReturn(1);
    }

    // ==================== 正向覆盖 ====================

    @Test
    void regularHooks执行成功() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1, regularHook2));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(regularHook2).execute(ctx, data);
    }

    @Test
    void systemHooks按index升序执行() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(systemHook1, systemHook2));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(systemHook2).execute(ctx, data);
        verify(systemHook1).execute(ctx, data);
    }

    @Test
    void systemPostHooks按index升序执行() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(postHook1, postHook2));
        hookManager.refreshHooks();
        hookManager.executePostHooks(ctx, data);
        verify(postHook2).execute(ctx, data);
        verify(postHook1).execute(ctx, data);
    }

    @Test
    void 所有类型hooks同时执行() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1, systemHook1, postHook1));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        hookManager.executePostHooks(ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(systemHook1).execute(ctx, data);
        verify(postHook1).execute(ctx, data);
    }

    // ==================== 反向覆盖 ====================

    @Test
    void regularHook抛出异常不影响后续hook执行() {
        doThrow(new RuntimeException("hook1 fail")).when(regularHook1).execute(ctx, data);
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1, regularHook2));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(regularHook2).execute(ctx, data);
    }

    @Test
    void systemHook抛出异常不影响后续hook执行() {
        doThrow(new RuntimeException("systemHook1 fail")).when(systemHook1).execute(ctx, data);
        when(chatDataProvider.getHooks()).thenReturn(List.of(systemHook1, systemHook2));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(systemHook1).execute(ctx, data);
        verify(systemHook2).execute(ctx, data);
    }

    @Test
    void postHook抛出异常不影响后续hook执行() {
        doThrow(new RuntimeException("postHook1 fail")).when(postHook1).execute(ctx, data);
        when(chatDataProvider.getHooks()).thenReturn(List.of(postHook1, postHook2));
        hookManager.refreshHooks();
        hookManager.executePostHooks(ctx, data);
        verify(postHook1).execute(ctx, data);
        verify(postHook2).execute(ctx, data);
    }

    @Test
    void 所有hook同时抛出异常不中断整体流程() {
        doThrow(new RuntimeException("fail")).when(regularHook1).execute(ctx, data);
        doThrow(new RuntimeException("fail")).when(systemHook1).execute(ctx, data);
        doThrow(new RuntimeException("fail")).when(postHook1).execute(ctx, data);
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1, systemHook1, postHook1));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        hookManager.executePostHooks(ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(systemHook1).execute(ctx, data);
        verify(postHook1).execute(ctx, data);
    }

    // ==================== 边界值 ====================

    @Test
    void triggerHooks无对应phase的hook不执行() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.SESSION_START, ctx, data);
        verify(regularHook1, never()).execute(any(), any());
    }

    @Test
    void refreshHooks空列表所有方法不抛异常() {
        when(chatDataProvider.getHooks()).thenReturn(List.of());
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        hookManager.executePostHooks(ctx, data);
    }

    @Test
    void 只有systemHooks时regularHooks不执行() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(systemHook1));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(systemHook1).execute(ctx, data);
    }

    @Test
    void 只有regularHooks时systemHooks不执行() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1));
        hookManager.refreshHooks();
        hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
        verifyNoMoreInteractions(systemHook1, systemHook2, postHook1, postHook2);
    }

    // ==================== triggerSessionHooks ====================

    @Test
    void triggerSessionHooks_正向_regularHook执行() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(regularHook1));
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
    }

    @Test
    void triggerSessionHooks_systemHook按index升序执行() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(systemHook1, systemHook2));
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(systemHook2).execute(ctx, data);
        verify(systemHook1).execute(ctx, data);
    }

    @Test
    void triggerSessionHooks_systemPostHook被跳过() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(postHook1));
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(postHook1, never()).execute(any(), any());
    }

    @Test
    void triggerSessionHooks_regularAndSystem混合执行() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(regularHook1, systemHook1));
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(systemHook1).execute(ctx, data);
    }

    @Test
    void triggerSessionHooks_异常不中断后续执行() {
        doThrow(new RuntimeException("fail")).when(regularHook1).execute(ctx, data);
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(regularHook1, regularHook2));
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(regularHook2).execute(ctx, data);
    }

    @Test
    void triggerSessionHooks_sessionHooks为null无异常() {
        when(chatDataProvider.getHooks("1")).thenReturn(null);
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
    }

    @Test
    void triggerSessionHooks_sessionHooks为空无异常() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of());
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
    }

    @Test
    void triggerSessionHooks_无匹配phase不执行() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(regularHook1));
        hookManager.triggerSessionHooks("1", HookPhase.SESSION_START, ctx, data);
        verify(regularHook1, never()).execute(any(), any());
    }

    @Test
    void triggerSessionHooks_systemPostHook混合场景不影响regular() {
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(regularHook1, postHook1));
        hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, data);
        verify(regularHook1).execute(ctx, data);
        verify(postHook1, never()).execute(any(), any());
    }

    // ==================== 会话级 HOOK 类型缓存 ====================

    @Test
    void triggerSessionHooks_同一class多实例仅解析一次() throws Exception {
        ChunkRegularHook hook1 = new ChunkRegularHook();
        ChunkRegularHook hook2 = new ChunkRegularHook();
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(hook1, hook2));

        List<HookResult> results = hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, new ChatChunkHookData(null));

        assertTrue(hook1.executed);
        assertTrue(hook2.executed);
        assertEquals(2, results.size());
        // 同一实现类仅缓存一个条目，证明类型仅解析一次
        Map<Class<?>, Class<?>> cache = hookDataTypeCache();
        assertEquals(1, cache.size());
        assertEquals(ChatChunkHookData.class, cache.get(ChunkRegularHook.class));
    }

    @Test
    void triggerSessionHooks_不同class分别缓存() throws Exception {
        ChunkRegularHook chunkHook = new ChunkRegularHook();
        ToolSystemHook toolHook = new ToolSystemHook();
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(chunkHook, toolHook));

        // toolHook 数据载体为 ToolHookContext，与 ChatChunkHookData 不匹配，跳过执行
        List<HookResult> results = hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, new ChatChunkHookData(null));

        assertTrue(chunkHook.executed);
        assertFalse(toolHook.executed);
        assertEquals(1, results.size());
        Map<Class<?>, Class<?>> cache = hookDataTypeCache();
        assertEquals(2, cache.size());
        assertEquals(ChatChunkHookData.class, cache.get(ChunkRegularHook.class));
        assertEquals(ToolHookContext.class, cache.get(ToolSystemHook.class));
    }

    @Test
    void triggerSessionHooks_类型不匹配时跳过执行() throws Exception {
        ChunkRegularHook hook = new ChunkRegularHook();
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(hook));

        List<HookResult> results = hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, new ToolHookContext());

        assertFalse(hook.executed);
        assertTrue(results.isEmpty());
        Map<Class<?>, Class<?>> cache = hookDataTypeCache();
        assertEquals(1, cache.size());
        assertEquals(ChatChunkHookData.class, cache.get(ChunkRegularHook.class));
    }

    @Test
    void triggerSessionHooks_无法解析类型时放行() throws Exception {
        RawHook hook = new RawHook();
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(hook));

        List<HookResult> results = hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, new ChatChunkHookData(null));

        assertTrue(hook.executed);
        assertEquals(1, results.size());
        // 无法解析时 computeIfAbsent 不缓存 null 映射，缓存保持为空
        assertTrue(hookDataTypeCache().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Map<Class<?>, Class<?>> hookDataTypeCache() throws Exception {
        Field field = HookManager.class.getDeclaredField("hookDataTypeCache");
        field.setAccessible(true);
        return (Map<Class<?>, Class<?>>) field.get(hookManager);
    }

    // ==================== 数据载体匹配与结果收集 ====================

    @Test
    void triggerHooks_数据载体类型不匹配时跳过执行() {
        ChunkRegularHook hook = new ChunkRegularHook();
        when(chatDataProvider.getHooks()).thenReturn(List.of(hook));
        hookManager.refreshHooks();

        List<HookResult> results = hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, new ToolHookContext());

        assertFalse(hook.executed);
        assertTrue(results.isEmpty());
    }

    @Test
    void triggerHooks_数据载体类型匹配时执行并收集结果() {
        ChunkRegularHook hook = new ChunkRegularHook();
        when(chatDataProvider.getHooks()).thenReturn(List.of(hook));
        hookManager.refreshHooks();

        List<HookResult> results = hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, new ChatChunkHookData(null));

        assertTrue(hook.executed);
        assertEquals(1, results.size());
        assertSame(EmptyHookResult.INSTANCE, results.get(0));
    }

    @Test
    void triggerHooks_返回null的结果不收集() {
        ChunkRegularHook hook = new ChunkRegularHook();
        hook.result = null;
        when(chatDataProvider.getHooks()).thenReturn(List.of(hook));
        hookManager.refreshHooks();

        List<HookResult> results = hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, new ChatChunkHookData(null));

        assertTrue(hook.executed);
        assertTrue(results.isEmpty());
    }

    @Test
    void triggerHooks_系统hook数据载体不匹配时跳过执行() {
        ToolSystemHook hook = new ToolSystemHook();
        when(chatDataProvider.getHooks()).thenReturn(List.of(hook));
        hookManager.refreshHooks();

        List<HookResult> results = hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, new ChatChunkHookData(null));

        assertFalse(hook.executed);
        assertTrue(results.isEmpty());
    }

    @Test
    void triggerHooks_系统hook数据载体匹配时执行() {
        ToolSystemHook hook = new ToolSystemHook();
        when(chatDataProvider.getHooks()).thenReturn(List.of(hook));
        hookManager.refreshHooks();

        List<HookResult> results = hookManager.triggerHooks(HookPhase.BEFORE_MESSAGE_SEND, ctx, new ToolHookContext());

        assertTrue(hook.executed);
        assertEquals(1, results.size());
    }

    @Test
    void triggerSessionHooks_数据载体不匹配时跳过会话hook() {
        ChunkRegularHook hook = new ChunkRegularHook();
        when(chatDataProvider.getHooks("1")).thenReturn(List.of(hook));

        List<HookResult> results = hookManager.triggerSessionHooks("1", HookPhase.BEFORE_MESSAGE_SEND, ctx, new ToolHookContext());

        assertFalse(hook.executed);
        assertTrue(results.isEmpty());
    }

    @Test
    void executePostHooks_数据载体不匹配时跳过后置hook() {
        ChunkRegularHook hook = new ChunkRegularHook();
        when(chatDataProvider.getHooks()).thenReturn(List.of(hook));
        hookManager.refreshHooks();

        List<HookResult> results = hookManager.executePostHooks(ctx, new ToolHookContext());

        assertFalse(hook.executed);
        assertTrue(results.isEmpty());
    }

    // ==================== castHookResult ====================

    @Test
    void castHookResult_类型匹配返回转换结果() {
        assertSame(EmptyHookResult.INSTANCE, hookManager.castHookResult(EmptyHookResult.INSTANCE, EmptyHookResult.class));
    }

    @Test
    void castHookResult_result为null返回null() {
        assertNull(hookManager.castHookResult(null, EmptyHookResult.class));
    }

    @Test
    void castHookResult_clazz为null返回null() {
        assertNull(hookManager.castHookResult(EmptyHookResult.INSTANCE, null));
    }

    @Test
    void castHookResult_类型不匹配返回null() {
        assertNull(hookManager.castHookResult(new FakeResult(), EmptyHookResult.class));
    }

    // ==================== hasHooks ====================

    @Test
    void hasHooks_未注册阶段或null阶段返回false() {
        assertFalse(hookManager.hasHooks(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD));
        assertFalse(hookManager.hasHooks(null));
    }

    @Test
    void hasHooks_注册普通HOOK后对应阶段返回true() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(regularHook1));
        hookManager.refreshHooks();

        assertTrue(hookManager.hasHooks(HookPhase.BEFORE_MESSAGE_SEND),
                "普通 HOOK 注册后其阶段应返回 true");
        assertFalse(hookManager.hasHooks(HookPhase.BEFORE_TOOL_DEFINITIONS_BUILD),
                "未注册阶段应返回 false");
    }

    @Test
    void hasHooks_注册系统HOOK后对应阶段返回true() {
        when(chatDataProvider.getHooks()).thenReturn(List.of(systemHook1));
        hookManager.refreshHooks();

        assertTrue(hookManager.hasHooks(HookPhase.BEFORE_MESSAGE_SEND),
                "系统 HOOK 注册后其阶段应返回 true");
    }
}