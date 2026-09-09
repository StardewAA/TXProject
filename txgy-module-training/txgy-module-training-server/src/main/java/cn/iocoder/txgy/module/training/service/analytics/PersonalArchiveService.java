package cn.iocoder.txgy.module.training.service.analytics;

import java.util.Map;

/**
 * 92 个人学习档案 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface PersonalArchiveService {

    /**
     * 获取当前学员个人档案总览（学时明细/考试记录/证书/VR记录/成长进度）
     *
     * @param userId 学员用户ID
     * @return 档案总览数据
     */
    Map<String, Object> getOverview(Long userId);

    /**
     * 证书到期提醒（识别即将到期/需复审证书并推送消息）
     *
     * @param userId 学员用户ID
     * @return 提醒是否推送成功
     */
    Boolean remindCertExpire(Long userId);

}
