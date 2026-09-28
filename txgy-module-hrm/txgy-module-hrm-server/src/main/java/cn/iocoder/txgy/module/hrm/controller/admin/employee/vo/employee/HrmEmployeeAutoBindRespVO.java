package cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - HRM 员工档案与后台账号自动绑定结果 Response VO")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HrmEmployeeAutoBindRespVO {

    @Schema(description = "扫描的「未绑定账号」员工档案数", example = "12")
    private Integer scannedCount;

    @Schema(description = "本次成功绑定的档案数", example = "8")
    private Integer boundCount;

    @Schema(description = "仍无法匹配账号的档案数（需人工用「从后台用户建档」或补手机号）", example = "4")
    private Integer unmatchedCount;

    @Schema(description = "绑定明细")
    private List<Item> items;

    @Schema(description = "HRM 员工档案与后台账号绑定的单条结果")
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {

        @Schema(description = "员工档案编号", example = "1")
        private Long employeeId;

        @Schema(description = "姓名", example = "徐超")
        private String name;

        @Schema(description = "工号", example = "QC000008")
        private String jobNumber;

        @Schema(description = "是否绑定成功", example = "true")
        private Boolean bound;

        @Schema(description = "说明（成功时是账号名，失败时是原因）", example = "qinchuan_xuchao_2")
        private String message;

    }

}
