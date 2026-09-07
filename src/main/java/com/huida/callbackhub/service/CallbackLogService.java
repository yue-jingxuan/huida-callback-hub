package com.huida.callbackhub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huida.callbackhub.entity.CallbackLog;

import java.util.List;

/**
 * 回调请求日志业务接口。
 * <p>
 * 回调日志数据量大、实时性要求高，所有查询直接走数据库，不做 Redis 缓存。
 * </p>
 */
public interface CallbackLogService extends IService<CallbackLog> {

    /**
     * 根据链路追踪 ID 查询日志。
     *
     * @param traceId 链路追踪 ID
     * @return 匹配的日志列表，无匹配返回空列表，不会返回 {@code null}
     */
    List<CallbackLog> listByTraceId(String traceId);

    /**
     * 根据广告点击 ID 查询最新一条回调记录。
     *
     * @param clickId 广告点击 ID
     * @return 最新一条记录（按创建时间倒序），不存在返回 {@code null}
     */
    CallbackLog getLatestByClickId(String clickId);

    /**
     * 更新回调结果状态与错误信息。
     *
     * @param id       日志主键
     * @param status   目标状态：pending / success / failed
     * @param errorMsg 错误信息，成功时可传 {@code null}
     * @return 是否更新成功
     */
    boolean updateResult(Long id, String status, String errorMsg);
}
