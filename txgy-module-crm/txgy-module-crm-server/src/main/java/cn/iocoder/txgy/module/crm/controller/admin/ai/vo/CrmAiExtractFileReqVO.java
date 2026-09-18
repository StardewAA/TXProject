package cn.iocoder.txgy.module.crm.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * CRM AI 文档识别 Request VO
 *
 * 前端把用户上传的 Word / PDF 文件转为 base64 后提交，
 * 由后端抽取文本并转交大模型做字段识别（与图片识别共用同一套规则与输出结构）。
 */
@Schema(description = "管理后台 - CRM AI 文档识别 Request VO")
@Data
public class CrmAiExtractFileReqVO {

    @Schema(description = "文件名（含扩展名，用于判断解析方式）", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "采购合同.docx")
    @NotBlank(message = "文件名不能为空")
    @Size(max = 200, message = "文件名过长")
    private String fileName;

    @Schema(description = "文件内容，base64（可带 data URL 前缀）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "文件内容不能为空")
    @Size(max = 14 * 1024 * 1024, message = "文件过大，请压缩后重试（不超过 10MB）")
    private String content;

    @Schema(description = "补充说明，例如「识别采购合同」", example = "识别采购合同")
    @Size(max = 200, message = "补充说明不能超过 200 字")
    private String hint;

}
