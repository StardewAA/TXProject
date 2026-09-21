package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierAdmissionCategoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpSupplierAdmissionCategoryMapper extends BaseMapperX<ErpSupplierAdmissionCategoryDO> {

    default List<ErpSupplierAdmissionCategoryDO> selectListBySupplierId(Long supplierId) {
        return selectList(new LambdaQueryWrapperX<ErpSupplierAdmissionCategoryDO>()
                .eq(ErpSupplierAdmissionCategoryDO::getSupplierId, supplierId)
                .orderByDesc(ErpSupplierAdmissionCategoryDO::getId));
    }
}
