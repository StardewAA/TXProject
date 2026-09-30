package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssuePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;

import java.util.List;

/**
 * 智慧标书审查 · 问题项服务（管理端复核；个人端只读本人任务的问题清单）。
 *
 * <p>方案依据：§5.1（portal/issue/list）、§5.2（issue/page、issue/review）、§7.1（复核抽屉）。
 */
public interface BidReviewIssueService {

    /** 管理端分页（可按任务/级别/状态过滤）。 */
    PageResult<BidReviewIssueDO> getIssuePage(BidReviewIssuePageReqVO reqVO);

    /** 个人端：本人任务的问题清单（先校验任务归属）。 */
    List<BidReviewIssueDO> getMyIssueList(Long taskId);

    /** 复核：确认 / 忽略 / 标记已整改（写复核人与意见）。 */
    void reviewIssue(BidReviewIssueReviewReqVO reqVO);

    /** 复核后的报告重算：结论与高危计数以"未被忽略"的问题为准。 */
    void refreshTaskVerdict(Long taskId);

    BidReviewIssueRespVO toResp(BidReviewIssueDO issue);

    /**
     * 批量转 RespVO：一次补齐"任务编号/任务名"与"复核人名称"。
     *
     * <p>为什么不用逐条 {@link #toResp}：跨任务的问题列表每行都要显示任务编号与复核人，
     * 逐条解析会产生 2N 次查询；此处按 taskId / reviewerId 各做一次批量解析。
     */
    List<BidReviewIssueRespVO> toRespList(List<BidReviewIssueDO> issues);

    /** 复核进度统计（问题复核页顶部指标）。 */
    BidReviewIssueStatsRespVO getIssueStats();
}
