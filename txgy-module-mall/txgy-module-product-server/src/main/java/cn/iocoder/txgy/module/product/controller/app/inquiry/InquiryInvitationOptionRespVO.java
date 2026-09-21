package cn.iocoder.txgy.module.product.controller.app.inquiry;

import lombok.Data;

/**
 * 定向询价可邀请店铺的公开目录项。
 *
 * <p>这里故意只暴露公开名称和业务编号，不承载联系人、资质附件、库存等经营数据。</p>
 */
@Data
public class InquiryInvitationOptionRespVO {

    private Long tenantId;
    private String tenantName;
    private Long enterpriseId;
    private String enterpriseName;
    private Long shopId;
    private String shopName;
}
