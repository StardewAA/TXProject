package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportDocRespVO;

/**
 * 智慧标书审查 · 报告文档（Word）生成服务。
 *
 * <p>用途：审查完成后「查看完整报告」可下载一份可编辑/可打印的报告文档。
 * <p>实现口径：手写最小 OOXML（docx = zip + document.xml），**不引入 POI/iText 依赖**；
 * 生成后经 FileApi 存为平台文件（拿全局唯一 file_id），并回写 report.docx_file_id 复用（同一任务只生成一次）。
 */
public interface BidReviewReportDocService {

    /**
     * 生成（或复用）审查报告 Word 文档。
     *
     * @param taskId     任务 id
     * @param checkOwner 是否校验任务归属（个人端 true：只能导自己的；管理端 false）
     */
    BidReviewReportDocRespVO getOrCreateDocx(Long taskId, boolean checkOwner);
}
