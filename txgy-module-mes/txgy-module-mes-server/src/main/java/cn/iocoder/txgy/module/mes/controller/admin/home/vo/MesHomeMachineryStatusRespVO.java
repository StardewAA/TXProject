package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - MES 首页设备状态分布 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeMachineryStatusRespVO {

    @Schema(description = "状态值", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer status;

    @Schema(description = "状态名", requiredMode = Schema.RequiredMode.REQUIRED, example = "生产中")
    private String statusName;

    @Schema(description = "设备台数", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long count;

}
