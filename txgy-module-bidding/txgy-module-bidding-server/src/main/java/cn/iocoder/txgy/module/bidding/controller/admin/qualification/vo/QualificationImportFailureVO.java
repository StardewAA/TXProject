package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 资质导入失败行（Q6 行级回执，2026-09-28）。
 */
@Schema(description = "管理后台 - 资质导入失败行")
@Data
public class QualificationImportFailureVO {

    @Schema(description = "Excel 行号（含表头行，从 2 起）", example = "5")
    private Integer rowNum;

    @Schema(description = "失败原因", example = "资质证书编号已存在")
    private String message;
}
