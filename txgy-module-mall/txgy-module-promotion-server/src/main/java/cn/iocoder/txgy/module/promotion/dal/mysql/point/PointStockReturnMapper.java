package cn.iocoder.txgy.module.promotion.dal.mysql.point;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.promotion.dal.dataobject.point.PointStockReturnDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PointStockReturnMapper extends BaseMapperX<PointStockReturnDO> {

    default PointStockReturnDO selectByOrderItemId(Long tenantId, Long orderItemId) {
        return selectOne(new LambdaQueryWrapperX<PointStockReturnDO>()
                .eq(PointStockReturnDO::getTenantId, tenantId)
                .eq(PointStockReturnDO::getOrderItemId, orderItemId));
    }

    /** 当前读，避免等待活动行锁后仍读到事务开始时的旧快照。 */
    default PointStockReturnDO selectByOrderItemIdForUpdate(Long tenantId, Long orderItemId) {
        return selectOne(new LambdaQueryWrapperX<PointStockReturnDO>()
                .eq(PointStockReturnDO::getTenantId, tenantId)
                .eq(PointStockReturnDO::getOrderItemId, orderItemId)
                .last("FOR UPDATE"));
    }
}
