package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssuePageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智慧标书审查问题项 Mapper。
 *
 * <p>规范依据：§5.6（命中 idx_task_severity / idx_task_status）。
 */
@Mapper
public interface BidReviewIssueMapper extends BaseMapperX<BidReviewIssueDO> {

    default List<BidReviewIssueDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<BidReviewIssueDO>()
                .eq(BidReviewIssueDO::getTaskId, taskId)
                .orderByAsc(BidReviewIssueDO::getSeverity)
                .orderByAsc(BidReviewIssueDO::getId));
    }

    default Long selectCountByTaskIdAndSeverity(Long taskId, String severity) {
        return selectCount(new LambdaQueryWrapperX<BidReviewIssueDO>()
                .eq(BidReviewIssueDO::getTaskId, taskId)
                .eq(BidReviewIssueDO::getSeverity, severity));
    }

    default PageResult<BidReviewIssueDO> selectPage(BidReviewIssuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidReviewIssueDO>()
                .eqIfPresent(BidReviewIssueDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(BidReviewIssueDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(BidReviewIssueDO::getCategory, reqVO.getCategory())
                .eqIfPresent(BidReviewIssueDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BidReviewIssueDO::getSource, reqVO.getSource())
                .eqIfPresent(BidReviewIssueDO::getEvidenceLack, reqVO.getEvidenceLack())
                .orderByDesc(BidReviewIssueDO::getId));
    }

    /** 按复核状态计数（「问题复核」页顶部指标用）。 */
    default Long selectCountByStatus(String status) {
        return selectCount(new LambdaQueryWrapperX<BidReviewIssueDO>()
                .eqIfPresent(BidReviewIssueDO::getStatus, status));
    }

    /** 删除任务下的问题项（重跑前清理，保证问题清单与本次规则集一致）。 */
    default int deleteByTaskId(Long taskId) {
        return delete(new LambdaQueryWrapperX<BidReviewIssueDO>()
                .eq(BidReviewIssueDO::getTaskId, taskId));
    }
}
