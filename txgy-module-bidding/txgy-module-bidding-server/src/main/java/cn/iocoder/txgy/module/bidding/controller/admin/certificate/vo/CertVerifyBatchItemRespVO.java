package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量验真行级回执（五页改造契约 Q2，2026-09-28）。
 *
 * <p>单条失败（不存在/验真中/通道未就绪）记入该行结果，不影响其余行。
 *
 * <p>2026-09-28 对齐前端已发布类型（api/bidding/certificate/index.ts 的 VerifyBatchItem）：
 * 前端读 {@code certId/certName/accepted/verifyResult/verifyChannel/message}；
 * {@code success} 与 {@code accepted} 同义（本次受理是否成功，**不代表**验真结论为"一致"）。
 */
@Schema(description = "管理后台 - 批量验真行级回执")
@Data
public class CertVerifyBatchItemRespVO {

    @Schema(description = "证书ID")
    private Long certId;

    @Schema(description = "证照名称（逐条结果表展示；查不到时为空）", example = "营业执照")
    private String certName;

    @Schema(description = "是否验真成功（= accepted；保留旧字段名兼容）")
    private Boolean success;

    @Schema(description = "是否本次受理成功（前端契约字段名；与 success 同义，不代表结论一致）")
    private Boolean accepted;

    @Schema(description = "验真结论 code：MATCH/MISMATCH/UNAVAILABLE（字典 verify_result；失败时为空）")
    private String verifyResult;

    @Schema(description = "验真渠道 code（字典 verify_channel）", example = "FOUR_PLATFORM")
    private String verifyChannel;

    @Schema(description = "失败原因（成功时为空）")
    private String message;
}
