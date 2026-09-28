package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.contract.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 索引文档请求 VO
 */
@Schema(description = "索引文档请求")
@Data
public class BidSegmentIndexReqVO {

    @Schema(description = "租户编号", example = "1")
    private Long tenantId;

    @Schema(description = "项目编号（企业资料可为空）", example = "1")
    private Long projectId;

    @Schema(description = "文档类型：TENDER/FACT/TEMPLATE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文档类型不能为空")
    private String docType;

    @Schema(description = "来源文档名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "来源文档名不能为空")
    private String sourceDocName;

    @Schema(description = "标段编号（TENDER 建议传；检索按标段隔离，FACT/TEMPLATE 可空）")
    private String bidSectionCode;

    @Schema(description = "文档下载地址（http/https，PDF/docx 等）。url 与 content 二选一，url 优先")
    private String url;

    @Schema(description = "文档全文（纯文本/无网络场景回退，与 url 二选一）")
    private String content;

    @Schema(description = "章节路径")
    private String chapterPath;

    @Schema(description = "页码")
    private Integer pageNo;
}
