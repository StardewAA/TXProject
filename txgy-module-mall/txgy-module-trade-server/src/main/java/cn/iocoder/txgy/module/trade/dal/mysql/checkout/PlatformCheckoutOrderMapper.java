package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformCheckoutOrderDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlatformCheckoutOrderMapper extends BaseMapperX<PlatformCheckoutOrderDO> {

    default List<PlatformCheckoutOrderDO> selectListByCheckout(Long checkoutId, String checkoutNo) {
        return selectList(new LambdaQueryWrapperX<PlatformCheckoutOrderDO>()
                .eq(PlatformCheckoutOrderDO::getCheckoutId, checkoutId)
                .eq(PlatformCheckoutOrderDO::getCheckoutNo, checkoutNo)
                .orderByAsc(PlatformCheckoutOrderDO::getTenantId,
                        PlatformCheckoutOrderDO::getShopId, PlatformCheckoutOrderDO::getId));
    }

}
