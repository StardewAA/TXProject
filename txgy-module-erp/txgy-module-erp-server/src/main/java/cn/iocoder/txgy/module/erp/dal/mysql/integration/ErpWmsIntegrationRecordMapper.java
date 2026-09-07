package cn.iocoder.txgy.module.erp.dal.mysql.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsIntegrationRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ErpWmsIntegrationRecordMapper extends BaseMapperX<ErpWmsIntegrationRecordDO> {

    default ErpWmsIntegrationRecordDO selectByRequestId(String requestId) {
        return selectOne(ErpWmsIntegrationRecordDO::getRequestId, requestId);
    }

}
