package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HookInvokerWrapper} 单元测试。
 *
 * <p>覆盖：数据载体类型预解析缓存、supports 匹配/不匹配/dataType 为 null 放行、
 * getPhase/getIndex 委托、execute 内部强转委托执行。</p>
 */
class HookInvokerWrapperTest {

    // ==================== 测试 HOOK 实现 ====================

    /** 直接实现 HookInvoker，返回固定结果 */
    static class DirectHook implements HookInvoker<ChatChunkHookData, EmptyHookResult> {
        final EmptyHookResult result;
        boolean executed;

        DirectHook(EmptyHookResult result) {
            this.result = result;
        }

        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
            executed = true;
            return result;
        }
    }

    /** 系统 HOOK，指定 index */
    static class IndexedSystemHook implements SystemHook<ChatChunkHookData, EmptyHookResult> {
        private final int index;

        IndexedSystemHook(int index) {
            this.index = index;
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 原始类型使用（无泛型信息），dataType 为 null */
    static class RawHook implements HookInvoker {
        boolean executed;

        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public HookResult execute(AgentExecutionContext ctx, HookData data) {
            executed = true;
            return null;
        }
    }

    // ==================== getPhase / getIndex ====================

    @Test
    void getPhase_委托给原始Hook() {
        HookInvokerWrapper wrapper = new HookInvokerWrapper(new DirectHook(EmptyHookResult.INSTANCE));
        assertEquals(HookPhase.AFTER_MESSAGE_RECEIVE, wrapper.getPhase());
    }

    @Test
    void getIndex_系统Hook委托getIndex() {
        HookInvokerWrapper wrapper = new HookInvokerWrapper(new IndexedSystemHook(42));
        assertEquals(42, wrapper.getIndex());
    }

    @Test
    void getIndex_非系统Hook返回0() {
        HookInvokerWrapper wrapper = new HookInvokerWrapper(new DirectHook(EmptyHookResult.INSTANCE));
        assertEquals(0, wrapper.getIndex());
    }

    // ==================== supports ====================

    @Test
    void supports_类型匹配返回true() {
        HookInvokerWrapper wrapper = new HookInvokerWrapper(new DirectHook(EmptyHookResult.INSTANCE));
        assertTrue(wrapper.supports(new ChatChunkHookData(null)));
    }

    @Test
    void supports_类型不匹配返回false() {
        HookInvokerWrapper wrapper = new HookInvokerWrapper(new DirectHook(EmptyHookResult.INSTANCE));
        assertFalse(wrapper.supports(new ToolHookContext()));
        assertFalse(wrapper.supports(null));
    }

    @Test
    void supports_dataType为null时放行() {
        HookInvokerWrapper wrapper = new HookInvokerWrapper(new RawHook());
        assertTrue(wrapper.supports(new ChatChunkHookData(null)));
        assertTrue(wrapper.supports(new ToolHookContext()));
        assertTrue(wrapper.supports(null));
    }

    // ==================== execute ====================

    @Test
    void execute_委托原始Hook并返回结果() {
        DirectHook hook = new DirectHook(EmptyHookResult.INSTANCE);
        HookInvokerWrapper wrapper = new HookInvokerWrapper(hook);

        HookResult result = wrapper.execute(null, new ChatChunkHookData(null));

        assertTrue(hook.executed);
        assertSame(EmptyHookResult.INSTANCE, result);
    }

    @Test
    void execute_返回null透传() {
        DirectHook hook = new DirectHook(null);
        HookInvokerWrapper wrapper = new HookInvokerWrapper(hook);

        assertNull(wrapper.execute(null, new ChatChunkHookData(null)));
        assertTrue(hook.executed);
    }

    @Test
    void execute_原始类型hook委托执行() {
        RawHook hook = new RawHook();
        HookInvokerWrapper wrapper = new HookInvokerWrapper(hook);

        wrapper.execute(null, new ChatChunkHookData(null));

        assertTrue(hook.executed);
    }
}