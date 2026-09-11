package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 员工证书状态流转入参（吊销 / 注销 / 恢复）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二（状态口径对齐建办市〔2024〕51号 ZZZT）；
 * 《招投标模块开发规范》§4.5（状态跃迁走接口 + 乐观锁 CAS）。
 * <p>为什么单独一个入参对象：证书状态影响"能否用于投标配证/资质申报"，必须有独立的权限点与留痕，
 * 不允许通过"更新证书"接口顺手改状态（否则状态变更会混在字段更新里，无法审计）。
 * <p>业务目标：证书失效与恢复全程留痕（谁、何时、为什么），到期状态由预警扫描自动维护、人工不可设置。
 */
@Schema(description = "管理后台 - 员工证书状态流转入参")
@Data
public class PersonnelCertificateStatusChangeReqVO {

    @Schema(description = "证书主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "证书不能为空")
    private Long id;

    @Schema(description = "目标状态 code：REVOKED 吊销/CANCELLED 注销/SUSPENDED 暂扣/VALID 恢复",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "REVOKED")
    @NotBlank(message = "目标状态不能为空")
    private String targetStatus;

    @Schema(description = "版本（乐观锁）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "版本不能为空")
    private Integer version;

    @Schema(description = "变更原因（吊销/注销事由，写入备注留痕）")
    private String reason;
}
