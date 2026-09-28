package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlatformCheckoutPlanMapper extends BaseMapperX<PlatformCheckoutPlanDO> {

    default List<PlatformCheckoutPlanDO> selectListByCheckout(Long checkoutId, String checkoutNo) {
        return selectList(new LambdaQueryWrapperX<PlatformCheckoutPlanDO>()
                .eq(PlatformCheckoutPlanDO::getCheckoutId, checkoutId)
                .eq(PlatformCheckoutPlanDO::getCheckoutNo, checkoutNo)
                .orderByAsc(PlatformCheckoutPlanDO::getTenantId, PlatformCheckoutPlanDO::getShopId,
                        PlatformCheckoutPlanDO::getId));
    }
}
