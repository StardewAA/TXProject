package cn.iocoder.txgy.module.product.dal.mysql.inquiry;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.product.dal.dataobject.inquiry.InquiryQuoteItemDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InquiryQuoteItemMapper extends BaseMapperX<InquiryQuoteItemDO> {

    /** 草稿编辑时物理替换明细；成交后报价不可再进入此路径。 */
    @Delete("DELETE FROM product_purchase_inquiry_quote_item WHERE quote_id = #{quoteId}")
    int deleteByQuoteIdPhysically(@Param("quoteId") Long quoteId);
}
