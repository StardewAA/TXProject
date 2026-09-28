package cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 企业资质导入入参（五页改造缺接口契约 Q6，2026-09-28）。
 */
@Schema(description = "管理后台 - 企业资质导入入参")
@Data
public class QualificationImportReqVO {

    @Schema(description = "Excel 文件（模板见 /import-template）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Excel 文件不能为空")
    private MultipartFile file;
}
