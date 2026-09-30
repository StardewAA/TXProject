package cn.iocoder.txgy.module.trade.dal.mysql.checkout;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.trade.dal.dataobject.checkout.PlatformFundAllocationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PlatformFundAllocationMapper extends BaseMapperX<PlatformFundAllocationDO> {

    default List<PlatformFundAllocationDO> selectListByCheckout(Long checkoutId, String checkoutNo) {
        return selectList(new LambdaQueryWrapperX<PlatformFundAllocationDO>()
                .eq(PlatformFundAllocationDO::getCheckoutId, checkoutId)
                .eq(PlatformFundAllocationDO::getCheckoutNo, checkoutNo)
                .orderByAsc(PlatformFundAllocationDO::getTenantId,
                        PlatformFundAllocationDO::getShopId, PlatformFundAllocationDO::getId));
    }

}
