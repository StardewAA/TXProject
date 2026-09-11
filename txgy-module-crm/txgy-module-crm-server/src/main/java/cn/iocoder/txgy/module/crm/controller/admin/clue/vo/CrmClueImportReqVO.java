package cn.iocoder.txgy.module.crm.controller.admin.clue.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 线索导入 Request VO
 */
@Schema(description = "管理后台 - 线索导入 Request VO")
@Data
@Builder
public class CrmClueImportReqVO {

    @Schema(description = "Excel 文件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Excel 文件不能为空")
    private MultipartFile file;

    @Schema(description = "是否支持更新", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    @NotNull(message = "是否支持更新不能为空")
    private Boolean updateSupport;

    @Schema(description = "负责人（为空时默认当前登录用户）", example = "1")
    private Long ownerUserId;

}
