package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证件生命周期流程审核入参（受理/审核中 → 通过 / 驳回）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 3/5/6/7 的 status 流转（PENDING→REVIEWING→APPROVED/
 * REJECTED）；《招投标模块开发规范》§4.5（状态跃迁走接口 + 乐观锁 CAS）。
 * <p>业务目标：审批留痕（谁在什么时候批过/驳回过），并防止"未经审核直接生效"绕过管控。
 */
@Schema(description = "管理后台 - 证件生命周期流程审核入参")
@Data
public class CertLifecycleAuditReqVO {

    @Schema(description = "生命周期记录 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生命周期记录不能为空")
    private Long id;

    @Schema(description = "目标状态 code：REVIEWING 审核中 / APPROVED 通过 / REJECTED 驳回",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "APPROVED")
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    @Schema(description = "版本（乐观锁）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "版本不能为空")
    private Integer version;

    @Schema(description = "审核意见（驳回时必填，便于申请人整改）")
    private String remark;
}
