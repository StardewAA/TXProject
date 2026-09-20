package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsFulfillmentEventDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpLogisticsFulfillmentEventMapper extends BaseMapperX<ErpLogisticsFulfillmentEventDO> {

    default List<ErpLogisticsFulfillmentEventDO> selectListByRelationId(Long relationId) {
        return selectList(new LambdaQueryWrapperX<ErpLogisticsFulfillmentEventDO>()
                .eq(ErpLogisticsFulfillmentEventDO::getRelationId, relationId)
                .orderByAsc(ErpLogisticsFulfillmentEventDO::getOccurredAt)
                .orderByAsc(ErpLogisticsFulfillmentEventDO::getId));
    }

}
