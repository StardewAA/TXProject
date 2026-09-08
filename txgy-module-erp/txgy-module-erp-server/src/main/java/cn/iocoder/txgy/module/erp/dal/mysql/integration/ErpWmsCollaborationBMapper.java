package cn.iocoder.txgy.module.erp.dal.mysql.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface ErpWmsCollaborationBMapper extends BaseMapperX<ErpWmsCollaborationBDO> {
    default ErpWmsCollaborationBDO selectByCollaborationNo(String collaborationNo) {
        return selectOne(ErpWmsCollaborationBDO::getCollaborationNo, collaborationNo);
    }

    default int updateByIdAndBusinessStatus(Long id, String businessStatus, ErpWmsCollaborationBDO updateObj) {
        return update(updateObj, new LambdaUpdateWrapper<ErpWmsCollaborationBDO>()
                .eq(ErpWmsCollaborationBDO::getId, id)
                .eq(ErpWmsCollaborationBDO::getBusinessStatus, businessStatus));
    }
}
