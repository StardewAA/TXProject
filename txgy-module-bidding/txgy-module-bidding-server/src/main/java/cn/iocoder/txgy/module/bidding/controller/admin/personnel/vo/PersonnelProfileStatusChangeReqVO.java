package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工在职状态流转入参（离职/停职/复职）。
 *
 * <p>规范出处：《招投标模块开发规范》§4.5（状态跃迁走接口 + 乐观锁 CAS）；《电子证件全流程管理_数据库设计.md》
 * v3.3 表 15-3（人员在职=挂靠核查口径）。
 * <p>业务目标：人员状态变更留痕且不可随意跳转（离职人员立即退出投标配证候选池）。
 */
@Schema(description = "管理后台 - 员工在职状态流转入参")
@Data
public class PersonnelProfileStatusChangeReqVO {

    @Schema(description = "员工档案主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "员工不能为空")
    private Long id;

    @Schema(description = "目标状态 code：ACTIVE/LEAVE/SUSPENDED/RETIRED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    @Schema(description = "版本（乐观锁）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "版本不能为空")
    private Integer version;

    @Schema(description = "变更原因（离职原因/复职说明，写入备注留痕）")
    private String reason;
}
