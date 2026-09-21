package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsNodeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpLogisticsNodeMapper extends BaseMapperX<ErpLogisticsNodeDO> {

    default List<ErpLogisticsNodeDO> selectListByRelationId(Long relationId) {
        return selectList(new LambdaQueryWrapperX<ErpLogisticsNodeDO>()
                .eq(ErpLogisticsNodeDO::getRelationId, relationId)
                .orderByAsc(ErpLogisticsNodeDO::getEventVersion));
    }

}
