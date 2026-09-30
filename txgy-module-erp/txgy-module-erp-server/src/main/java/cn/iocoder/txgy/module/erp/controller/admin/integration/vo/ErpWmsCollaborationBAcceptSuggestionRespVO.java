package cn.iocoder.txgy.module.erp.controller.admin.integration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 接受缺料协同单前的商务信息自动匹配建议 Response VO
 *
 * 依据协同单物料的历史采购记录，自动匹配供应商、默认结算账户与最近采购单价/税率，
 * 业务人员打开接受弹窗即可一键提交，无需手工逐项填写。
 */
@Schema(description = "管理后台 - 供应链协同单接受建议 Response VO")
@Data
public class ErpWmsCollaborationBAcceptSuggestionRespVO {

    @Schema(description = "匹配到的供应商编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "9403")
    private Long supplierId;

    @Schema(description = "匹配到的供应商名称", example = "山东金彩板业有限公司")
    private String supplierName;

    @Schema(description = "默认结算账户编号", example = "9701")
    private Long accountId;

    @Schema(description = "默认结算账户名称", example = "默认账户")
    private String accountName;

    @Schema(description = "建议采购时间（当前时间）", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime orderTime;

    @Schema(description = "是否匹配到供应商", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean matched;

    @Schema(description = "多个物料的历史供应商是否不一致（true 时请人工确认）", example = "false")
    private Boolean multiSupplier;

    @Schema(description = "匹配结论提示文案", example = "已根据最近采购记录自动匹配供应商与采购价")
    private String summary;

    @Schema(description = "明细行建议（单价/税率按最近采购记录回填）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Item> items;

    @Schema(description = "管理后台 - 协同单接受建议明细 Response VO")
    @Data
    public static class Item {

        @Schema(description = "行号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer lineNo;

        @Schema(description = "产品编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "9317")
        private Long productId;

        @Schema(description = "该物料最近一次采购的供应商编号", example = "9403")
        private Long supplierId;

        @Schema(description = "该物料最近一次采购的供应商名称", example = "山东金彩板业有限公司")
        private String supplierName;

        @Schema(description = "建议采购单价（取最近采购价，无记录时取参考价）", example = "52.00")
        private BigDecimal productPrice;

        @Schema(description = "建议税率（百分比，取最近采购税率，无记录时默认 13）", example = "13.00")
        private BigDecimal taxPercent;

        @Schema(description = "价格来源采购单号（便于追溯）", example = "CGDD2026092000001")
        private String sourceOrderNo;

    }

}
