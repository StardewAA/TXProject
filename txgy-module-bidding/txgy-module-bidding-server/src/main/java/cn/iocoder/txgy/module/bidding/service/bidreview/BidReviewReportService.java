package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageRespVO;
import cn.iocoder.txgy.framework.common.pojo.PageResult;

/**
 * 智慧标书审查 · 报告服务（只读 + 导出内容）。
 *
 * <p>方案依据：§5.1（portal/report）、§5.2（report/get、report/export）。
 * 报告生成在流水线内完成（保证与问题清单同事务口径），本服务只负责读取与导出内容。
 */
public interface BidReviewReportService {

    /** 按任务取报告（个人端先校验归属，管理端不校验）。 */
    BidReviewReportRespVO getReportByTaskId(Long taskId, boolean checkOwner);

    /** 导出内容（HTML；PDF/Word 渲染留 P1，见方案 §8 P1）。 */
    String exportHtml(Long taskId, boolean checkOwner);

    /** 报告台账分页（管理端「报告与统计」页）。 */
    PageResult<BidReviewReportPageRespVO> getReportPage(BidReviewReportPageReqVO reqVO);
}
