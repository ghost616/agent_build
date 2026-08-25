package com.ghost616.platform.config;

import com.ghost616.agentbase.dto.tool.ToolConfigDTO;
import com.ghost616.agentbase.service.agent.ToolDataProvider;
import com.ghost616.agentbase.service.agent.invoker.CustomToolInvoker;
import com.ghost616.agentinteg.tool.SubSessionCallbackTool;
import com.ghost616.platform.PlatformApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 完整 Spring 上下文启动验证（皋陶审查指出单测无法覆盖的点）：
 * DefaultToolDataProvider 改为 ObjectProvider&lt;AgentContextManager&gt; 按需注入后，
 * 构造器循环依赖（DefaultToolDataProvider → AgentContextManager → AgentAssembler → toolDataProvider）
 * 应被解除——Spring Boot 3.2（allow-circular-references=false）下上下文可正常启动，
 * 不再抛 BeanCurrentlyInCreationException / UnsatisfiedDependencyException。
 */
@SpringBootTest(classes = PlatformApplication.class)
@TestPropertySource(properties = {
        "spring.datasource.dynamic.primary=primary",
        "spring.datasource.dynamic.strict=false",
        "spring.datasource.dynamic.datasource.primary.url=jdbc:sqlite:target/agent_platform_ctx_test.db",
        "spring.datasource.dynamic.datasource.message.url=jdbc:sqlite:target/agent_platform_ctx_test.db",
        "spring.sql.init.mode=always"
})
class PlatformApplicationContextLoadsTest {

    @Autowired
    private ToolDataProvider toolDataProvider;

    @Test
    @DisplayName("完整 Spring 上下文可启动：循环依赖已解除")
    void contextLoads() {
        assertNotNull(toolDataProvider, "ToolDataProvider Bean 应已注册");
    }

    @Test
    @DisplayName("真实上下文中 getCustomInvoker 通过 ObjectProvider.getObject() 构造 SubSessionCallbackTool")
    void subSessionToolInvoker_constructedWithRealAgentContextManager() {
        ToolConfigDTO config = SubSessionCallbackTool.createToolConfig();
        config.setId(SubSessionCallbackTool.TOOL_NAME);

        CustomToolInvoker invoker = toolDataProvider.getCustomInvoker(config);

        assertInstanceOf(SubSessionCallbackTool.class, invoker,
                "getCustomInvoker 应返回 SubSessionCallbackTool 实例（内部经 agentContextManagerProvider.getObject() 获取真实 AgentContextManager Bean）");
    }
}