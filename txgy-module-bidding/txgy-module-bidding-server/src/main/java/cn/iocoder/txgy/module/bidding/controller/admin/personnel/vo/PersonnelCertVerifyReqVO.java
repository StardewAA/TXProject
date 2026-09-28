package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工证书级验真入参（五页改造契约 Q13，2026-09-28）。
 *
 * <p>2026-09-28 对账：前端已发布类型（api/bidding/personnel/index.ts 的 verifyPersonnelCert）以
 * **JSON body** 发送 {@code {certId}}，而后端原先只用 {@code @RequestParam} → 前端调用会 400。
 * 故改为"body 优先、query 兜底"双形态（见 PersonnelCertificateController#verifyPersonnelCert）。
 */
@Schema(description = "管理后台 - 员工证书级验真入参")
@Data
public class PersonnelCertVerifyReqVO {

    @Schema(description = "员工证书主键", example = "1001")
    private Long certId;

    @Schema(description = "验真渠道 code（可选；不传由后端按类别选默认渠道）", example = "FOUR_PLATFORM")
    private String verifyChannel;
}
