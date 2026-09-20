package cn.iocoder.txgy.module.erp.dal.mysql.logistics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.logistics.ErpLogisticsExceptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpLogisticsExceptionMapper extends BaseMapperX<ErpLogisticsExceptionDO> {

    default ErpLogisticsExceptionDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(ErpLogisticsExceptionDO::getId, id);
    }

    default List<ErpLogisticsExceptionDO> selectListByRelationId(Long relationId) {
        return selectList(new LambdaQueryWrapperX<ErpLogisticsExceptionDO>()
                .eq(ErpLogisticsExceptionDO::getRelationId, relationId)
                .orderByDesc(ErpLogisticsExceptionDO::getId));
    }

    default long selectOpenCountByRelationId(Long relationId) {
        return selectCount(new LambdaQueryWrapperX<ErpLogisticsExceptionDO>()
                .eq(ErpLogisticsExceptionDO::getRelationId, relationId)
                .eq(ErpLogisticsExceptionDO::getStatus, "OPEN"));
    }

}
