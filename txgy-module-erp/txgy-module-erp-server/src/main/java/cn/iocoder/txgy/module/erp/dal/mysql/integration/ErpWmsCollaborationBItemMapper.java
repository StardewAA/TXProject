package cn.iocoder.txgy.module.erp.dal.mysql.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpWmsCollaborationBItemMapper extends BaseMapperX<ErpWmsCollaborationBItemDO> {

    default List<ErpWmsCollaborationBItemDO> selectListByCollaborationId(Long collaborationId) {
        return selectList(ErpWmsCollaborationBItemDO::getCollaborationId, collaborationId);
    }
}
