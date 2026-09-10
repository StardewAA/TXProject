package cn.iocoder.txgy.module.training.service.learning;

import java.util.Map;

/**
 * 学员学习中心 - 全局配置 Service
 *
 * @author 云培训学院课题小组
 */
public interface LearningConfigService {

    // ========== 配置键 ==========

    /**
     * 是否开启直播弹幕
     */
    String KEY_DANMAKU_ENABLED = "danmaku_enabled";
    /**
     * 防挂机验证间隔（分钟）
     */
    String KEY_VERIFY_INTERVAL_MIN = "verify_interval_min";
    /**
     * 心跳上报间隔（秒），约定值，供前端定时器使用
     */
    String KEY_HEARTBEAT_INTERVAL_SECONDS = "heartbeat_interval_seconds";
    /**
     * 单次心跳允许的最大间隔（秒），超过视为异常心跳不计有效学时
     */
    String KEY_MAX_DELTA_SECONDS = "max_delta_seconds";
    /**
     * 心跳超时（秒），超过服务端自动结束会话
     */
    String KEY_SESSION_TIMEOUT_SECONDS = "session_timeout_seconds";
    /**
     * 文档单页最少停留（秒），达到后才计为已覆盖页
     */
    String KEY_DOC_MIN_STAY_SECONDS = "doc_min_stay_seconds";
    /**
     * 视频完成判定比例（有效学时/媒资时长 ≥ 该值即完成）
     */
    String KEY_VIDEO_COMPLETE_RATIO = "video_complete_ratio";
    /**
     * 文档完成判定比例（覆盖页数/总页数 ≥ 该值即完成）
     */
    String KEY_DOC_COMPLETE_RATIO = "doc_complete_ratio";
    /**
     * 打卡最低有效学习秒数
     */
    String KEY_MIN_VALID_SECONDS_CHECKIN = "min_valid_seconds_checkin";

    String getConfig(String key);

    boolean getBoolean(String key, boolean defaultValue);

    int getInt(String key, int defaultValue);

    double getDouble(String key, double defaultValue);

    /**
     * 读取全量配置（含默认值），有序
     */
    Map<String, String> getConfigMap();

    /**
     * 更新配置（键须在白名单内）
     */
    void updateConfig(String key, String value);

}
