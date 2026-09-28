package cn.iocoder.txgy.module.erp.controller.admin.assistant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

@Schema(description = "管理后台 - ERP 智能助手可跳转入口")
@Data
@Accessors(chain = true)
public class ErpAssistantActionVO {

    @Schema(description = "按钮文案", requiredMode = Schema.RequiredMode.REQUIRED, example = "打开销售订单")
    private String label;

    @Schema(description = "前端路由", requiredMode = Schema.RequiredMode.REQUIRED, example = "/erp/sale/order")
    private String path;

    @Schema(description = "补充说明", example = "按单号 XSDD-2026-0003 过滤")
    private String hint;

}
