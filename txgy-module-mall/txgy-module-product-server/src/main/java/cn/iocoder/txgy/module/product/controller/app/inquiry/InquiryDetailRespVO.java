package cn.iocoder.txgy.module.product.controller.app.inquiry;

import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryItemDO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InquiryDetailRespVO {
    private InquiryRespVO order;
    private List<InquiryItemDO> items;
    private List<Long> invitedShopIds;
    private List<InquiryInvitationScopeRespVO> invitedScopes;
}
