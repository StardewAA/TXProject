package cn.iocoder.txgy.module.bidding.service.externalfetch.parser;
// [依据] 解析器 SPI 输入：管理端测试（HTML/JSON 文本框或文件）与后续下载器管线共用同一入口；
//       payloadType=FILE 时 fileName 参与类型推断（服务层先归一为 HTML/JSON 再交给解析器）。

import lombok.Data;

/**
 * 解析器输入（与下载器输出对齐：下载器产出原文（HTML/JSON 文本或文件引用）→ 解析器消费）。
 */
@Data
public class ParseInput {

    /** 载荷类型：HTML/JSON/PDF（FILE 由服务层归一后传入） */
    private String payloadType;
    /** HTML/JSON 文本内容 */
    private String content;
    /** 原始文件名（FILE 场景） */
    private String fileName;
    /** 来源页 URL（可选；用于附件相对链接绝对化/上下文提示） */
    private String url;
    /** 文件字节（payloadType=PDF 时：二进制原文直接交给 PDF 文本抽取；HTML/JSON 文件已由服务层字符串化） */
    private byte[] fileBytes;

}
