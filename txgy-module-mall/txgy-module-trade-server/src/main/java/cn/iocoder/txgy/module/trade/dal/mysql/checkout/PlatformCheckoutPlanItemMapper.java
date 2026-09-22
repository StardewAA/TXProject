package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutPlanItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlatformCheckoutPlanItemMapper extends BaseMapperX<PlatformCheckoutPlanItemDO> {

    default List<PlatformCheckoutPlanItemDO> selectListByCheckout(Long checkoutId, String checkoutNo) {
        return selectList(new LambdaQueryWrapperX<PlatformCheckoutPlanItemDO>()
                .eq(PlatformCheckoutPlanItemDO::getCheckoutId, checkoutId)
                .eq(PlatformCheckoutPlanItemDO::getCheckoutNo, checkoutNo)
                .orderByAsc(PlatformCheckoutPlanItemDO::getPlanId, PlatformCheckoutPlanItemDO::getId));
    }
}
