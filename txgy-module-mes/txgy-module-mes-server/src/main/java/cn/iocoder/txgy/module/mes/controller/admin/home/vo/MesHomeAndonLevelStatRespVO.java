package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - MES 首页安灯级别分布 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeAndonLevelStatRespVO {

    @Schema(description = "级别值", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer level;

    @Schema(description = "级别名", requiredMode = Schema.RequiredMode.REQUIRED, example = "一级")
    private String levelName;

    @Schema(description = "呼叫数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long count;

}
