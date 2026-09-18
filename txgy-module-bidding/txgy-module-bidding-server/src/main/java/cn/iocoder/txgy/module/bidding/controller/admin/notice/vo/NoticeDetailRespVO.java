package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;
// 规范依据：《招投标模块开发规范》§2.1、§4.8；01/05（PDF原文件+结构化两形态）

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

/**
 * 公告/公示详情（结构化 + 版式文件两种形态）：
 * <p>两种前端展示形态说明（同一公告，展示方式差异，底层数据同一份）：
 * <ul>
 *   <li>PDF 形态：前端内嵌查看器渲染 {@link #pdfFileId} 对应的 <b>PDF 原文件</b>
 *       （原文件必须保存并在发布前落库，发布校验强制 pdfFileId 非空）；</li>
 *   <li>结构化 HTML 形态：前端按 {@link #payload}（权威字段全集）+ {@link #content} 渲染成 HTML
 *       （后端不生成 HTML，只给结构化数据）。</li>
 * </ul>
 */
@Schema(description = "管理后台 - 公告/公示详情")
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeDetailRespVO extends NoticeRespVO {

    @Schema(description = "正文全文（当前生效版本）")
    private String content;
    @Schema(description = "正文版本号")
    private Integer contentVersion;
    @Schema(description = "权威数据集字段全集（结构化形态）")
    private LinkedHashMap<String, Object> payload;
    @Schema(description = "版式文件（PDF）file_id（版式形态；无则 null）")
    private Long pdfFileId;
    @Schema(description = "版式文件（PDF）原文件哈希（SHA-256）")
    private String pdfSha256;
    @Schema(description = "版式文件（PDF）原文件大小（字节）")
    private Long pdfSize;

}
