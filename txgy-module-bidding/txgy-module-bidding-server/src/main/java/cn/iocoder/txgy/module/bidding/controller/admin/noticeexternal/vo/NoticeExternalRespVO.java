package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：设计文档 v0.6 §7（列表只出主列）；01/05（只外发社会公开字段）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    @Schema(description = "关联项目编号（跟踪维度键，可为 null=未解析到项目）")
    private String projectKey;
    @Schema(description = "项目名称快照（可为 null）")
    private String projectName;
    @Schema(description = "关联招标项目编号（跟踪维度键，可为 null）")
    private String tenderProjectKey;
    @Schema(description = "招标项目名称（一期无数据源可为 null）")
    private String tenderProjectName;
    @Schema(description = "关联标段编号列表（跟踪维度键，可为空数组；有键即可发起 SECTION 关注）")
    private List<String> bidSectionKeys;
    @Schema(description = "标段名称（一期无数据源可为 null/空）")
    private List<String> bidSectionNames;
    @Schema(description = "关联标段编号原文（逗号/分号分隔，兼容旧字段读取）")
    private String bidSectionCodes;

    @Schema(description = "外站公告发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "生效开始")
    private LocalDateTime effectiveFrom;

    @Schema(description = "生效截止")
    private LocalDateTime effectiveTo;

    @Schema(description = "状态：ACTIVE 等（公开列表只会出现 ACTIVE）")
    private String status;

}
