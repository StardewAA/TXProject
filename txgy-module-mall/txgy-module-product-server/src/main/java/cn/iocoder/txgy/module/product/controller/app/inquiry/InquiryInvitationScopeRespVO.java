package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 采购方查看自己询价单时返回的跨租户定向邀请范围。 */
@Data
@AllArgsConstructor
public class InquiryInvitationScopeRespVO {
    private String targetType;
    private Long targetTenantId;
    private Long targetEnterpriseId;
    private Long targetShopId;
}
