package cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 员工证书导入回执（行级结果）。
 *
 * <p>口径与证照/资质导入一致：单个失败不影响其余，逐行回执便于运营定位。
 * {@code failCount} 是 {@code failureCount} 的别名（前端契约字段名）。
 */
@Schema(description = "管理后台 - 员工证书导入回执")
@Data
public class PersonnelCertImportRespVO {

    @Schema(description = "数据总行数（不含表头）", example = "12")
    private Integer totalRows;

    @Schema(description = "成功条数", example = "10")
    private Integer successCount;

    @Schema(description = "失败条数", example = "2")
    private Integer failureCount;

    @Schema(description = "失败条数（= failureCount 的别名；前端契约字段名）", example = "2")
    private Integer failCount;

    @Schema(description = "失败明细（行号+原因）")
    private List<PersonnelCertImportFailureVO> failures;
}
