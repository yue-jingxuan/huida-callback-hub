package com.huida.callbackhub.service;

/**
 * 回调异步转发服务。
 * <p>
 * 负责将已落库的回调报文异步转发到下游地址，并根据转发结果更新日志状态。
 * 转发失败不在本服务内重试，重试由后续独立开发的定时任务负责。
 * </p>
 */
public interface CallbackForwardAsyncService {

    /**
     * 异步转发回调报文。
     * <p>
     * 将指定日志记录的原始报文 POST 转发到目标地址：
     * <ul>
     *   <li>转发成功（下游返回 2xx）：更新状态为 success</li>
     *   <li>转发失败（超时、非 2xx、网络异常等）：更新状态为 failed 并记录错误信息</li>
     * </ul>
     * 本方法不抛出异常，所有异常内部捕获并落库。
     * </p>
     *
     * @param logId      回调日志 ID
     * @param forwardUrl 下游转发地址
     */
    void forwardAsync(Long logId, String forwardUrl);
}
