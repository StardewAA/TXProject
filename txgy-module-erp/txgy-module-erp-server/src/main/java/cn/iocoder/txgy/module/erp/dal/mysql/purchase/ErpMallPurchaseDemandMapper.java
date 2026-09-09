package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpMallPurchaseDemandDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpMallPurchaseDemandMapper extends BaseMapperX<ErpMallPurchaseDemandDO> {

    default ErpMallPurchaseDemandDO selectByRequestId(String requestId) {
        return selectOne(ErpMallPurchaseDemandDO::getRequestId, requestId);
    }

    /**
     * Locks an existing request row, or its unique-index gap when it has not
     * been created yet. This serializes concurrent deliveries from the mall.
     */
    default ErpMallPurchaseDemandDO selectByRequestIdForUpdate(String requestId) {
        return selectOneForUpdate(ErpMallPurchaseDemandDO::getRequestId, requestId);
    }

    default ErpMallPurchaseDemandDO selectByMallDemandNo(String mallDemandNo) {
        return selectOne(ErpMallPurchaseDemandDO::getMallDemandNo, mallDemandNo);
    }

    default ErpMallPurchaseDemandDO selectByMallDemandNoForUpdate(String mallDemandNo) {
        return selectOneForUpdate(ErpMallPurchaseDemandDO::getMallDemandNo, mallDemandNo);
    }

    default ErpMallPurchaseDemandDO selectByErpRequestNo(String erpRequestNo) {
        return selectOne(ErpMallPurchaseDemandDO::getErpRequestNo, erpRequestNo);
    }

    default ErpMallPurchaseDemandDO selectByErpRequestNoForUpdate(String erpRequestNo) {
        return selectOneForUpdate(ErpMallPurchaseDemandDO::getErpRequestNo, erpRequestNo);
    }

}
