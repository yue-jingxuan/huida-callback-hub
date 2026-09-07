package com.huida.callbackhub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 汇答投流回调网关启动类
 * <p>
 * 负责启动 Spring Boot 应用。异步能力由 {@code CallbackAsyncConfig} 中的
 * {@code @EnableAsync} 开启，用于广告平台回调的异步转发。
 * 通过 {@code @MapperScan} 统一扫描 mapper 包，所有 Mapper 接口不再需要添加 {@code @Mapper} 注解。
 * </p>
 *
 * @author huida
 */
@SpringBootApplication
@MapperScan("com.huida.callbackhub.mapper")
public class CallbackHubApplication {

    /**
     * 应用入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CallbackHubApplication.class, args);
    }
}
