package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseContractTemplateDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpPurchaseContractTemplateMapper extends BaseMapperX<ErpPurchaseContractTemplateDO> {
    default List<ErpPurchaseContractTemplateDO> selectAllEnabled() {
        return selectList(new LambdaQueryWrapperX<ErpPurchaseContractTemplateDO>()
                .eq(ErpPurchaseContractTemplateDO::getStatus, 0)
                .orderByDesc(ErpPurchaseContractTemplateDO::getDefaultFlag)
                .orderByDesc(ErpPurchaseContractTemplateDO::getId));
    }
}
