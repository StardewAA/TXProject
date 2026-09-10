package cn.iocoder.txgy.module.training.dal.mysql.analytics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.analytics.ArchiveManualLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 档案人工变更日志 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface ArchiveManualLogMapper extends BaseMapperX<ArchiveManualLogDO> {
}
