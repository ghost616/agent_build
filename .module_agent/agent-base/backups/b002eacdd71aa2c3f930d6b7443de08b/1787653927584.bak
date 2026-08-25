package com.ghost616.agentbase.service.agent.invoker;

import com.ghost616.agentbase.dto.model.Message;
import com.ghost616.agentbase.service.agent.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 SubSessionCallback 仍是函数式接口：
 * 1. @FunctionalInterface 注解保留
 * 2. 仅有一个抽象方法，且签名为 execute(AgentExecutionContext, String, String, Boolean)
 * 3. 可用 lambda 直接实现
 */
class SubSessionCallbackFunctionalTest {

    @Test
    void 应标注FunctionalInterface注解() {
        assertNotNull(SubSessionCallback.class.getAnnotation(FunctionalInterface.class),
                "SubSessionCallback 必须保留 @FunctionalInterface 注解");
    }

    @Test
    void 应仅有一个抽象方法() {
        Method[] methods = SubSessionCallback.class.getDeclaredMethods();
        int abstractCount = 0;
        for (Method m : methods) {
            if (Modifier.isAbstract(m.getModifiers())) {
                abstractCount++;
            }
        }
        assertEquals(1, abstractCount, "函数式接口必须只有 1 个抽象方法");
    }

    @Test
    void execute方法签名应包含AgentExecutionContext参数() throws Exception {
        Method execute = SubSessionCallback.class.getDeclaredMethod(
                "execute", AgentExecutionContext.class, String.class, String.class, Boolean.class);
        assertNotNull(execute, "execute(AgentExecutionContext, String, String, Boolean) 方法应存在");
        assertEquals(Message.class, execute.getReturnType());
    }

    @Test
    void 应可用lambda实现() {
        SubSessionCallback callback = (ctx, sessionId, userMessage, thinking) -> null;
        assertNotNull(callback);
    }
}