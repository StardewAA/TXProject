package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpDeliveryReceiptDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpDeliveryReceiptMapper extends BaseMapperX<ErpDeliveryReceiptDO> {

    default List<ErpDeliveryReceiptDO> selectListByRelationId(Long relationId) {
        return selectList(new LambdaQueryWrapperX<ErpDeliveryReceiptDO>()
                .eq(ErpDeliveryReceiptDO::getRelationId, relationId)
                .orderByAsc(ErpDeliveryReceiptDO::getEventVersion));
    }

}
