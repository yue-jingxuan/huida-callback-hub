# 汇答投流回调网关（huida-callback-hub）

通用投流回调网关，统一接收抖音、快手、穿山甲等广告平台的转化回调，完成验签、幂等落库、异步转发、失败自动重试。

## 功能特性

- **统一回调入口**：`POST /api/callback/{platformCode}`，全平台一个地址
- **原始报文保全**：完整读取请求体，不解析不丢参数
- **幂等控制**：基于 `event_id` 唯一索引，重复推送自动拦截
- **验签框架**：策略模式按平台分发，已实现抖音示例验签器，新平台只需新增实现类
- **异步转发**：回调立即返回 200，转发在独立线程池异步执行，不阻塞上游
- **失败重试**：定时任务扫描失败记录自动重试，超限转死信
- **链路追踪**：traceId 贯穿接收→落库→转发全链路（MDC + 数据库）
- **平台配置缓存**：Redis 缓存平台配置，事务提交后失效，防脏读

## 架构流程

```
广告平台
   │ POST /api/callback/{platformCode}
   ▼
接收Controller ──► 读原始报文 ──► 查平台配置(缓存)
   │                                  │
   │ 配置不存在/禁用 ──► 返回200(静默)   │
   ▼                                  ▼
验签(策略分发) ──失败──► 返回200(静默)
   │ 通过
   ▼
幂等落库(pending) ──重复──► 返回200(拦截)
   │ 成功
   ▼
返回200 ──同时──► 异步转发线程池 ──► POST下游
                     │                │
                     │           成功→success
                     │           失败→failed
                     ▼
              重试定时任务(60s) ──► 重新转发
                     │
                超过3次 ──► dead死信
```

## 技术栈

- Java 21 + Spring Boot 3.3
- MyBatis-Plus 3.5.9 + MySQL 8
- Redis（Lettuce）
- Maven

## 快速开始

### 方式一：Docker Compose（推荐）

```bash
docker compose up -d
```

自动启动 MySQL + Redis + 应用，建表脚本和示例配置自动执行。

验证：

```bash
curl -X POST http://localhost:8080/api/callback/douyin \
  -H "Content-Type: application/json" \
  -d '{"event_id":"test001","click_id":"abc"}'
# 返回 ok
```

### 方式二：本地运行

1. 准备 MySQL、Redis，执行建表脚本：
   - `src/main/resources/db/schema.sql`
   - `src/main/resources/db/data.sql`（示例配置）
2. 按需修改 `application.yml` 或通过环境变量注入：
   - `MYSQL_HOST` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`
   - `REDIS_HOST` / `REDIS_PASSWORD`
3. 启动：

```bash
mvnw spring-boot:run
```

## 配置说明

`application.yml` 中 `huida.callback` 前缀：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `cache.single-ttl` | 1800 | 单条配置缓存时长(秒) |
| `cache.list-ttl` | 480 | 列表缓存时长(秒) |
| `cache.null-ttl` | 180 | 空值占位时长(秒)，防穿透 |
| `async.core-pool-size` | 8 | 转发线程池核心线程 |
| `async.max-pool-size` | 32 | 转发线程池最大线程 |
| `async.queue-capacity` | 1000 | 转发队列容量 |
| `forward.connect-timeout` | 3000 | 转发连接超时(ms) |
| `forward.read-timeout` | 5000 | 转发读取超时(ms) |
| `retry.enabled` | true | 是否启用重试任务 |
| `retry.interval-ms` | 60000 | 重试扫描间隔(ms) |
| `retry.batch-size` | 100 | 单轮最大处理条数 |
| `retry.max-retry` | 3 | 最大重试次数，超限转死信 |

## 接入新平台

1. 数据库 `platform_config` 插入平台配置（`platform_code`、`target_url`、`config_json`）
2. 如需验签，在 `sign.impl` 包新增验签器：

```java
@Component
public class KuaishouSignVerifier implements SignVerifier {
    @Override
    public String platform() { return "kuaishou"; }

    @Override
    public boolean verify(HttpServletRequest request, String rawBody, PlatformConfig config) {
        // 按快手验签规则实现
        return true;
    }
}
```

无需修改任何调用方代码，验签分发器自动注册。未实现验签器的平台默认放行（记录告警日志）。

## 回调状态流转

| 状态 | 含义 |
|------|------|
| `pending` | 已落库，等待/正在转发 |
| `success` | 转发成功（下游返回 2xx） |
| `failed` | 转发失败，等待重试任务处理 |
| `dead` | 超过最大重试次数，需人工介入 |

## 项目结构

```
com.huida.callbackhub
├── controller      # 回调接收入口
├── service         # 平台配置(带缓存)/回调日志/异步转发
├── sign            # 验签框架(接口+分发器+平台实现)
├── task            # 失败重试定时任务
├── config          # Redis缓存/异步线程池/重试配置
├── common          # 统一响应/全局异常
├── entity          # CallbackLog/PlatformConfig
└── mapper          # MyBatis-Plus Mapper
```

## 设计要点

- **永远返回 200**：回调接口任何情况下都返回 200 "ok"，防止上游平台因错误码重复推送
- **事务后清缓存**：配置变更在事务提交后才失效 Redis 缓存，避免脏数据回填
- **MDC 链路透传**：异步线程通过 TaskDecorator 继承 traceId，日志全程可串联
- **优雅降级**：Redis 故障时配置查询自动回源数据库

## License

MIT
