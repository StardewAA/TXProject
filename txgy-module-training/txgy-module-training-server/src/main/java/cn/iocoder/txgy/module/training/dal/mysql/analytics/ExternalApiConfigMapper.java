package cn.iocoder.txgy.module.training.dal.mysql.analytics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.analytics.ExternalApiConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部API接口配置 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface ExternalApiConfigMapper extends BaseMapperX<ExternalApiConfigDO> {
}
