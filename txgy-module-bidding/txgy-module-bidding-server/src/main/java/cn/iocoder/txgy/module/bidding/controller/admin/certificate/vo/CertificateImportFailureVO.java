package cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 证照导入失败行（Q1 行级回执，2026-09-28：行号+原因，前端逐行定位）。
 */
@Schema(description = "管理后台 - 证照导入失败行")
@Data
public class CertificateImportFailureVO {

    @Schema(description = "Excel 行号（含表头行，从 2 起）", example = "5")
    private Integer rowNum;

    @Schema(description = "失败原因", example = "证书编号已存在")
    private String message;
}
