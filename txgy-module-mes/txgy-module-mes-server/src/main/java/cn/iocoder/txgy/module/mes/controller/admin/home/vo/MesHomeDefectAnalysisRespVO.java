package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - MES 首页不良缺陷分析 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeDefectAnalysisRespVO {

    @Schema(description = "缺陷记录条数", requiredMode = Schema.RequiredMode.REQUIRED, example = "23")
    private Long recordCount;

    @Schema(description = "缺陷总数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "156")
    private Long totalQuantity;

    @Schema(description = "致命缺陷数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Long criticalQuantity;

    @Schema(description = "严重缺陷数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "58")
    private Long majorQuantity;

    @Schema(description = "轻微缺陷数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "86")
    private Long minorQuantity;

    @Schema(description = "缺陷 TOP N 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<DefectItem> defectTopList;

    @Schema(description = "缺陷 TOP 明细")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefectItem {

        @Schema(description = "缺陷描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "焊缝气孔")
        private String name;

        @Schema(description = "缺陷数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        private Long quantity;

    }

}
