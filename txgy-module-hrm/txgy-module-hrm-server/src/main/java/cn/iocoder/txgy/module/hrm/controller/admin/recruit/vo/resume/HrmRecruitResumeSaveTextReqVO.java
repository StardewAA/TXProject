package cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台 - HRM 简历文本保存 Request VO（扫描件 OCR / 人工粘贴）
 *
 * @author OPENLAB BS
 */
@Schema(description = "管理后台 - HRM 简历文本保存 Request VO")
@Data
public class HrmRecruitResumeSaveTextReqVO {

    @Schema(description = "简历编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "简历编号不能为空")
    private Long id;

    @Schema(description = "简历文本（OCR 识别结果或人工粘贴内容）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "简历文本不能为空")
    private String content;

    @Schema(description = "内容来源：ocr=扫描件识别、manual=人工粘贴", example = "ocr")
    private String source;

}
