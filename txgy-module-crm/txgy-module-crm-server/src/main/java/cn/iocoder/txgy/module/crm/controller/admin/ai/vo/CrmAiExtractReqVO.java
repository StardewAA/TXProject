package cn.iocoder.txgy.module.crm.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * CRM AI 图片识别 Request VO
 *
 * 前端把用户上传的合同 / 报价 / 客户资料图片转为 base64 Data URL 后提交，
 * 由后端转发多模态大模型做 OCR 与字段提取。
 */
@Schema(description = "管理后台 - CRM AI 图片识别 Request VO")
@Data
public class CrmAiExtractReqVO {

    @Schema(description = "图片内容，base64 Data URL（data:image/png;base64,...）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "图片内容不能为空")
    @Size(max = 8 * 1024 * 1024, message = "图片过大，请压缩后重试（不超过 8MB）")
    private String image;

    @Schema(description = "补充说明，例如「识别报价单」", example = "识别报价单")
    @Size(max = 200, message = "补充说明不能超过 200 字")
    private String hint;

}
