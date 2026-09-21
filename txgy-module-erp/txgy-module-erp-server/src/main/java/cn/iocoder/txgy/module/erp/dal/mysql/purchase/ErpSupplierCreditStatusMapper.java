package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierCreditStatusDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpSupplierCreditStatusMapper extends BaseMapperX<ErpSupplierCreditStatusDO> {

    default ErpSupplierCreditStatusDO selectBySupplierId(Long supplierId) {
        return selectOne(ErpSupplierCreditStatusDO::getSupplierId, supplierId);
    }

    default List<ErpSupplierCreditStatusDO> selectListBySupplierIds(Collection<Long> supplierIds) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ErpSupplierCreditStatusDO>()
                .in(ErpSupplierCreditStatusDO::getSupplierId, supplierIds));
    }
}
