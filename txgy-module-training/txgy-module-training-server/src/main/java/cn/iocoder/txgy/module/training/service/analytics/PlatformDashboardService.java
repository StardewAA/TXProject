package cn.iocoder.txgy.module.training.service.analytics;

import java.util.Map;

/**
 * 95 全平台看板与治理 Service 接口（仅超级管理员）
 *
 * @author 云培训学院课题小组
 */
public interface PlatformDashboardService {

    /**
     * 全平台大数据看板指标（工种覆盖率/课程访问量/考试通过率/证书总量/VR完成率/人才等级分布）
     *
     * @return 平台级指标数据
     */
    Map<String, Object> getPlatformDashboard();

    /**
     * 导出平台报表
     *
     * @return 说明信息（框架期返回"导出功能开发中"）
     */
    String exportPlatformReport();

    /**
     * 获取租户与企业数据隔离配置
     *
     * @return 隔离配置项
     */
    Map<String, Object> getTenantIsolationConfig();

}
