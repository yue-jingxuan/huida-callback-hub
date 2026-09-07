package com.huida.callbackhub.controller;

import com.huida.callbackhub.entity.CallbackLog;
import com.huida.callbackhub.entity.PlatformConfig;
import com.huida.callbackhub.service.CallbackForwardAsyncService;
import com.huida.callbackhub.service.CallbackLogService;
import com.huida.callbackhub.service.PlatformConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * 投流回调统一接收入口。
 * <p>
 * 处理流程：
 * <ol>
 *   <li>读取完整原始请求体（不解析、不丢参数）</li>
 *   <li>生成 traceId 放入 MDC 用于链路追踪（异步转发线程通过 TaskDecorator 继承）</li>
 *   <li>查询平台配置，配置不存在/未开启时静默返回 200，防止上游平台重复推送</li>
 *   <li>回调日志落库，初始状态 pending</li>
 *   <li>立即返回 200，转发任务异步执行，不阻塞 HTTP 响应</li>
 * </ol>
 * 注意：验签、幂等、重试定时任务后续版本单独开发。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/callback")
@RequiredArgsConstructor
public class CallbackReceiveController {

    /** MDC 中链路追踪 ID 的 key，与日志 pattern 中的 %X{traceId} 对应 */
    private static final String MDC_TRACE_ID = "traceId";

    /** 平台配置服务 */
    private final PlatformConfigService platformConfigService;

    /** 回调日志服务 */
    private final CallbackLogService callbackLogService;

    /** 回调异步转发服务 */
    private final CallbackForwardAsyncService callbackForwardAsyncService;

    /**
     * 广告平台回调统一接收入口。
     * <p>
     * 无论内部处理成功与否，一律返回 200 "ok"，
     * 避免上游平台因收到错误码而重复推送回调。
     * </p>
     *
     * @param platformCode 平台编码：douyin / kuaishou / chuanshanjia
     * @param request      原始 HTTP 请求
     * @return 固定返回 "ok"
     */
    @PostMapping("/{platformCode}")
    public String receive(@PathVariable("platformCode") String platformCode,
                          HttpServletRequest request) {
        // 1. 生成链路追踪 ID：UUID 去掉横杠，放入 MDC，本线程所有日志自动携带
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_TRACE_ID, traceId);
        try {
            log.info("[回调接收] 收到回调, platformCode={}", platformCode);

            // 2. 读取完整原始请求体，不做任何解析，保证回调参数不丢失
            String rawBody = readRawBody(request);

            // 3. 查询平台配置（直查库）
            PlatformConfig config = platformConfigService.getByPlatformCode(platformCode);

            // 平台不存在：静默丢弃，不落库不转发，防止上游重复推送
            if (config == null) {
                log.warn("[回调接收] 平台配置不存在, 忽略本次回调, platformCode={}", platformCode);
                return "ok";
            }

            // 平台总开关关闭：静默丢弃，不落库
            if (!Objects.equals(config.getEnabled(), 1)) {
                log.warn("[回调接收] 平台已禁用, 忽略本次回调, platformCode={}", platformCode);
                return "ok";
            }

            // 4. 组装回调日志（模式 2：Webhook 入站转发）
            CallbackLog callbackLog = new CallbackLog();
            callbackLog.setPlatform(platformCode);
            callbackLog.setModeType(2);
            callbackLog.setRawBody(rawBody);
            callbackLog.setTraceId(traceId);
            callbackLog.setRetryCount(0);
            callbackLog.setTargetUrl(config.getTargetUrl());

            // 5. 判断 Webhook 转发开关
            if (!Objects.equals(config.getWebhookEnabled(), 1)) {
                // 转发关闭：仅保存日志留痕，状态记为 success（接收成功，无需转发）
                callbackLog.setStatus("success");
                callbackLogService.save(callbackLog);
                log.info("[回调接收] webhook转发关闭, 仅保存回调日志, logId={}", callbackLog.getId());
                return "ok";
            }

            // 6. 转发开启：落库 pending，提交异步转发任务后立即返回
            callbackLog.setStatus("pending");
            callbackLogService.save(callbackLog);
            log.info("[回调接收] 回调日志已落库, logId={}", callbackLog.getId());

            String forwardUrl = config.getTargetUrl();
            if (StringUtils.hasText(forwardUrl)) {
                callbackForwardAsyncService.forwardAsync(callbackLog.getId(), forwardUrl);
            } else {
                // 未配置转发地址：直接标记失败，等待后续重试或人工处理
                callbackLogService.updateResult(callbackLog.getId(), "failed", "平台未配置转发地址targetUrl");
                log.warn("[回调接收] 平台未配置转发地址, 无法转发, platformCode={}", platformCode);
            }
        } catch (Exception e) {
            // 内部异常只记录日志，对外仍返回 200，防止上游重复推送
            log.error("[回调接收] 处理回调发生异常, platformCode={}", platformCode, e);
        } finally {
            // 清理 MDC，防止线程池复用导致 traceId 串号
            MDC.remove(MDC_TRACE_ID);
        }
        return "ok";
    }

    /**
     * 读取 HttpServletRequest 完整原始请求体。
     * <p>
     * 按请求声明的字符集解码，未声明或字符集非法时默认 UTF-8，保证报文原样保留。
     * </p>
     *
     * @param request HTTP 请求
     * @return 原始请求体字符串
     * @throws IOException 读取流失败
     */
    private String readRawBody(HttpServletRequest request) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        String encoding = request.getCharacterEncoding();
        if (StringUtils.hasText(encoding)) {
            try {
                charset = Charset.forName(encoding);
            } catch (Exception e) {
                // 非法字符集名时降级为 UTF-8，避免整条回调被丢弃
                log.warn("[回调接收] 请求字符集非法, 降级为UTF-8, encoding={}", encoding);
            }
        }
        return new String(request.getInputStream().readAllBytes(), charset);
    }
}
