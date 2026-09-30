package cn.iocoder.txgy.module.training.service.learning;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.AdminCourseTrackRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.AssignCandidateRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearnerTaskRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningAssignReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningStatRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningTaskPageReqVO;

import java.util.List;

/**
 * 管理端-学员学习中心 聚合服务接口
 *
 * <p>负责：课程指派、指派候选、任务列表、学习数据统计、单课程学习轨迹。</p>
 *
 * @author 云培训学院课题小组
 */
public interface AdminLearningService {

    /**
     * 课程指派（指派给多位学员；幂等，已存在的任务自动跳过）
     *
     * @param reqVO 指派信息
     * @return 本次新增任务数
     */
    int assign(LearningAssignReqVO reqVO);

    /**
     * 获得指派候选：已上架课程 + 已有学习任务的学员（用于前端选择）
     *
     * @return 候选信息
     */
    AssignCandidateRespVO getAssignCandidates();

    /**
     * 获得学习任务分页（管理端，带实时进度）
     *
     * @param reqVO 分页查询
     * @return 学习任务分页
     */
    PageResult<LearnerTaskRespVO> getTaskPage(LearningTaskPageReqVO reqVO);

    /**
     * 获得学习数据统计（按课程汇总）
     *
     * @return 统计列表
     */
    List<LearningStatRespVO> getLearnStats();

    /**
     * 获得某学员某课程的完整学习轨迹（按媒资/会话明细）
     *
     * @param userId   学员编号
     * @param courseId 课程编号
     * @return 课程学习轨迹
     */
    AdminCourseTrackRespVO getCourseTrack(Long userId, Long courseId);

}
