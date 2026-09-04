package cn.iocoder.txgy.module.bidding.service.notice.search;

import lombok.Data;

/**
 * ES 公告文档内容：公告全文（body）与招标项目基础信息（title/projectCode/projectName…）。
 * 索引名：bidding_notice；id 取 noticeId；publishTime 存毫秒时间戳便于排序。
 */
@Data
public class NoticeEsDoc {

    private Long noticeId;
    private Long tenantId;
    private Boolean internalFlag;
    private String status;
    private String noticeType;
    private String title;
    private String projectCode;
    private String projectName;
    private Long publishTime;
    /** 公告正文全文（仅检索用，不存储回显） */
    private String body;

}
