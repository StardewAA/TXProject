package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ErpPurchaseContractTemplateSaveReqVO {
    private Long id;
    @NotBlank(message = "模板名称不能为空")
    private String name;
    @NotBlank(message = "模板版本不能为空")
    private String version;
    @NotBlank(message = "通用合同条款不能为空")
    private String content;
    private Boolean defaultFlag;
    private Integer status;
    private String remark;
}
