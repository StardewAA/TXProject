package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsRelationItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpLogisticsRelationItemMapper extends BaseMapperX<ErpLogisticsRelationItemDO> {

    default List<ErpLogisticsRelationItemDO> selectListByRelationId(Long relationId) {
        return selectList(ErpLogisticsRelationItemDO::getRelationId, relationId);
    }

}
