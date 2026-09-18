package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo;
// 规范依据：设计文档 v0.6 §7/§14.2（HTML 原样+origUrl / PDF JS预览+下载+origUrl / 附件下载列表）；红线 8

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部公告详情（大厅公开只读，三形态）。
 *
 * <p>HTML：contentHtmlSafe 净化版（公开展示必用）；PDF：pdfFileId/pdfSha256/pdfSize（前端 JS 预览，
 * origUrl 与下载走 FileApi 签名 downloadUrl）；附件：attachments 逐条下载地址。
 * 安全评审意见：原样 HTML 留档仅存库（供抽取/审计），不随公开匿名接口下发。
 */
@Schema(description = "管理后台 - 外部公告详情 Response")
@Data
public class NoticeExternalDetailRespVO {

    @Schema(description = "主键 ID")
    private Long id;
    @Schema(description = "来源站 code")
    private String sourceCode;
    @Schema(description = "来源站名称")
    private String sourceName;
    @Schema(description = "标题")
    private String title;
    @Schema(description = "原网址（必带）")
    private String origUrl;
    @Schema(description = "内容形态：HTML/PDF/ATTACHMENT/IMAGE/OTHER")
    private String contentType;
    @Schema(description = "HTML 原文（预留字段：留档原样不下发公开匿名接口，恒为 null；公开渲染一律用 contentHtmlSafe）")
    private String contentHtml;
    @Schema(description = "HTML 净化后内容（jsoup 白名单去 script/事件属性/iframe 等，仅 contentType=HTML，建议前端渲染此值）")
    private String contentHtmlSafe;
    @Schema(description = "PDF 原文件 file_id（仅 PDF）")
    private Long pdfFileId;
    @Schema(description = "PDF 原文件哈希（仅 PDF）")
    private String pdfSha256;
    @Schema(description = "PDF 原文件大小（仅 PDF）")
    private Long pdfSize;
    @Schema(description = "PDF 下载地址（FileApi 签名，仅 PDF）")
    private String pdfDownloadUrl;
    @Schema(description = "PDF 抽取状态（NONE/SUCCESS/FAIL_EMPTY/FAIL）")
    private String extractionStatus;
    @Schema(description = "外站自述类型原文")
    private String noticeTypeRaw;
    @Schema(description = "依法必招标记：Y/N/NULL=未知")
    private String legalFlag;
    @Schema(description = "防腐保温行业判定")
    private String industryFlag;
    @Schema(description = "正文结构化策略码：HTML_STRUCTURE/AI_FALLBACK（NONE=未结构化，按 contentHtmlSafe 原文渲染）")
    private String bodyParserCode;
    @Schema(description = "正文结构化状态：NONE/SUCCESS/FAILED")
    private String bodyBlocksStatus;
    @Schema(description = "正文结构化块（JSON 数组：[{type:heading/paragraph/table/list,...}]；SUCCESS 时下发，前端块渲染优先于 contentHtmlSafe）")
    private String bodyBlocksJson;
    @Schema(description = "关联项目编号（跟踪维度键，可为 null=未解析到项目）")
    private String projectKey;
    @Schema(description = "项目名称快照")
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
    @Schema(description = "发布单位")
    private String issuerName;
    @Schema(description = "外站公告发布时间")
    private LocalDateTime publishTime;
    @Schema(description = "生效开始")
    private LocalDateTime effectiveFrom;
    @Schema(description = "生效截止")
    private LocalDateTime effectiveTo;
    @Schema(description = "附件下载列表（仅 ATTACHMENT/含附件时）")
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
        @Schema(description = "下载地址（FileApi 签名）")
        private String downloadUrl;
        @Schema(description = "外站原始下载链接（溯源）")
        private String origDownloadUrl;
        @Schema(description = "病毒扫描状态：PENDING/CLEAN/INFECTED")
        private String virusStatus;
    }

}
