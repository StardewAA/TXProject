package cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo;

import lombok.Data;

/** 审查报告文档（下载用）：生成的 Word 文件信息。 */
@Data
public class BidReviewReportDocRespVO {

    /** 文件编号（infra_file.id；前端凭它下载/预览） */
    private Long fileId;
    /** 下载地址（平台文件服务签名/直链） */
    private String url;
    /** 文件名（含 .docx） */
    private String fileName;
    /** 是否本次新生成（false=命中已生成的文件，未重复生成） */
    private Boolean generated;
}
