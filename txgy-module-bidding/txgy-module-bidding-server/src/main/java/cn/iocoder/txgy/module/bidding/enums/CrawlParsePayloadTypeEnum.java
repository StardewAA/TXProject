package cn.iocoder.txgy.module.bidding.enums;
// [依据] 解析器测试功能入参类型（HTML 文本框/JSON 文本框/文件上传）；FILE 由服务端按扩展名推断为 HTML 或 JSON 后执行。

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 解析器测试载荷类型。
 */
@Getter
@AllArgsConstructor
public enum CrawlParsePayloadTypeEnum {

    /** 直接粘贴的 HTML 文本 */
    HTML("HTML", "HTML 文本"),
    /** 直接粘贴的 JSON 文本 */
    JSON("JSON", "JSON 文本"),
    /** 上传文件（.html/.htm/.json/.pdf，服务端按扩展名推断 HTML/JSON/PDF） */
    FILE("FILE", "文件上传"),
    /** PDF 原文（仅走文件上传 FILE 归一；文本公告解析/站点 PDF 公告用） */
    PDF("PDF", "PDF 文本公告");

    private final String type;
    private final String name;

    /** 忽略大小写解析；未知返回 null */
    public static CrawlParsePayloadTypeEnum of(String type) {
        if (type == null) {
            return null;
        }
        for (CrawlParsePayloadTypeEnum item : values()) {
            if (item.type.equalsIgnoreCase(type)) {
                return item;
            }
        }
        return null;
    }

}
