package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpInquirySupplierDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpInquirySupplierMapper extends BaseMapperX<ErpInquirySupplierDO> {

    default List<ErpInquirySupplierDO> selectListByInquiryId(Long inquiryId) {
        return selectList(ErpInquirySupplierDO::getInquiryId, inquiryId);
    }

    default List<ErpInquirySupplierDO> selectListBySupplierId(Long supplierId) {
        return selectList(ErpInquirySupplierDO::getSupplierId, supplierId);
    }

    default ErpInquirySupplierDO selectByInquiryIdAndSupplierId(Long inquiryId, Long supplierId) {
        return selectOne(new LambdaQueryWrapperX<ErpInquirySupplierDO>()
                .eq(ErpInquirySupplierDO::getInquiryId, inquiryId)
                .eq(ErpInquirySupplierDO::getSupplierId, supplierId));
    }

    default List<ErpInquirySupplierDO> selectListByInquiryIds(Collection<Long> inquiryIds) {
        return selectList(ErpInquirySupplierDO::getInquiryId, inquiryIds);
    }

    default int deleteByInquiryId(Long inquiryId) {
        return delete(ErpInquirySupplierDO::getInquiryId, inquiryId);
    }

}
