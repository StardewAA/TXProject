package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;

import java.util.List;

/**
 * 91 学时管控与学习监管 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface StudyMonitorService {

    /**
     * 分页查询全员学习行为数据（含有效学时/刷课异常标记）
     *
     * @param pageReqVO 分页参数（后续定义专用 PageReqVO）
     * @return 学习行为日志分页
     */
    PageResult<?> getStudyBehaviorPage(Object pageReqVO);

    /**
     * 批量推送站内督促消息（勾选未完成人员）
     *
     * @param userIds 学员用户ID列表
     * @return 推送是否成功
     */
    Boolean remind(List<Long> userIds);

}
