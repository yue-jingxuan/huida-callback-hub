package com.huida.callbackhub.service.impl;

import com.huida.callbackhub.entity.CallbackLog;
import com.huida.callbackhub.service.CallbackForwardAsyncService;
import com.huida.callbackhub.service.CallbackLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * 回调异步转发服务实现。
 * <p>
 * 使用 {@code @Async} 在 {@code callbackTaskExecutor} 线程池中执行转发，
 * 不阻塞回调接收的 HTTP 响应。转发结果通过 {@link CallbackLogService#updateResult}
 * 回写日志状态。本服务不做重试，重试由后续独立开发的定时任务负责。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackForwardAsyncServiceImpl implements CallbackForwardAsyncService {

    /** 错误信息入库最大长度，与表字段 error_msg varchar(500) 对齐 */
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    /** 回调日志服务 */
    private final CallbackLogService callbackLogService;

    /** 转发专用 RestTemplate（已配置连接/读取超时） */
    private final RestTemplate forwardRestTemplate;

    /**
     * 异步转发回调报文，详见接口注释。
     *
     * @param logId      回调日志 ID
     * @param forwardUrl 下游转发地址
     */
    @Override
    @Async("callbackTaskExecutor")
    public void forwardAsync(Long logId, String forwardUrl) {
        log.info("[转发] 开始异步转发, logId={}, forwardUrl={}", logId, forwardUrl);
        try {
            // 1. 取出日志记录与原始报文
            CallbackLog callbackLog = callbackLogService.getById(logId);
            if (callbackLog == null) {
                log.error("[转发] 日志记录不存在, 终止转发, logId={}", logId);
                return;
            }
            String rawBody = callbackLog.getRawBody();

            // 2. 组装请求：原样 POST 转发原始报文，保持 JSON 内容类型
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(rawBody, headers);

            // 3. 执行转发
            ResponseEntity<String> response = forwardRestTemplate.postForEntity(forwardUrl, request, String.class);

            // 4. 根据下游响应更新状态
            if (response.getStatusCode().is2xxSuccessful()) {
                callbackLogService.updateResult(logId, "success", null);
                log.info("[转发] 转发成功, logId={}, traceId={}, 下游状态码={}",
                        logId, callbackLog.getTraceId(), response.getStatusCode().value());
            } else {
                String errorMsg = "下游返回非2xx状态码: " + response.getStatusCode().value();
                callbackLogService.updateResult(logId, "failed", truncate(errorMsg));
                log.warn("[转发] 转发失败, logId={}, traceId={}, {}",
                        logId, callbackLog.getTraceId(), errorMsg);
            }
        } catch (ResourceAccessException e) {
            // 连接超时 / 读取超时 / 网络不可达
            String errorMsg = "转发网络异常: " + e.getMessage();
            callbackLogService.updateResult(logId, "failed", truncate(errorMsg));
            log.error("[转发] 转发超时或网络异常, logId={}, forwardUrl={}", logId, forwardUrl, e);
        } catch (Exception e) {
            // 其他异常（下游 4xx/5xx 抛出的 RestClientException、序列化异常等）
            String errorMsg = "转发异常: " + e.getMessage();
            callbackLogService.updateResult(logId, "failed", truncate(errorMsg));
            log.error("[转发] 转发发生未知异常, logId={}, forwardUrl={}", logId, forwardUrl, e);
        }
    }

    /**
     * 截断错误信息，防止超出 error_msg 字段长度。
     *
     * @param errorMsg 原始错误信息
     * @return 截断后的错误信息
     */
    private String truncate(String errorMsg) {
        if (errorMsg == null) {
            return null;
        }
        return errorMsg.length() > ERROR_MSG_MAX_LENGTH
                ? errorMsg.substring(0, ERROR_MSG_MAX_LENGTH)
                : errorMsg;
    }
}
