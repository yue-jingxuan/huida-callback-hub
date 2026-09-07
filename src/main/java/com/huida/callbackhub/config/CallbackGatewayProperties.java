package com.huida.callbackhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 回调网关配置属性，对应 yml 中 {@code huida.callback} 前缀。
 * <p>
 * 包含两部分：
 * <ul>
 *   <li>{@link Async}：异步转发线程池参数</li>
 *   <li>{@link Forward}：转发 HTTP 请求的超时参数</li>
 * </ul>
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "huida.callback")
public class CallbackGatewayProperties {

    /** 异步转发线程池参数 */
    private Async async = new Async();

    /** 转发 HTTP 超时参数 */
    private Forward forward = new Forward();

    /**
     * 异步转发线程池参数
     */
    @Data
    public static class Async {

        /** 核心线程数 */
        private int corePoolSize = 8;

        /** 最大线程数 */
        private int maxPoolSize = 32;

        /** 等待队列容量 */
        private int queueCapacity = 1000;

        /** 非核心线程空闲存活时间（秒） */
        private int keepAliveSeconds = 60;
    }

    /**
     * 转发 HTTP 超时参数
     */
    @Data
    public static class Forward {

        /** 建立连接超时时间（毫秒） */
        private int connectTimeout = 3000;

        /** 读取响应超时时间（毫秒） */
        private int readTimeout = 5000;
    }
}
