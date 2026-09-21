package cn.iocoder.txgy.module.crm.controller.admin.product.vo.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CRM 产品 WMS 同步结果 Response VO")
@Data
public class CrmProductWmsSyncRespVO {

    @Schema(description = "是否跳过本次同步（例如租户未开启同步）", example = "false")
    private Boolean skipped = Boolean.FALSE;

    @Schema(description = "跳过原因或同步概要")
    private String message;

    @Schema(description = "WMS 返回的规格行数", example = "12")
    private Integer total = 0;

    @Schema(description = "新增产品数", example = "3")
    private Integer created = 0;

    @Schema(description = "更新产品数", example = "4")
    private Integer updated = 0;

    @Schema(description = "无变化产品数", example = "5")
    private Integer unchanged = 0;

    @Schema(description = "因产品编码已被占用而跳过的数量", example = "0")
    private Integer conflict = 0;

    @Schema(description = "同步失败数量", example = "0")
    private Integer failed = 0;

    @Schema(description = "明细信息，最多 20 条")
    private List<String> messages;

    @Schema(description = "同步完成时间")
    private LocalDateTime syncTime;

}
