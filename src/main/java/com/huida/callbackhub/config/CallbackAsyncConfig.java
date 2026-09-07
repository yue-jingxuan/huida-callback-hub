package com.huida.callbackhub.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 回调网关异步与转发配置。
 * <p>
 * 职责：
 * <ul>
 *   <li>开启 {@code @EnableAsync} 异步支持</li>
 *   <li>提供回调转发专用线程池 {@code callbackTaskExecutor}</li>
 *   <li>提供带连接/读取超时的 {@link RestTemplate}，用于转发原始回调报文</li>
 * </ul>
 * 线程池与超时参数统一由 {@link CallbackGatewayProperties} 注入，可在 yml 中调整。
 * </p>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(CallbackGatewayProperties.class)
public class CallbackAsyncConfig {

    /**
     * 回调转发异步线程池。
     * <p>
     * 拒绝策略使用 CallerRunsPolicy：队列打满时由接收线程自己执行转发，
     * 宁可拖慢 HTTP 响应也不丢转发任务（任务已落库，丢失会导致状态永远 pending）。
     * </p>
     *
     * @param properties 网关配置
     * @return 线程池
     */
    @Bean(name = "callbackTaskExecutor")
    public ThreadPoolTaskExecutor callbackTaskExecutor(CallbackGatewayProperties properties) {
        CallbackGatewayProperties.Async async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setKeepAliveSeconds(async.getKeepAliveSeconds());
        executor.setThreadNamePrefix("callback-forward-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // MDC 上下文传递：让异步转发线程继承接收线程的 traceId，保证链路日志可串联
        executor.setTaskDecorator(mdcTaskDecorator());
        // 优雅停机：等待转发任务执行完再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * MDC 上下文传递装饰器。
     * <p>
     * MDC 基于 ThreadLocal，异步线程默认拿不到主线程的 traceId。
     * 本装饰器在任务提交时拷贝 MDC，在异步线程执行前恢复、执行后清理，
     * 避免线程池复用导致 traceId 串号。
     * </p>
     *
     * @return MDC 传递装饰器
     */
    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            // 提交时（主线程）拷贝当前 MDC 上下文
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                // 执行前（异步线程）恢复上下文
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    // 执行后清理，防止线程复用串号
                    MDC.clear();
                }
            };
        };
    }

    /**
     * 转发专用 RestTemplate，连接/读取超时由配置控制，避免下游慢导致线程长期占用。
     *
     * @param properties 网关配置
     * @return RestTemplate
     */
    @Bean
    public RestTemplate forwardRestTemplate(CallbackGatewayProperties properties) {
        CallbackGatewayProperties.Forward forward = properties.getForward();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(forward.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(forward.getReadTimeout()));
        return new RestTemplate(factory);
    }
}
