package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.enums.HookPhase;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HookDataMatcher} 单元测试。
 *
 * <p>覆盖：直接实现 HookInvoker、经 SystemHook/SystemPostHook 中间接口实现、
 * 泛型父类继承链与类型变量映射的类型解析；matches 匹配/不匹配/无法解析放行。</p>
 */
class HookDataMatcherTest {

    // ==================== 测试 HOOK 实现 ====================

    /** 直接实现 HookInvoker */
    static class DirectHook implements HookInvoker<ChatChunkHookData, EmptyHookResult> {
        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 经 SystemHook 中间接口实现 */
    static class SystemHookImpl implements SystemHook<ChatChunkHookData, EmptyHookResult> {
        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 经 SystemPostHook 中间接口实现 */
    static class PostHookImpl implements SystemPostHook<ChatChunkHookData, EmptyHookResult> {
        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 泛型父类实现 SystemHook，具体类经类型变量映射解析 */
    static abstract class AbstractToolHook<D extends HookData<R>, R extends HookResult> implements SystemHook<D, R> {
        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }
    }

    static class ToolContextHook extends AbstractToolHook<ToolHookContext, EmptyHookResult> {
        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ToolHookContext data) {
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 中间接口（无类型参数）再经 SystemHook 间接实现 */
    interface ChunkPostInterface extends SystemPostHook<ChatChunkHookData, EmptyHookResult> {
    }

    static class IndirectInterfaceHook implements ChunkPostInterface {
        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public EmptyHookResult execute(AgentExecutionContext ctx, ChatChunkHookData data) {
            return EmptyHookResult.INSTANCE;
        }
    }

    /** 原始类型使用（无泛型信息），无法解析 */
    static class RawHook implements HookInvoker {
        @Override
        public HookPhase getPhase() {
            return HookPhase.AFTER_MESSAGE_RECEIVE;
        }

        @Override
        public HookResult execute(AgentExecutionContext ctx, HookData data) {
            return null;
        }
    }

    // ==================== resolveDataType ====================

    @Test
    void resolveDataType_直接实现HookInvoker解析D() {
        assertEquals(ChatChunkHookData.class, HookDataMatcher.resolveDataType(new DirectHook()));
    }

    @Test
    void resolveDataType_经SystemHook中间接口解析D() {
        assertEquals(ChatChunkHookData.class, HookDataMatcher.resolveDataType(new SystemHookImpl()));
    }

    @Test
    void resolveDataType_经SystemPostHook中间接口解析D() {
        assertEquals(ChatChunkHookData.class, HookDataMatcher.resolveDataType(new PostHookImpl()));
    }

    @Test
    void resolveDataType_泛型父类继承链类型变量映射() {
        assertEquals(ToolHookContext.class, HookDataMatcher.resolveDataType(new ToolContextHook()));
    }

    @Test
    void resolveDataType_中间接口间接实现() {
        assertEquals(ChatChunkHookData.class, HookDataMatcher.resolveDataType(new IndirectInterfaceHook()));
    }

    @Test
    void resolveDataType_原始类型使用返回null() {
        assertNull(HookDataMatcher.resolveDataType(new RawHook()));
    }

    @Test
    void resolveDataType_null返回null() {
        assertNull(HookDataMatcher.resolveDataType(null));
    }

    // ==================== matches ====================

    @Test
    void matches_类型匹配返回true() {
        DirectHook hook = new DirectHook();
        assertTrue(HookDataMatcher.matches(hook, new ChatChunkHookData(null)));
    }

    @Test
    void matches_类型不匹配返回false() {
        DirectHook hook = new DirectHook();
        assertFalse(HookDataMatcher.matches(hook, new ToolHookContext()));
    }

    @Test
    void matches_类型不匹配且data为null返回false() {
        DirectHook hook = new DirectHook();
        assertFalse(HookDataMatcher.matches(hook, null));
    }

    @Test
    void matches_无法解析时放行返回true() {
        RawHook hook = new RawHook();
        assertTrue(HookDataMatcher.matches(hook, new ChatChunkHookData(null)));
        assertTrue(HookDataMatcher.matches(hook, null));
    }

    @Test
    void matches_nullHook放行返回true() {
        assertTrue(HookDataMatcher.matches(null, new ChatChunkHookData(null)));
    }
}