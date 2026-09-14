package cn.iocoder.txgy.module.bidding.enums;
// 规范依据：设计文档 v0.6 §7/§14.2（三形态展示 + PDF 抽取）

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 外部公告内容形态（bidding_notice_external.content_type），决定展示方式：
 * HTML=原文原样 + origUrl；PDF=pdfFileId JS 预览 + 下载 + origUrl（本地 OpenDataLoader 抽取文本/表格）；
 * ATTACHMENT=附件下载列表；IMAGE/OTHER 兜底展示 origUrl。
 */
@Getter
@AllArgsConstructor
public enum NoticeExternalContentTypeEnum {

    HTML("HTML", "HTML 原文"),
    PDF("PDF", "PDF 原文件（JS 预览）"),
    ATTACHMENT("ATTACHMENT", "附件下载"),
    IMAGE("IMAGE", "图片"),
    OTHER("OTHER", "其它");

    private final String type;
    private final String name;

    public static NoticeExternalContentTypeEnum of(String type) {
        return Arrays.stream(values())
                .filter(item -> item.getType().equals(type))
                .findFirst()
                .orElse(OTHER);
    }

}
