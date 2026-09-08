package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.DocStatusRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerTrackRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerTrackSummaryRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.CheckinRecordDO;
import cn.iocoder.txgy.framework.common.pojo.PageParam;

import java.util.List;

/**
 * 学员端 - 个人学习轨迹 Service 接口
 *
 * @author 云培训学院课题小组
 */
public interface LearnerTrackService {

    /**
     * 获得我的学习轨迹（分页）
     *
     * @param userId    学员 ID
     * @param mediaType 媒资类型过滤（可选）
     * @param pageParam 分页参数
     */
    PageResult<LearnerTrackRespVO> getMyTracks(Long userId, String mediaType, PageParam pageParam);

    /**
     * 获得我的学习轨迹汇总（学习档案首页）
     */
    LearnerTrackSummaryRespVO getMySummary(Long userId);

    /**
     * 获得文档媒资学习状态（用于恢复续学、完成判定提示）
     */
    DocStatusRespVO getDocStatus(Long userId, Long courseId, Long mediaId);

    /**
     * 获得我的打卡记录（可选课程过滤）
     */
    List<CheckinRecordDO> getMyCheckins(Long userId, Long courseId);

}
