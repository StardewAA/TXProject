package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerCourseCardRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerCourseDetailRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;

import java.util.List;

/**
 * 学员课程服务（我的课程/课程详情/自助选学/任务进度落库）
 *
 * @author 云培训学院课题小组
 */
public interface LearnerCourseService {

    /**
     * 我的课程卡片列表（按任务创建时间倒序）
     */
    List<LearnerCourseCardRespVO> getMyCourses(Long userId);

    /**
     * 课程学习详情（含媒资目录与逐项学习状态）
     */
    LearnerCourseDetailRespVO getCourseDetail(Long userId, Long courseId);

    /**
     * 自助选学（幂等：重复选学直接返回）
     */
    void enrollSelf(Long userId, Long courseId);

    /**
     * 学习会话结算后刷新任务进度（由会话服务调用）
     *
     * @param progress 计算器最新进度（calc 结果）
     */
    void refreshTaskProgress(LearnerProgressCalculator.TaskProgress progress);

    /**
     * 任务开始时校正任务状态为学习中
     */
    void markTaskLearning(LearnerTaskDO task);

}
