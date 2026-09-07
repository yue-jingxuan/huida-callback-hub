# 汇答投流回调网关（huida-callback-hub）

通用投流回调网关，统一接收抖音、快手、穿山甲等广告平台的转化回调，完成落库、异步转发。

## 功能特性

- **统一回调入口**：`POST /api/callback/{platformCode}`，全平台一个地址
- **原始报文保全**：完整读取请求体，不解析不丢参数
- **异步转发**：回调立即返回 200，转发在独立线程池异步执行，不阻塞上游
- **链路追踪**：traceId 贯穿接收→落库→转发全链路（MDC + 数据库）
- **状态回写**：转发成功/失败自动更新日志状态

## 架构流程

```
广告平台
   │ POST /api/callback/{platformCode}
   ▼
接收Controller ──► 读原始报文 ──► 查平台配置
   │                                  │
   │ 配置不存在/禁用 ──► 返回200(静默)   │
   ▼                                  ▼
落库(pending)
   │
   ▼
返回200 ──同时──► 异步转发线程池 ──► POST下游
                     │                │
                     │           成功→success
                     │           失败→failed
```

## 技术栈

- Java 21 + Spring Boot 3.3.3
- MyBatis-Plus 3.5.9 + MySQL 8
- Maven

## 快速开始

### 1. 准备数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE huida_callback_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行建表脚本
mysql -u root -p huida_callback_hub < src/main/resources/db/schema.sql
mysql -u root -p huida_callback_hub < src/main/resources/db/data.sql
```

### 2. 修改配置

编辑 `src/main/resources/application.yml`，或通过环境变量注入：

```bash
export MYSQL_HOST=localhost
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your_password
```

### 3. 启动应用

```bash
./mvnw spring-boot:run
```

### 4. 测试

```bash
curl -X POST http://localhost:8080/api/callback/douyin \
  -H "Content-Type: application/json" \
  -d '{"event_id":"test001","click_id":"abc"}'
# 返回 ok
```

查看日志：

```
[回调接收] 收到回调, platformCode=douyin
[回调接收] 回调日志已落库, logId=1
[转发] 开始异步转发, logId=1, forwardUrl=http://127.0.0.1:9000/receive/douyin
[转发] 转发超时或网络异常 ...
```

## 配置说明

`application.yml` 中 `huida.callback` 前缀：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `async.core-pool-size` | 4 | 转发线程池核心线程 |
| `async.max-pool-size` | 8 | 转发线程池最大线程 |
| `async.queue-capacity` | 100 | 转发队列容量 |
| `forward.connect-timeout` | 3000 | 转发连接超时(ms) |
| `forward.read-timeout` | 5000 | 转发读取超时(ms) |

## 回调状态流转

| 状态 | 含义 |
|------|------|
| `pending` | 已落库，等待/正在转发 |
| `success` | 转发成功（下游返回 2xx） |
| `failed` | 转发失败 |

## 项目结构

```
com.huida.callbackhub
├── controller      # 回调接收入口
├── service         # 平台配置/回调日志/异步转发
├── config          # 异步线程池/转发超时/MyBatis自动填充
├── entity          # CallbackLog/PlatformConfig
└── mapper          # MyBatis-Plus Mapper
```

## 设计要点

- **永远返回 200**：回调接口任何情况下都返回 200 "ok"，防止上游平台因错误码重复推送
- **MDC 链路透传**：异步线程通过 TaskDecorator 继承 traceId，日志全程可串联

## 后续版本规划

- v1.1：验签框架、幂等控制、失败重试、Redis 缓存、Docker 部署
- v1.2：CAPI 出站上报（主动调广告平台 API）
- v2：多租户、MQ 化、监控告警

## License

MIT
