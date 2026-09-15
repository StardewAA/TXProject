package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// [依据] 前端配合说明 BR-7（P4 前置）：审核页“读完正文再 approve”——全状态管理详情预览端点出参；
// 预览内容含净化正文（contentHtmlSafe）/正文结构化块/附件病毒状态/PDF 下载/抽取摘要；公开详情只放行 ACTIVE+SOCIAL，本 VO 供审核全状态使用。

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 外部公告管理详情 Response（全状态预览，审核/运营用）")
@Data
public class NoticeExternalManageDetailRespVO {

    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "来源站 code")
    private String sourceCode;
    @Schema(description = "来源站名称")
    private String sourceName;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "原网址（溯源/跳转）")
    private String origUrl;
    @Schema(description = "内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER")
    private String contentType;
    @Schema(description = "外站自述类型原文")
    private String noticeTypeRaw;
    @Schema(description = "依法必招：Y/N/NULL=未知")
    private String legalFlag;
    @Schema(description = "防腐保温行业判定：BELONGS/NOT_BELONGS/UNCERTAIN")
    private String industryFlag;
    @Schema(description = "行业判定置信度")
    private BigDecimal industryConfidence;
    @Schema(description = "展示状态：NEW/ACTIVE/INACTIVE/ARCHIVED（全状态可见）")
    private String status;
    @Schema(description = "审核状态：UNREVIEWED/APPROVED/REJECTED/TAKEN_DOWN")
    private String auditStatus;
    @Schema(description = "公开范围：SOCIAL/INTERNAL")
    private String publicScope;
    @Schema(description = "HTML 净化后内容（jsoup 白名单；审核预览正文渲染此值）")
    private String contentHtmlSafe;
    @Schema(description = "正文结构化状态：NONE/SUCCESS/FAILED")
    private String bodyBlocksStatus;
    @Schema(description = "正文结构化策略码：HTML_STRUCTURE/AI_FALLBACK")
    private String bodyParserCode;
    @Schema(description = "正文结构化块（JSON 数组，SUCCESS 时下发；块渲染优先于 contentHtmlSafe）")
    private String bodyBlocksJson;
    @Schema(description = "PDF 原文件 file_id（仅 PDF）")
    private Long pdfFileId;
    @Schema(description = "PDF 原文件哈希（仅 PDF）")
    private String pdfSha256;
    @Schema(description = "PDF 原文件大小（仅 PDF）")
    private Long pdfSize;
    @Schema(description = "PDF 下载地址（FileApi 签名接入后回填）")
    private String pdfDownloadUrl;
    @Schema(description = "PDF 抽取状态：NONE/SUCCESS/FAIL_EMPTY/FAIL")
    private String extractionStatus;
    @Schema(description = "抽取正文摘要（extraction_text 前 200 字符，便于审核速览；无=null）")
    private String extractionSummary;
    @Schema(description = "AI 结构化抽取状态：NONE/SUCCESS/FAILED")
    private String aiExtractionStatus;
    @Schema(description = "AI 结构化字段数（ai_meta_json 元字段计数；无=null）")
    private Integer aiMetaFieldCount;
    @Schema(description = "发布单位")
    private String issuerName;
    @Schema(description = "关联项目编号（跟踪维度键）")
    private String projectKey;
    @Schema(description = "项目名称快照")
    private String projectName;
    @Schema(description = "关联招标项目编号（跟踪维度键）")
    private String tenderProjectKey;
    @Schema(description = "关联标段编号列表（跟踪维度键）")
    private List<String> bidSectionKeys;
    @Schema(description = "关联标段编号原文")
    private String bidSectionCodes;
    @Schema(description = "外站公告发布时间")
    private LocalDateTime publishTime;
    @Schema(description = "生效开始/截止")
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @Schema(description = "抓取次数/最近抓取时间")
    private Integer fetchCount;
    private LocalDateTime lastFetchTime;
    @Schema(description = "入库/更新时间（审计）")
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Schema(description = "附件下载列表（INFECTED 的附件不下发 downloadUrl，virusStatus 照回）")
    private List<Attachment> attachments;

    @Schema(description = "附件（下载地址=FileApi 签名；origDownloadUrl 为外站原始链接溯源）")
    @Data
    public static class Attachment {

        @Schema(description = "附件记录 id")
        private Long id;
        @Schema(description = "文件名")
        private String name;
        @Schema(description = "扩展名")
        private String ext;
        @Schema(description = "FileApi file_id")
        private Long fileId;
        @Schema(description = "文件哈希")
        private String sha256;
        @Schema(description = "文件大小（字节）")
        private Long size;
        @Schema(description = "下载地址（FileApi 签名；INFECTED 不下发）")
        private String downloadUrl;
        @Schema(description = "外站原始下载链接（溯源）")
        private String origDownloadUrl;
        @Schema(description = "病毒扫描状态：PENDING/CLEAN/INFECTED")
        private String virusStatus;
    }

}
