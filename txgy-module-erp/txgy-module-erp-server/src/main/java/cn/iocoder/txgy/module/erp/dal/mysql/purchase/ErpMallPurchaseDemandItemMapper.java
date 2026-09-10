package cn.iocoder.txgy.module.erp.dal.mysql.purchase;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpMallPurchaseDemandItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpMallPurchaseDemandItemMapper extends BaseMapperX<ErpMallPurchaseDemandItemDO> {

    default List<ErpMallPurchaseDemandItemDO> selectListByDemandId(Long demandId) {
        return selectList(ErpMallPurchaseDemandItemDO::getDemandId, demandId);
    }

}
