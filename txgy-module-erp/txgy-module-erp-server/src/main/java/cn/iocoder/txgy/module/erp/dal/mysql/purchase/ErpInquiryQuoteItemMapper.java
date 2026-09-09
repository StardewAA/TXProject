package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquiryQuoteItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpInquiryQuoteItemMapper extends BaseMapperX<ErpInquiryQuoteItemDO> {
    default List<ErpInquiryQuoteItemDO> selectListByQuoteId(Long quoteId) { return selectList(ErpInquiryQuoteItemDO::getQuoteId, quoteId); }
    default List<ErpInquiryQuoteItemDO> selectListByQuoteIds(Collection<Long> quoteIds) { return selectList(ErpInquiryQuoteItemDO::getQuoteId, quoteIds); }
    default int deleteByQuoteId(Long quoteId) { return delete(ErpInquiryQuoteItemDO::getQuoteId, quoteId); }
}
