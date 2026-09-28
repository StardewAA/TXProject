package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.controller.portal.learner.vo.ExamEligibilityRespVO;

import java.util.List;

/**
 * 学员端 - 岗位认证考试资格 Service 接口
 *
 * <p>业务口径（按认证等级递进，不依赖学习地图）：
 * 课程与岗位的相关性由系统自行判断（课程适用岗位/标题与工种名匹配），每个认证等级最多 2 门必修；
 * 未标注等级的课程 → 晋升初级工，L1-L2 → 中级工，L2-L3 → 高级工，L3-L4 → 技师，L4-L5 → 高级技师；
 * 完成当前等级必修课程后下一等级解锁（晋升前不可学高级课程）；
 * 每个等级一场线上理论考试（90 分钟），通过即获得该等级认证。</p>
 *
 * @author 云培训学院课题小组
 */
public interface LearnerExamService {

    /**
     * 获得可报考的岗位/工种列表（来自上架课程适用岗位去重）
     *
     * @return 工种名称列表
     */
    List<String> getExamJobs();

    /**
     * 获得岗位认证考试资格（按认证等级分组、递进解锁，每级最多 2 门相关课程）
     *
     * @param userId  学员编号
     * @param jobCode 岗位/工种名称
     * @return 分等级课程清单与各等级考试资格
     */
    ExamEligibilityRespVO getExamEligibility(Long userId, String jobCode);
}
