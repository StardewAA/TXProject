package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewAiLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智慧标书审查 AI 调用流水 Mapper。
 *
 * <p>方案依据：§6.4（失败率告警：近 1 小时 FAILED 占比 > 50% 或出现 401 时告警）。
 */
@Mapper
public interface BidReviewAiLogMapper extends BaseMapperX<BidReviewAiLogDO> {

    default List<BidReviewAiLogDO> selectListByTaskId(Long taskId) {
        return selectList(new LambdaQueryWrapperX<BidReviewAiLogDO>()
                .eq(BidReviewAiLogDO::getTaskId, taskId)
                .orderByAsc(BidReviewAiLogDO::getId));
    }

    /** 近 N 小时某状态的调用数（自检/告警用）。 */
    default Long selectCountByStatusSince(String status, int hours) {
        return selectCount(new LambdaQueryWrapperX<BidReviewAiLogDO>()
                .eq(BidReviewAiLogDO::getStatus, status)
                .ge(BidReviewAiLogDO::getCreateTime, LocalDateTime.now().minusHours(hours)));
    }

    /** 近 N 小时全部调用数。 */
    default Long selectCountSince(int hours) {
        return selectCount(new LambdaQueryWrapperX<BidReviewAiLogDO>()
                .ge(BidReviewAiLogDO::getCreateTime, LocalDateTime.now().minusHours(hours)));
    }
}
