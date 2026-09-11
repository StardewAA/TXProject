package cn.iocoder.txgy.module.wms.dal.mysql.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.dal.dataobject.integration.WmsIntegrationRequestDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WmsIntegrationRequestMapper extends BaseMapperX<WmsIntegrationRequestDO> {

    default WmsIntegrationRequestDO selectByRequestId(String requestId) {
        return selectOne(new LambdaQueryWrapperX<WmsIntegrationRequestDO>()
                .eq(WmsIntegrationRequestDO::getRequestId, requestId));
    }
}
