package com.huida.callbackhub.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huida.callbackhub.entity.PlatformConfig;
import java.util.List;

/**
 * 投流平台配置接口
 */
public interface PlatformConfigService extends IService<PlatformConfig> {

    /**
     * 根据主键查询
     * @param id 主键
     * @return 平台配置
     */
    PlatformConfig getConfigById(Long id);

    /**
     * 根据平台编码查询（网关回调核心查询入口）
     * @param platformCode 平台编码：douyin / kuaishou / chuanshanjia
     * @return 平台配置
     */
    PlatformConfig getByPlatformCode(String platformCode);

    /**
     * 查询全部平台配置
     * @return 全部列表
     */
    List<PlatformConfig> listAll();

    /**
     * 查询已启用的平台列表
     * @return 启用列表
     */
    List<PlatformConfig> listEnabled();

    /**
     * 新增平台配置
     * @param config 配置实体
     * @return 是否成功
     */
    boolean createConfig(PlatformConfig config);

    /**
     * 更新平台配置
     * @param config 配置实体
     * @return 是否成功
     */
    boolean updateConfig(PlatformConfig config);

    /**
     * 删除平台配置
     * @param id 主键id
     * @return 是否成功
     */
    boolean removeConfig(Long id);

}
