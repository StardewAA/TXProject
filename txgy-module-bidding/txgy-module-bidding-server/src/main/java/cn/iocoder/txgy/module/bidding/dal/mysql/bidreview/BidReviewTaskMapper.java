package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 智慧标书审查任务 Mapper。
 *
 * <p>规范依据：§4.2 分层、§5.6（索引以必要为限——查询全部命中
 * uk_task_no / uk_idempotent_key / idx_owner_status / idx_bid_sha 四个索引）。
 * <p>设计遵循/针对：个人端查询一律带 owner_user_id（越权防线在 SQL 层，不靠 Service 记忆）。
 */
@Mapper
public interface BidReviewTaskMapper extends BaseMapperX<BidReviewTaskDO> {

    /** 按任务编号取任务（业务唯一键）。 */
    default BidReviewTaskDO selectByTaskNo(String taskNo) {
        return selectOne(BidReviewTaskDO::getTaskNo, taskNo);
    }

    /** 按幂等键取任务（同一幂等键只应产生一个任务）。 */
    default BidReviewTaskDO selectByIdempotentKey(String idempotentKey) {
        return selectOne(BidReviewTaskDO::getIdempotentKey, idempotentKey);
    }

    /** 管理端分页（可按归属人/状态/结论过滤）。 */
    default PageResult<BidReviewTaskDO> selectPage(BidReviewTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidReviewTaskDO>()
                .likeIfPresent(BidReviewTaskDO::getTaskName, reqVO.getTaskName())
                .eqIfPresent(BidReviewTaskDO::getTaskNo, reqVO.getTaskNo())
                .eqIfPresent(BidReviewTaskDO::getOwnerUserId, reqVO.getOwnerUserId())
                .likeIfPresent(BidReviewTaskDO::getOwnerName, reqVO.getOwnerName())
                .eqIfPresent(BidReviewTaskDO::getSourceScene, reqVO.getSourceScene())
                .eqIfPresent(BidReviewTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BidReviewTaskDO::getVerdict, reqVO.getVerdict())
                .betweenIfPresent(BidReviewTaskDO::getCreateTime, reqVO.getBeginTime(), reqVO.getEndTime())
                .orderByDesc(BidReviewTaskDO::getId));
    }

    /** 个人端分页：强制按归属人过滤（个人端只能看自己的）。 */
    default PageResult<BidReviewTaskDO> selectMyPage(Long ownerUserId, BidReviewTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidReviewTaskDO>()
                .eq(BidReviewTaskDO::getOwnerUserId, ownerUserId)
                .likeIfPresent(BidReviewTaskDO::getTaskName, reqVO.getTaskName())
                .eqIfPresent(BidReviewTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(BidReviewTaskDO::getVerdict, reqVO.getVerdict())
                .betweenIfPresent(BidReviewTaskDO::getCreateTime, reqVO.getBeginTime(), reqVO.getEndTime())
                .orderByDesc(BidReviewTaskDO::getId));
    }

    /** 待执行任务（调度兜底用：QUEUED 超时未启动 → 重新投递）。 */
    default List<BidReviewTaskDO> selectQueuedList(int limit) {
        return selectList(new LambdaQueryWrapperX<BidReviewTaskDO>()
                .eq(BidReviewTaskDO::getStatus, "QUEUED")
                .orderByAsc(BidReviewTaskDO::getId)
                .last("LIMIT " + limit));
    }

    /** 执行中任务（超时回收用）。 */
    default List<BidReviewTaskDO> selectRunningList(int limit) {
        return selectList(new LambdaQueryWrapperX<BidReviewTaskDO>()
                .eq(BidReviewTaskDO::getStatus, "RUNNING")
                .orderByAsc(BidReviewTaskDO::getId)
                .last("LIMIT " + limit));
    }

    /** 24 小时内同标书 + 同规则集的成功任务（复用解析与问题清单，省 token）。 */
    default BidReviewTaskDO selectReusable(String bidFileSha256, Long ruleSetId) {
        return selectOne(new LambdaQueryWrapperX<BidReviewTaskDO>()
                .eq(BidReviewTaskDO::getBidFileSha256, bidFileSha256)
                .eqIfPresent(BidReviewTaskDO::getRuleSetId, ruleSetId)
                .eq(BidReviewTaskDO::getStatus, "SUCCESS")
                .ge(BidReviewTaskDO::getCreateTime, java.time.LocalDateTime.now().minusHours(24))
                .orderByDesc(BidReviewTaskDO::getId)
                .last("LIMIT 1"));
    }
}
