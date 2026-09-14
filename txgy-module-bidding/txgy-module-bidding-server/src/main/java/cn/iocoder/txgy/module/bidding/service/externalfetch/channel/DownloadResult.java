package cn.iocoder.txgy.module.bidding.service.externalfetch.channel;
// 规范依据：设计文档 v0.6 §四（原文先落存储：FileApi 或本地存储按部署；三形态内容引用）

import lombok.Data;

/**
 * 下载结果：原文引用 + 内容形态（映射主档 content_type：HTML|PDF|ATTACHMENT|IMAGE|OTHER）。
 */
@Data
public class DownloadResult {

    /** 原文内容引用：FileApi fileId（PDF/附件）或本地存储引用 key */
    private String contentRef;

    /** 原文 sha256（完整性/去重） */
    private String contentSha256;

    /** 内容形态：HTML/PDF/API/ATTACHMENT/IMAGE/OTHER（对应解析侧 contentType） */
    private String contentType;

    /** 抓取到的 HTTP 状态（200 等；非 2xx 视为失败） */
    private Integer httpStatus;

    /** 原始响应体/HTML（HTML 形态直接存主档 content_html 时使用） */
    private String rawBody;

}
