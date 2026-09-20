package cn.iocoder.txgy.module.erp.controller.admin.integration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ERP 采购入库单手动申请入库到 WMS 的请求 VO。
 *
 * <p>由 ERP 端采购入库单详情页「申请入库到 WMS」按钮触发。
 * 调用方提交采购入库单 id 与目标 ERP 仓库 id（取其 wmsWarehouseCode 作为 WMS 入库仓库）。</p>
 */
@Schema(description = "管理后台 - ERP 申请入库到 WMS 请求")
@Data
public class ErpWmsInboundApplyReqVO {

    @Schema(description = "采购入库单 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "采购入库单 ID 不能为空")
    private Long purchaseInId;

    @Schema(description = "目标 ERP 仓库 ID（需配置 wmsWarehouseCode 且 status=ENABLE）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标仓库不能为空")
    private Long targetErpWarehouseId;

    @Schema(description = "备注（可选，最长 200 字符）")
    @Size(max = 200, message = "备注长度不能超过 200 个字符")
    private String remark;

}
