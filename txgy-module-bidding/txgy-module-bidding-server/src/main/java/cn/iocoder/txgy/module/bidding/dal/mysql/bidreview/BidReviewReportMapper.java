package cn.iocoder.txgy.module.bidding.dal.mysql.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewReportDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 智慧标书审查报告 Mapper。
 */
@Mapper
public interface BidReviewReportMapper extends BaseMapperX<BidReviewReportDO> {

    /** 一个任务一份报告（重跑时覆盖生成，历史版正文在 content 表留痕）。 */
    default BidReviewReportDO selectByTaskId(Long taskId) {
        return selectOne(new LambdaQueryWrapperX<BidReviewReportDO>()
                .eq(BidReviewReportDO::getTaskId, taskId)
                .orderByDesc(BidReviewReportDO::getId)
                .last("LIMIT 1"));
    }

    default BidReviewReportDO selectByReportNo(String reportNo) {
        return selectOne(BidReviewReportDO::getReportNo, reportNo);
    }

    /**
     * 清空报告已生成的导出文件指针（重跑时用）。
     *
     * <p><b>为什么需要（2026-09-18 修复）</b>：重跑后报告正文由流水线 {@code updateById} 就地覆盖，
     * 但 MP 的 updateById 只更新非空字段，旧的 docx/pdf 指针会被保留 ——
     * 用户重跑后再点「下载 Word」拿到的会是**上一次**生成的文档。这里显式置 NULL，
     * 强制下一次下载按本次结果重新生成。
     *
     * <p><b>为什么不是软删除整行</b>：{@code report_no = "BRR" + taskId} 会复用，而
     * {@code uk_report_no(report_no, deleted, tenant_id)} 只允许「一行未删 + 一行已删」，
     * 第二次重跑再软删就撞唯一键。实测 task 8 / BRR8 / 租户 2019：
     * {@code Duplicate entry 'BRR8-1-2019' for key 'uk_report_no'} —— 报错发生在
     * 「问题项已软删、报告删不掉、任务未重置」的中间态，表现为重跑 500 且问题复核突然没数据。
     */
    @Update("UPDATE bidding_bid_review_report SET pdf_file_id = NULL, docx_file_id = NULL, docx_file_url = NULL "
            + "WHERE task_id = #{taskId} AND deleted = b'0'")
    int clearGeneratedFilesByTaskId(@Param("taskId") Long taskId);

    /** 报告台账分页（不查 html_content，正文按需用 report/get 取）。 */
    default PageResult<BidReviewReportDO> selectPage(BidReviewReportPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BidReviewReportDO>()
                .likeIfPresent(BidReviewReportDO::getReportNo, reqVO.getReportNo())
                .eqIfPresent(BidReviewReportDO::getVerdict, reqVO.getVerdict())
                .orderByDesc(BidReviewReportDO::getId));
    }
}
