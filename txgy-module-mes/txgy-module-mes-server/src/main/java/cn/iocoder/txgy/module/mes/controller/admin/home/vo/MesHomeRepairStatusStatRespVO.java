package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - MES 首页维修工单状态分布 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeRepairStatusStatRespVO {

    @Schema(description = "状态值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "状态名", requiredMode = Schema.RequiredMode.REQUIRED, example = "维修中")
    private String statusName;

    @Schema(description = "工单数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "6")
    private Long count;

}
