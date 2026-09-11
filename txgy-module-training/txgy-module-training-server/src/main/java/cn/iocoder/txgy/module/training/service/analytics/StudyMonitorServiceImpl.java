package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 91 学时管控与学习监管 Service 实现（框架骨架）
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class StudyMonitorServiceImpl implements StudyMonitorService {

    @Override
    public PageResult<?> getStudyBehaviorPage(Object pageReqVO) {
        // TODO[模块九-学习行为] 基于学习行为日志表实现分页查询：
        //  1. 结合防刷课规则计算有效学时；2. 区分正常学时与刷课异常标记
        return PageResult.empty();
    }

    @Override
    public Boolean remind(List<Long> userIds) {
        // TODO[模块九-学习督促] 勾选未完成人员后批量推送站内督促消息，并记录消息推送日志
        return Boolean.TRUE;
    }

}
