package cn.iocoder.txgy.module.bidding.controller.admin.externalfetch.vo;
// [依据] 解析器测试结果出参：公告规范化字段（与 ingest/主档列对齐）+ 观测信息（meta/warnings/耗时），
//       供管理界面校准解析规则（先解析后下载的开发顺序）。

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 爬虫解析器测试 Response")
@Data
public class CrawlParseTestRespVO {

    // ---- 请求回显 ----
    @Schema(description = "解析器 code")
    private String parserCode;
    @Schema(description = "解析器名称")
    private String parserName;
    @Schema(description = "载荷类型：HTML/JSON（FILE 已归一）")
    private String payloadType;
    @Schema(description = "原始文件名（FILE 场景回显）")
    private String fileName;
    @Schema(description = "来源 URL（可选，回显）")
    private String url;
    @Schema(description = "输入内容长度（字符）")
    private Integer contentLength;
    @Schema(description = "解析耗时（毫秒）")
    private Long durationMs;

    // ---- 解析状态 ----
    @Schema(description = "SUCCESS 成功 / PARTIAL 部分产出（缺主列）/ FAILED 失败（error 说明）")
    private String status;
    @Schema(description = "失败原因（FAILED 时）")
    private String error;

    // ---- 公告规范化字段 ----
    @Schema(description = "公告标题")
    private String title;
    @Schema(description = "原网址（入参 url 或 JSON 中解析）")
    private String origUrl;
    @Schema(description = "内容形态建议：HTML/PDF")
    private String contentType;
    @Schema(description = "公告类型原文")
    private String noticeTypeRaw;
    @Schema(description = "依法必招 Y/N")
    private String legalFlag;
    @Schema(description = "关联项目编号（跟踪键）")
    private String projectKey;
    @Schema(description = "项目名称")
    private String projectName;
    @Schema(description = "关联招标项目编号（跟踪键）")
    private String tenderProjectKey;
    @Schema(description = "标段编号（分号分隔）")
    private String bidSectionCodes;
    @Schema(description = "发布单位")
    private String issuerName;
    @Schema(description = "公告发布时间")
    private LocalDateTime publishTime;
    @Schema(description = "生效开始/截止")
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @Schema(description = "正文纯文本预览（前 3000 字符，用于人工核对抽取质量）")
    private String contentTextPreview;
    @Schema(description = "HTML 原文长度（字符，不随预览下发原文）")
    private Integer contentHtmlLength;

    // ---- 附件候选 ----
    @Schema(description = "附件候选（下载器阶段将下载后回传 ingest）")
    private List<Attachment> attachments;
    @Schema(description = "页面形态：DETAIL=公告详情 / LIST=公告列表（entries 有值）/ UNKNOWN")
    private String pageType;
    @Schema(description = "列表页条目（列表输入时返回；每行=一条待抓取详情）")
    private List<Entry> entries;

    // ---- 观测信息 ----
    @Schema(description = "抽取过程键值（matched.*/unmatched.*/计数等）")
    private Map<String, String> meta;
    @Schema(description = "告警（缺字段/启发式待核对等）")
    private List<String> warnings;

    @Schema(description = "列表条目行")
    @Data
    public static class Entry {

        @Schema(description = "条目标题")
        private String title;
        @Schema(description = "详情链接")
        private String url;
        @Schema(description = "日期（yyyy-MM-dd）")
        private String date;
        @Schema(description = "栏目/类型提示")
        private String category;
    }

    @Schema(description = "附件候选行")
    @Data
    public static class Attachment {

        @Schema(description = "文件名")
        private String name;
        @Schema(description = "链接（相对链接未绝对化时为原样，urlIncomplete=true）")
        private String url;
        @Schema(description = "链接是否未绝对化（缺来源 URL 时 true）")
        private Boolean urlIncomplete;
    }

}
