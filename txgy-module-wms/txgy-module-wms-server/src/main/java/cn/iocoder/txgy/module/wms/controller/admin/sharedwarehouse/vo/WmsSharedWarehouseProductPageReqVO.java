package cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 共享仓库商品分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WmsSharedWarehouseProductPageReqVO extends PageParam {

    @Schema(description = "WMS 仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "仓库编号不能为空")
    private Long warehouseId;

    @Schema(description = "商品名称", example = "苹果")
    private String itemName;

    @Schema(description = "来源系统：WMS/MALL/ERP", example = "MALL")
    private String sourceSystem;

}
