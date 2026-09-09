package cn.iocoder.txgy.module.erp.dal.mysql.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpCollaborationConfirmLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpCollaborationConfirmLogMapper extends BaseMapperX<ErpCollaborationConfirmLogDO> {

    default List<ErpCollaborationConfirmLogDO> selectListByCollaborationId(Long collaborationId) {
        return selectList(new LambdaQueryWrapperX<ErpCollaborationConfirmLogDO>()
                .eq(ErpCollaborationConfirmLogDO::getCollaborationId, collaborationId)
                .orderByDesc(ErpCollaborationConfirmLogDO::getId));
    }
}
