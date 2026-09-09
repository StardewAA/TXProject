package cn.iocoder.txgy.module.wms.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WmsPartnerUpsertReqDTO extends WmsIntegrationBaseReqDTO {

    @NotBlank(message = "partnerCode 不能为空")
    private String partnerCode;

    @NotBlank(message = "企业名称不能为空")
    private String name;

    @NotBlank(message = "partnerType 不能为空")
    private String partnerType;

    private String contact;
    private String mobile;
    private String telephone;
    private String email;
    private String address;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private String remark;

    @NotBlank(message = "status 不能为空")
    private String status;

    @NotNull(message = "dataVersion 不能为空")
    private Long dataVersion;

}
