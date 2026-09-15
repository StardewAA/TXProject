package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：设计文档 v0.6 §7/§7.2（列表只出主列 + 审核/抽取状态列）；《招投标模块开发规范》§3.1（VO 规约）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部公告管理分页行（管理端审核/运营列表）。
 *
 * <p>覆盖公开列表行（NoticeExternalRespVO）并追加管理端维度：status/auditStatus（全量，非只 ACTIVE）、
 * 抽取/结构化状态、AI 行业判定、附件与标段信息等；用于审核页选中记录后执行 approve/reject/take-down/ai-extract/body-structure。
 */
@Schema(description = "管理后台 - 外部公告管理分页行 Response")
@Data
public class NoticeExternalManageRespVO {

    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "来源站 code")
    private String sourceCode;
    @Schema(description = "来源站名称")
    private String sourceName;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "原网址")
    private String origUrl;
    @Schema(description = "内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER")
    private String contentType;
    @Schema(description = "外站自述类型原文")
    private String noticeTypeRaw;
    @Schema(description = "依法必招：Y/N（NULL=未知）")
    private String legalFlag;
    @Schema(description = "防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN")
    private String industryFlag;
    @Schema(description = "行业判定置信度 0~1")
    private java.math.BigDecimal industryConfidence;
    @Schema(description = "发布单位")
    private String issuerName;
    @Schema(description = "项目名称快照")
    private String projectName;
    @Schema(description = "关联标段编号（逗号分隔）")
    private String bidSectionCodes;
    @Schema(description = "展示状态：NEW/ACTIVE/INACTIVE/ARCHIVED")
    private String status;
    @Schema(description = "审核状态：UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN")
    private String auditStatus;
    @Schema(description = "PDF 抽取状态：NONE/SUCCESS/FAIL_EMPTY/FAIL")
    private String extractionStatus;
    @Schema(description = "AI 结构化抽取状态：NONE/SUCCESS/FAILED")
    private String aiExtractionStatus;
    @Schema(description = "正文结构化状态：NONE/SUCCESS/FAILED")
    private String bodyBlocksStatus;
    @Schema(description = "正文结构化策略码：HTML_STRUCTURE/AI_FALLBACK")
    private String bodyParserCode;
    @Schema(description = "外站公告发布时间")
    private LocalDateTime publishTime;
    @Schema(description = "生效开始")
    private LocalDateTime effectiveFrom;
    @Schema(description = "生效截止")
    private LocalDateTime effectiveTo;
    @Schema(description = "抓取次数")
    private Integer fetchCount;
    @Schema(description = "最近抓取时间")
    private LocalDateTime lastFetchTime;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
