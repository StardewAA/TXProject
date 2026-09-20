package cn.iocoder.txgy.module.erp.controller.admin.recognition.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - ERP 图片识别任务创建 Request VO")
@Data
public class ErpRecognitionTaskCreateReqVO {

    @Schema(description = "业务类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "PURCHASE_ORDER")
    @NotBlank(message = "业务类型不能为空")
    private String bizType;

    @Schema(description = "已上传文件的访问地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请先上传图片或 PDF 文件")
    private String sourceFileUrl;

}
