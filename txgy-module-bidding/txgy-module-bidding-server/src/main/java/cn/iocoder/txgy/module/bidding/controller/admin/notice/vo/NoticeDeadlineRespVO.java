package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 截止时间查询出参（05 §3.1 BID-ENT-NOTICE-02，模块内部只读）。
 *
 * <p>字段取自权威数据集 payload 快照（招标公告：NOTICE_SEND_TIME=文件发出、
 * BID_DOC_REFER_END_TIME=澄清/质疑截止、SUBMIT_TIMESTAMP=递交截止；
 * 变更公告以 BID_END_TIME 覆盖递交截止），时间为规范 yyyyMMddHHmmss 字符串；
 * 标段域建表后可改为按标段实体关联查询（当前以 payload 的 BID_SECTION_CODES 匹配）。
 */
@Schema(description = "截止时间查询出参（BID-ENT-NOTICE-02）")
@Data
public class NoticeDeadlineRespVO {

    @Schema(description = "标段编号")
    private String bidSectionCode;
    @Schema(description = "命中公告内部编号")
    private String noticeCode;
    @Schema(description = "命中公告类型（TENDER_NOTICE/CHANGE_NOTICE）")
    private String noticeType;
    @Schema(description = "文件发出时间（NOTICE_SEND_TIME）")
    private String fileIssueTime;
    @Schema(description = "澄清/质疑截止时间（BID_DOC_REFER_END_TIME）")
    private String clarifyDeadline;
    @Schema(description = "递交截止时间（SUBMIT_TIMESTAMP；变更公告取 BID_END_TIME）")
    private String submitDeadline;
    @Schema(description = "开标时间（BID_OPEN_TIME，招标公告有则返回）")
    private String bidOpenTime;

}
