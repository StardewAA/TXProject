package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquiryItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpInquiryItemMapper extends BaseMapperX<ErpInquiryItemDO> {
    default List<ErpInquiryItemDO> selectListByInquiryId(Long inquiryId) { return selectList(ErpInquiryItemDO::getInquiryId, inquiryId); }
    default List<ErpInquiryItemDO> selectListByInquiryIds(Collection<Long> inquiryIds) { return selectList(ErpInquiryItemDO::getInquiryId, inquiryIds); }
    default int deleteByInquiryId(Long inquiryId) { return delete(ErpInquiryItemDO::getInquiryId, inquiryId); }
}
