package com.huida.callbackhub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huida.callbackhub.entity.CallbackLog;
import com.huida.callbackhub.mapper.CallbackLogMapper;
import com.huida.callbackhub.service.CallbackLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 回调请求日志业务实现。
 * <p>
 * 回调日志不做 Redis 缓存，所有读写直接落库。
 * </p>
 */
@Slf4j
@Service
public class CallbackLogServiceImpl extends ServiceImpl<CallbackLogMapper, CallbackLog>
        implements CallbackLogService {

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CallbackLog> listByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return Collections.emptyList();
        }
        // 按创建时间倒序，最近的在前
        return lambdaQuery()
                .eq(CallbackLog::getTraceId, traceId)
                .orderByDesc(CallbackLog::getCreatedAt)
                .list();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CallbackLog getLatestByClickId(String clickId) {
        if (!StringUtils.hasText(clickId)) {
            return null;
        }
        // 取最新一条：按创建时间、主键倒序，LIMIT 1
        return lambdaQuery()
                .eq(CallbackLog::getClickId, clickId)
                .orderByDesc(CallbackLog::getCreatedAt)
                .orderByDesc(CallbackLog::getId)
                .last("LIMIT 1")
                .one();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean updateResult(Long id, String status, String errorMsg) {
        if (id == null || !StringUtils.hasText(status)) {
            return false;
        }
        boolean updated = lambdaUpdate()
                .eq(CallbackLog::getId, id)
                .set(CallbackLog::getStatus, status)
                .set(CallbackLog::getErrorMsg, errorMsg)
                .update();
        if (updated) {
            log.info("回调日志状态更新成功, id={}, status={}", id, status);
        }
        return updated;
    }
}
