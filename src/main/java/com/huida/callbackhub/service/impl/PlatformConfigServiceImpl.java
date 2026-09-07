package com.huida.callbackhub.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huida.callbackhub.entity.PlatformConfig;
import com.huida.callbackhub.mapper.PlatformConfigMapper;
import com.huida.callbackhub.service.PlatformConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 投流平台配置业务实现。
 * <p>
 * v1 版本直接查库，不做 Redis 缓存。
 * 平台配置数据量小，查询频率低，直查库完全够用。
 * </p>
 */
@Slf4j
@Service
public class PlatformConfigServiceImpl extends ServiceImpl<PlatformConfigMapper, PlatformConfig>
        implements PlatformConfigService {

    /**
     * 根据主键查询
     *
     * @param id 主键id
     * @return 平台配置，不存在返回null
     */
    @Override
    public PlatformConfig getConfigById(Long id) {
        if (id == null) {
            return null;
        }
        return super.getById(id);
    }

    /**
     * 根据平台编码查询，网关回调主查询入口
     *
     * @param platformCode 平台编码 douyin / kuaishou / chuanshanjia
     * @return 平台配置，不存在返回null
     */
    @Override
    public PlatformConfig getByPlatformCode(String platformCode) {
        if (!StringUtils.hasText(platformCode)) {
            return null;
        }
        return lambdaQuery()
                .eq(PlatformConfig::getPlatformCode, platformCode)
                .one();
    }

    /**
     * 查询全部平台配置
     */
    @Override
    public List<PlatformConfig> listAll() {
        return super.list();
    }

    /**
     * 查询所有已启用的平台配置
     */
    @Override
    public List<PlatformConfig> listEnabled() {
        return lambdaQuery()
                .eq(PlatformConfig::getEnabled, 1)
                .list();
    }

    /**
     * 新增平台配置，增加platformCode唯一性校验
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createConfig(PlatformConfig config) {
        Objects.requireNonNull(config, "平台配置不能为空");
        if (!StringUtils.hasText(config.getPlatformCode())) {
            throw new IllegalArgumentException("平台编码不能为空");
        }
        // 查重校验，防止重复平台编码；数据库需配套建立唯一索引做并发兜底
        PlatformConfig exist = getByPlatformCode(config.getPlatformCode());
        if (exist != null) {
            throw new IllegalArgumentException("该平台编码已存在：" + config.getPlatformCode());
        }
        // 设置默认值
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        if (config.getWebhookEnabled() == null) {
            config.setWebhookEnabled(0);
        }
        boolean saved = save(config);
        if (saved) {
            log.info("新增平台配置成功, id={}, platformCode={}", config.getId(), config.getPlatformCode());
        }
        return saved;
    }

    /**
     * 更新平台配置，变更平台编码时校验唯一性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateConfig(PlatformConfig config) {
        Objects.requireNonNull(config, "平台配置不能为空");
        if (config.getId() == null) {
            throw new IllegalArgumentException("更新平台配置时主键不能为空");
        }
        // 变更平台编码时查重，排除自身，防止改成已存在的编码；数据库唯一索引做并发兜底
        if (StringUtils.hasText(config.getPlatformCode())) {
            PlatformConfig exist = getByPlatformCode(config.getPlatformCode());
            if (exist != null && !config.getId().equals(exist.getId())) {
                throw new IllegalArgumentException("该平台编码已存在：" + config.getPlatformCode());
            }
        }
        boolean updated = updateById(config);
        if (updated) {
            log.info("更新平台配置成功, id={}, platformCode={}", config.getId(), config.getPlatformCode());
        }
        return updated;
    }

    /**
     * 删除平台配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("删除平台配置时主键不能为空");
        }
        PlatformConfig oldConfig = super.getById(id);
        if (oldConfig == null) {
            return false;
        }
        boolean removed = super.removeById(id);
        if (removed) {
            log.info("删除平台配置成功, id={}, platformCode={}", id, oldConfig.getPlatformCode());
        }
        return removed;
    }
}
