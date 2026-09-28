package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工证书批量续期逐条结果（契约 Q11，2026-09-28：成功/失败原因，前端展示部分成功）。
 */
@Schema(description = "管理后台 - 员工证书批量续期逐条结果")
@Data
public class PersonnelCertBatchRenewItemRespVO {

    @Schema(description = "证书主键", example = "88001")
    private Long id;

    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    @Schema(description = "失败原因（成功时为空）", example = "版本冲突，请刷新后重试")
    private String message;
}
