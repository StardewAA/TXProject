package cn.iocoder.txgy.module.erp.controller.admin.stock.vo.warehouse;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - ERP 仓库新增/修改 Request VO")
@Data
public class ErpWarehouseSaveReqVO {

    @Schema(description = "仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "11614")
    private Long id;

    @Schema(description = "WMS 权威仓库编码；由 WMS 分配后在 ERP 建立映射", example = "WH-XBHT-001")
    private String wmsWarehouseCode;

    @Schema(description = "仓库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "李四")
    @NotEmpty(message = "仓库名称不能为空")
    private String name;

    @Schema(description = "仓库地址", example = "上海陆家嘴")
    private String address;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "排序不能为空")
    private Long sort;

    @Schema(description = "备注", example = "随便")
    private String remark;

    @Schema(description = "负责人", example = "芋头")
    private String principal;

    @Schema(description = "负责人用户编号；调拨确认和库存变动通知将发送给该账号", example = "1")
    private Long principalUserId;

    @Schema(description = "仓储费，单位：元", example = "13973")
    private BigDecimal warehousePrice;

    @Schema(description = "搬运费，单位：元", example = "9903")
    private BigDecimal truckagePrice;

    @Schema(description = "开启状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @NotNull(message = "开启状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

}
