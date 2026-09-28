package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchasePlanItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpPurchasePlanItemMapper extends BaseMapperX<ErpPurchasePlanItemDO> {
    default List<ErpPurchasePlanItemDO> selectListByPlanId(Long planId) { return selectList(ErpPurchasePlanItemDO::getPlanId, planId); }
    default List<ErpPurchasePlanItemDO> selectListByPlanIds(Collection<Long> planIds) { return selectList(ErpPurchasePlanItemDO::getPlanId, planIds); }
    default int deleteByPlanId(Long planId) { return delete(ErpPurchasePlanItemDO::getPlanId, planId); }
}
