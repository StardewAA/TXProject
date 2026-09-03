package cn.iocoder.txgy.module.bidding.controller.admin.notice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

/**
 * 公告/公示详情（结构化 + 版式文件两种形态）：
 * <ul>
 *   <li>结构化形态：{@link #payload}——权威数据集字段全集（key=规范英文名称，缺失为 null，
 *       字段序与数据集声明一致），配合 {@code noticeType} 可完整映射到对应全量 VO；</li>
 *   <li>版式文件形态：{@link #pdfFileId}——FileApi 文件对象主键，前端据此下载 PDF/版式原件；</li>
 *   <li>正文：{@link #content}（当前生效版本）+ {@link #contentVersion}。</li>
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

}
