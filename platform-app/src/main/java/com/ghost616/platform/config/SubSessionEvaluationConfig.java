package com.ghost616.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 评估子会话后台驱动线程池配置。
 *
 * <p>独立 {@code @Configuration}，避免 ExecutorService Bean 参与 {@link AgentContextConfiguration}
 * 的构造链造成启动循环依赖：
 * AgentContextConfiguration(构造注入 ToolDataProvider) → DefaultToolDataProvider →
 * DefaultSubSessionCallback(构造注入本 Bean) → 若本 Bean 定义于 AgentContextConfiguration，
 * 则需在 AgentContextConfiguration 实例构造完成前调用其 @Bean 工厂方法形成环。</p>
 *
 * <p>线程池为无界缓存线程池：每层嵌套驱动占用一个线程，父线程阻塞等待期间仍可创建子层
 * 驱动任务，避免固定容量线程池造成死锁；守护线程保证不阻塞应用退出。</p>
 */
@Configuration
public class SubSessionEvaluationConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService subSessionEvaluationExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "eval-sub-session-driver");
            thread.setDaemon(true);
            return thread;
        });
    }
}
