package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryInvitedShopDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InquiryInvitedShopMapper extends BaseMapperX<InquiryInvitedShopDO> {

    @Delete("DELETE FROM product_purchase_inquiry_invited_shop WHERE tenant_id = #{tenantId} AND inquiry_id = #{inquiryId}")
    int deletePhysicallyByInquiryId(@Param("tenantId") Long tenantId, @Param("inquiryId") Long inquiryId);
}
