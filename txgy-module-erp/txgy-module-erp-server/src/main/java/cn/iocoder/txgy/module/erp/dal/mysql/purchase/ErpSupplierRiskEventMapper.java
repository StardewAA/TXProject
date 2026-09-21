package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierRiskEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpSupplierRiskEventMapper extends BaseMapperX<ErpSupplierRiskEventDO> {

    default List<ErpSupplierRiskEventDO> selectListBySupplierId(Long supplierId) {
        return selectList(new LambdaQueryWrapperX<ErpSupplierRiskEventDO>()
                .eq(ErpSupplierRiskEventDO::getSupplierId, supplierId)
                .orderByDesc(ErpSupplierRiskEventDO::getOccurredAt)
                .orderByDesc(ErpSupplierRiskEventDO::getId));
    }
}
