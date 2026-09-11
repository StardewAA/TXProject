package cn.iocoder.txgy.module.mes.controller.admin.home.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "管理后台 - MES 首页人员报工产出分析 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MesHomeFeedbackUserRespVO {

    @Schema(description = "参与报工人数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long userCount;

    @Schema(description = "报工总量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1596")
    private BigDecimal totalQuantity;

    @Schema(description = "合格品总量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1480")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "合格率（%）", requiredMode = Schema.RequiredMode.REQUIRED, example = "92.7")
    private BigDecimal passRate;

    @Schema(description = "报工人产出 TOP N", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<UserItem> topList;

    @Schema(description = "报工人产出明细")
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserItem {

        @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
        private Long userId;

        @Schema(description = "用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
        private String userName;

        @Schema(description = "报工单数", requiredMode = Schema.RequiredMode.REQUIRED, example = "4")
        private Long recordCount;

        @Schema(description = "合格品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "320")
        private BigDecimal qualifiedQuantity;

    }

}
