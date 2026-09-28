package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 证件生命周期催办入参（五页改造缺接口契约 Q15，2026-09-18）。
 *
 * <p>规范出处：Q15 契约——入参 id（生命周期记录）；产出通知/待办；同日重复催办不叠加（幂等）。
 * <p>为什么这样写：催办仅需定位记录主键，其余（记录内容快照/催办人）由后端从上下文取，前端不传。
 */
@Schema(description = "管理后台 - 证件生命周期催办入参")
@Data
public class CertLifecycleUrgeReqVO {

    @Schema(description = "生命周期记录 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生命周期记录不能为空")
    private Long id;
}
