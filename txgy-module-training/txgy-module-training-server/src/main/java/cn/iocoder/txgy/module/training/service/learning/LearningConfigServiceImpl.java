package cn.iocoder.txgy.module.training.service.learning;

import cn.iocoder.txgy.module.training.dal.dataobject.learning.GlobalConfigDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.GlobalConfigMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 学员学习中心 - 全局配置 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class LearningConfigServiceImpl implements LearningConfigService {

    /**
     * 默认配置（顺序即展示顺序）。允许修改的键仅限 DEFAULT_CONFIGS 内。
     */
    private static final Map<String, String> DEFAULT_CONFIGS = new LinkedHashMap<>();

    static {
        DEFAULT_CONFIGS.put(KEY_DANMAKU_ENABLED, "true");
        DEFAULT_CONFIGS.put(KEY_VERIFY_INTERVAL_MIN, "1");
        DEFAULT_CONFIGS.put(KEY_HEARTBEAT_INTERVAL_SECONDS, "5");
        DEFAULT_CONFIGS.put(KEY_MAX_DELTA_SECONDS, "15");
        DEFAULT_CONFIGS.put(KEY_SESSION_TIMEOUT_SECONDS, "30");
        DEFAULT_CONFIGS.put(KEY_DOC_MIN_STAY_SECONDS, "5");
        DEFAULT_CONFIGS.put(KEY_VIDEO_COMPLETE_RATIO, "0.9");
        DEFAULT_CONFIGS.put(KEY_DOC_COMPLETE_RATIO, "0.8");
        DEFAULT_CONFIGS.put(KEY_MIN_VALID_SECONDS_CHECKIN, "15");
    }

    @Resource
    private GlobalConfigMapper globalConfigMapper;

    @Override
    public String getConfig(String key) {
        if (!DEFAULT_CONFIGS.containsKey(key)) {
            return null;
        }
        GlobalConfigDO config = globalConfigMapper.selectByKey(key);
        return config != null && StringUtils.hasText(config.getConfigValue())
                ? config.getConfigValue() : DEFAULT_CONFIGS.get(key);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getConfig(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String value = getConfig(key);
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        String value = getConfig(key);
        try {
            return value == null ? defaultValue : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Map<String, String> getConfigMap() {
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULT_CONFIGS.forEach((key, defaultValue) -> result.put(key, getConfig(key)));
        return result;
    }

    @Override
    public void updateConfig(String key, String value) {
        if (!DEFAULT_CONFIGS.containsKey(key)) {
            throw exception(ErrorCodeConstants.LEARNER_CONFIG_KEY_INVALID);
        }
        GlobalConfigDO config = globalConfigMapper.selectByKey(key);
        if (config == null) {
            GlobalConfigDO insert = new GlobalConfigDO();
            insert.setConfigKey(key);
            insert.setConfigValue(value);
            insert.setRemark("学员学习中心全局配置");
            globalConfigMapper.insert(insert);
            return;
        }
        GlobalConfigDO update = new GlobalConfigDO();
        update.setId(config.getId());
        update.setConfigValue(value);
        globalConfigMapper.updateById(update);
        log.info("[updateConfig][配置 {} -> {}]", key, value);
    }

    /**
     * 全量键列表（供校验与导出）
     */
    public static List<String> getKeys() {
        return List.copyOf(DEFAULT_CONFIGS.keySet());
    }

}
