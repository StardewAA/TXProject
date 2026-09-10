package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 93 讲师数据权限 Service 实现（框架骨架）
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class LecturerDataServiceImpl implements LecturerDataService {

    @Override
    public PageResult<?> getLecturerStudentPage(Long lecturerUserId, Object pageReqVO) {
        // TODO[模块九-讲师权限] 仅查询讲师自己创建课程下的学员学习完成情况；
        //  严禁跨课程、企业级档案和报表查看权限（数据权限边界）
        return PageResult.empty();
    }

}
