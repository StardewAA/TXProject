package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证件生命周期流程生效入参（回写主档）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 5（办结回写主档 valid_from/valid_to/next_review_date）、
 * 表 6（生效同步台账字段）、表 7（吊销/注销落状态）；《招投标模块开发规范》§4.5（写操作与流转同事务 + 乐观锁）。
 * <p>为什么把"生效"独立成一个接口：审核通过 ≠ 已生效（如续期要等拿到新证、注销要等决定书下达），
 * 分开可以让"通过"与"落地"两个时点都可追溯。
 * <p>业务目标：生效一次即完成"记录生效 + 主档回写"，台账与流程永不脱节。
 */
@Schema(description = "管理后台 - 证件生命周期流程生效入参")
@Data
public class CertLifecycleEffectReqVO {

    @Schema(description = "生命周期记录 id（变更流程为变更组内任一行 id，服务层按组同步生效）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生命周期记录不能为空")
    private Long id;

    @Schema(description = "版本（乐观锁）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "版本不能为空")
    private Integer version;

    @Schema(description = "生效备注")
    private String remark;
}
