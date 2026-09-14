package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 解析结果=公告字段规范化模型（与 ingest DTO/主档列对齐：title/origUrl/跟踪键/时间/附件），
//       叠加测试观测信息（status/warnings/meta/耗时）供解析器测试接口展示。

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析结果（SUCCESS=主列齐备；PARTIAL=有产出但缺关键列；FAILED=无产出，error 说明）。
 */
@Data
public class NoticeParseResult {

    /** 解析状态 */
    public enum Status {
        SUCCESS, PARTIAL, FAILED
    }

    /** 解析状态 */
    private Status status;
    /** 失败/不可解析原因（FAILED 时） */
    private String error;

    /** 解析器 code */
    private String parserCode;
    /** 解析器名称 */
    private String parserName;

    // ---- 公告规范化字段（与 ExternalNoticeIngestDTO 对齐）----
    private String title;
    private String origUrl;
    /** 内容形态建议：HTML/PDF/...（附件候选为 pdf 时提示 PDF） */
    private String contentType;
    private String contentHtml;
    /** 纯文本视图（HTML 去标签；JSON 时正文文本） */
    private String contentText;
    private String noticeTypeRaw;
    private String legalFlag;
    private String projectCode;
    /** 防腐保温行业 AI 判定：BELONGS/NOT_BELONGS/UNCERTAIN（AI 解析器产出） */
    private String industryFlag;
    /** 行业判定置信度 0~1（AI 解析器产出） */
    private String industryConfidence;
    /** AI 结构化 meta（原样 JSON 留痕：额外字段/判定理由/命中词） */
    private String aiMetaJson;
    private String tenderProjectCode;
    private String bidSectionCodes;
    private String projectName;
    private String issuerName;
    private LocalDateTime publishTime;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;

    /** 附件候选（下载器阶段将按此下载并回传 ingest） */
    private List<AttachmentCandidate> attachments = new ArrayList<>();
    /** 列表页条目（输入为栏目/列表页时产出：标题/详情链接/日期；下载器阶段按条目建任务抓详情） */
    private List<ListEntry> entries = new ArrayList<>();
    /** 页面形态：DETAIL=公告详情 / LIST=公告列表 / UNKNOWN */
    private String pageType;
    /** 抽取过程观测键值（字段名/中间值/计数等，前端平铺展示便于校准） */
    private Map<String, String> meta = new LinkedHashMap<>();
    /** 告警（缺字段/启发式提取待核对等） */
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class ListEntry {

        private String title;
        private String url;
        /** 列表展示日期（yyy-MM-dd；优先详情 URL 内日期，其次行内日期） */
        private String date;
        /** 栏目/类型提示（站点给定） */
        private String category;
        /** 全文 HTML（全文型列表接口如秦源 res.rows.text 携带；下载器直采入库，测试 VO 不回显） */
        private String contentHtml;
        /**
         * 详情/落库阶段解析器（2026-09-10 下载器改 AI）。
         *
         * <p>依据：用户口径「把现在的三个下载器都改成 AI 的」——列表解析器只负责“栏目→条目列表”，
         * 条目正文的结构化与防腐保温行业判定应交 AI 解析器（HTML_AI/PDF_AI）。
         * 原因：task.parser_code 仍须保留列表解析器用于执行路由（GX 详情→PDF、QYZB JSON 直采等），
         * 故详情解析器随 payload 快照下发，二者解耦、互不污染既有路由与去重语义。
         */
        private String detailParser;
    }

    @Data
    public static class AttachmentCandidate {

        private String name;
        private String url;
        /** 绝对化失败/仅相对链接时为 true */
        private boolean urlIncomplete;

        public static AttachmentCandidate of(String name, String url, boolean urlIncomplete) {
            AttachmentCandidate candidate = new AttachmentCandidate();
            candidate.setName(name);
            candidate.setUrl(url);
            candidate.setUrlIncomplete(urlIncomplete);
            return candidate;
        }
    }

    public static NoticeParseResult failed(String parserCode, String parserName, String error) {
        NoticeParseResult result = new NoticeParseResult();
        result.setParserCode(parserCode);
        result.setParserName(parserName);
        result.setStatus(Status.FAILED);
        result.setError(error);
        return result;
    }

    /** 按主列齐备度定级（title+正文文本+时间=SUCCESS；有正文文本缺 title/时间=PARTIAL）。 */
    public void finalizeStatus() {
        boolean hasTitle = title != null && !title.isBlank();
        boolean hasBody = (contentText != null && !contentText.isBlank())
                || (contentHtml != null && !contentHtml.isBlank());
        if (hasTitle && hasBody) {
            status = Status.SUCCESS;
        } else if (hasBody || hasTitle) {
            status = Status.PARTIAL;
            warnings.add("产出不完整：主列（标题/正文）缺失，已按 PARTIAL 返回，请人工核对解析规则");
        } else {
            status = Status.FAILED;
            error = "未从输入中解析出任何公告字段（标题/正文均为空）";
        }
    }

}
