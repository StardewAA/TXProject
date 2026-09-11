package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseRequestItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ErpPurchaseRequestItemMapper extends BaseMapperX<ErpPurchaseRequestItemDO> {
    default List<ErpPurchaseRequestItemDO> selectListByRequestId(Long requestId) { return selectList(ErpPurchaseRequestItemDO::getRequestId, requestId); }
    default List<ErpPurchaseRequestItemDO> selectListByRequestIds(Collection<Long> requestIds) { return selectList(ErpPurchaseRequestItemDO::getRequestId, requestIds); }
    default int deleteByRequestId(Long requestId) { return delete(ErpPurchaseRequestItemDO::getRequestId, requestId); }
}
