package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工证书批量续期条目（契约 Q11，2026-09-28）。
 */
@Schema(description = "管理后台 - 员工证书批量续期条目")
@Data
public class PersonnelCertRenewItemReqVO {

    @Schema(description = "证书主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "证书不能为空")
    private Long id;

    // 2026-09-28 对账放宽：前端已发布类型（personnel/index.ts 的 BatchRenewReq.items[]）里 version 是可选。
    // 原 @NotNull 会让"前端不带版本"的整批请求 400（一个字段拖死整批）；服务层 renewOne 已处理 null：
    // 带 version → 做 CAS；不带 → 仅靠 @Version 乐观锁拦并发（updateById=0 仍报 DATA_MODIFIED，不会丢更新）。
    @Schema(description = "乐观锁版本（可选；带了做 CAS，不带则仅靠乐观锁拦并发）", example = "0")
    private Integer version;

    @Schema(description = "续期后有效期止", requiredMode = Schema.RequiredMode.REQUIRED, example = "2028-12-31")
    @NotNull(message = "新有效期不能为空")
    private LocalDate newValidTo;

    @Schema(description = "续期类型 code（登记留痕，如 AUTO 系统延续 / MANUAL 人工延续）", example = "MANUAL")
    private String renewalType;
}
