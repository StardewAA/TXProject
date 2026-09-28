package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工证书批量续期·失败明细（五页改造契约 Q11，2026-09-28）。
 *
 * <p>2026-09-28 对账：前端已发布类型 personnel/index.ts 的 {@code BatchResult.failures} 元素为
 * {@code {id, name, message}}，故单独建此 VO（不复用逐条回执 VO——那个含 {@code success} 字段，语义不同）。
 */
@Schema(description = "管理后台 - 员工证书批量续期失败明细")
@Data
public class PersonnelCertBatchRenewFailVO {

    @Schema(description = "证书主键", example = "1001")
    private Long id;

    @Schema(description = "证书名称（查得到时给出，便于前端直接展示失败行）", example = "安全生产考核合格证（B 类）")
    private String name;

    @Schema(description = "失败原因", example = "新有效期不能早于原有效期")
    private String message;
}
