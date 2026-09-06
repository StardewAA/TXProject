package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：设计文档 v0.6 §7（列表只出主列）；01/05（只外发社会公开字段）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部公告列表行（大厅公开只读；分页/列表不携带大字段原文）。
 */
@Schema(description = "管理后台 - 外部公告列表行 Response")
@Data
public class NoticeExternalRespVO {

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

    @Schema(description = "依法必招：Y/N（NULL=未知）")
    private String legalFlag;

    @Schema(description = "外站自述类型原文")
    private String noticeTypeRaw;

    @Schema(description = "防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN")
    private String industryFlag;

    @Schema(description = "发布单位")
    private String issuerName;

    @Schema(description = "外站公告发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "生效开始")
    private LocalDateTime effectiveFrom;

    @Schema(description = "生效截止")
    private LocalDateTime effectiveTo;

    @Schema(description = "状态：ACTIVE 等（公开列表只会出现 ACTIVE）")
    private String status;

}
