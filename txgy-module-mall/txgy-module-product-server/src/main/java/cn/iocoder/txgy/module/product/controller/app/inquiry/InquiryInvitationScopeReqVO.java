package cn.iocoder.txgy.module.product.controller.app.inquiry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 跨租户定向询价的明确邀请范围。 */
@Data
public class InquiryInvitationScopeReqVO {

    @NotBlank
    @Pattern(regexp = "TENANT|ENTERPRISE|SHOP")
    private String targetType;

    @NotNull
    private Long targetTenantId;

    private Long targetEnterpriseId;

    private Long targetShopId;
}
