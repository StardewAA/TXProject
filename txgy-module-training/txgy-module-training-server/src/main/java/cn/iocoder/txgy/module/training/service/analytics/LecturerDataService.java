package cn.iocoder.txgy.module.training.service.analytics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;

/**
 * 93 讲师数据权限 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface LecturerDataService {

    /**
     * 分页查询讲师自己创建课程下的学员学习完成情况
     *
     * @param lecturerUserId 讲师用户ID
     * @param pageReqVO      分页参数（后续定义专用 PageReqVO）
     * @return 学员学习完成情况分页
     */
    PageResult<?> getLecturerStudentPage(Long lecturerUserId, Object pageReqVO);

}
