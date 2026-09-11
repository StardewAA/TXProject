package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - MES 首页安灯呼叫原因统计 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeAndonReasonStatRespVO {

    @Schema(description = "呼叫原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "设备故障")
    private String reason;

    @Schema(description = "呼叫数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "8")
    private Long count;

}
