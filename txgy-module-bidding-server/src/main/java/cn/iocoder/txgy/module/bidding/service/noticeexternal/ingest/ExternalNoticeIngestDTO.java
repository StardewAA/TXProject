package cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest;
// 规范依据：设计文档 v0.6 §五/§七/§15.1（采集产出落主档 NEW：主列稳定+meta_json 兜底；附件行、PDF 红线8）

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集产出落库请求（下载/抽取就绪后的外部公告主档录入）。
 * <p>约定：origUrlHash 为空时由服务按 origUrl 计算（去重指纹）；contentType=HTML 传 contentHtml，
 * PDF 传 pdfFileId+pdfSha256+pdfSize（红线 8：file_id+sha256+size）；附件列表写子表并在主档留 ids JSON。
 */
@Data
public class ExternalNoticeIngestDTO {

    private String sourceCode;
    private String sourceName;
    private String title;
    private String origUrl;
    /** 为空自动 SHA-256(origUrl) */
    private String origUrlHash;
    private String contentHash;
    /** HTML/PDF/ATTACHMENT/IMAGE/OTHER（NoticeExternalContentTypeEnum） */
    private String contentType;
    private String contentHtml;
    private Long pdfFileId;
    private String pdfSha256;
    private Long pdfSize;
    private String noticeTypeRaw;
    private String legalFlag;
    /** BELONGS/NOT_BELONGS/UNCERTAIN */
    private String industryFlag;
    private String projectCode;
    private String tenderProjectCode;
    private String bidSectionCodes;
    private String projectName;
    private String issuerName;
    private LocalDateTime publishTime;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    /** 解析侧策略码（URL 审批绑定，见设计 §六/§14.1） */
    private String parserCode;
    /** 原始页面结构化字段全集（JSON，兜底防丢） */
    private String metaJson;
    /** 整页快照（可追溯/复审） */
    private String extraRaw;

    /** 附件下载记录（子表） */
    private List<Attachment> attachments = new ArrayList<>();

    @Data
    public static class Attachment {

        private Long fileId;
        private String sha256;
        private Long size;
        private String name;
        private String ext;
        private String origDownloadUrl;
    }

}
