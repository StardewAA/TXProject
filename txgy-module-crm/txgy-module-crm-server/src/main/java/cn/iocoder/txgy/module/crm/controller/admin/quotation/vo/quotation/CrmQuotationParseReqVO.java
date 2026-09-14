package cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - CRM 报价单文本智能解析 Request VO")
@Data
public class CrmQuotationParseReqVO {

    @Schema(description = "待解析文本", requiredMode = Schema.RequiredMode.REQUIRED, example = "王经理 13800138000 中石化四公司")
    @NotBlank(message = "待解析文本不能为空")
    private String content;

}
