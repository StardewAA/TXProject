package cn.iocoder.txgy.module.bidding.service.notice.search;

import lombok.Data;

/**
 * ES 公告文档内容：公告全文（body）与招标项目基础信息（title/projectCode/projectName…）。
 * 索引名：bidding-notice（短横线风格，与外部公告 bidding-notice-external 一致）；id 取 noticeId；publishTime 存毫秒时间戳便于排序。
 */
@Data
public class NoticeEsDoc {

    private Long noticeId;
    private Long tenantId;
    private Boolean internalFlag;
    private String status;
    private String noticeType;
    /** 公告编号（B+17 位；编号查询是主键类查询，须进索引字段——2026-10-02 契约《公告检索覆盖本站公告》） */
    private String noticeCode;
    private String title;
    private String projectCode;
    private String projectName;
    /**
     * 发布时间。存 ISO 字符串（如 2026-09-19T06:25:02）而非毫秒：
     * 索引的 publishTime 被动态映射为 date 时，毫秒数值会触发解析失败（PUT 400 被静默豁免 → 文档不落库），
     * ISO 字符串符合 ES date 默认格式（strict_date_optional_time，排序照常）。
     */
    private String publishTime;
    /** 公告正文全文（仅检索用，不存储回显） */
    private String body;

}
