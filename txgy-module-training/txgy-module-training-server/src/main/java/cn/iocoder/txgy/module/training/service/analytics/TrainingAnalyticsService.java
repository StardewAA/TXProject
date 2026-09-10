package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.module.training.controller.admin.analytics.vo.AnalyticsDashboardRespVO;

/**
 * 数据统计与档案归档 - 统计看板 Service 接口
 *
 * 首期仅提供看板聚合查询；档案归档（生成/锁定/导出）将在后续迭代扩展。
 *
 * @author 云培训学院课题小组
 */
public interface TrainingAnalyticsService {

    /**
     * 获取统计看板数据
     *
     * @return 看板聚合数据
     */
    AnalyticsDashboardRespVO getDashboard();

}
