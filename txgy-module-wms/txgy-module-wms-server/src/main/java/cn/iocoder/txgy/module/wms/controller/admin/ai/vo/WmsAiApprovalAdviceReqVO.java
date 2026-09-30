package cn.iocoder.txgy.module.wms.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 辅助审批 —— 审批建议请求
 *
 * <p>审批人在 BPM 审批详情页点击「AI 审批建议」时调用，只读取单据与库存做分析，
 * 不改变单据状态、不代替人工审批。
 */
@Schema(description = "管理后台 - WMS AI 辅助审批建议 Request VO")
@Data
public class WmsAiApprovalAdviceReqVO {

    @Schema(description = "单据编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "3101019")
    @NotNull(message = "单据编号不能为空")
    private Long orderId;

    @Schema(description = "单据类型：RECEIPT 入库 / SHIPMENT 出库", requiredMode = Schema.RequiredMode.REQUIRED, example = "RECEIPT")
    @NotBlank(message = "单据类型不能为空")
    private String orderType;

}
