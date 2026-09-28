package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 证照导入回执（五页改造缺接口契约 Q1，2026-09-28：行级回执，部分成功可见）。
 *
 * <p>2026-09-28 对账补齐前端已发布字段（api/bidding/certificate/index.ts 的 ImportResp 读
 * {@code totalRows/successCount/failCount/failures[{message,rowNum}]}）：
 * {@code failCount} 与 {@code failureCount} 同义（两个都发），{@code totalRows} 为数据行数（不含表头）。
 */
@Schema(description = "管理后台 - 证照导入回执")
@Data
public class CertificateImportRespVO {

    @Schema(description = "数据总行数（不含表头）", example = "12")
    private Integer totalRows;

    @Schema(description = "成功条数", example = "10")
    private Integer successCount;

    @Schema(description = "失败条数", example = "2")
    private Integer failureCount;

    @Schema(description = "失败条数（= failureCount 的别名；前端契约字段名）", example = "2")
    private Integer failCount;

    @Schema(description = "失败明细（行号+原因）")
    private List<CertificateImportFailureVO> failures;
}
