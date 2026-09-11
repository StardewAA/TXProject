package cn.iocoder.txgy.module.training.dal.mysql.analytics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.analytics.StudyBehaviorLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 培训学习行为日志 Mapper
 * // TODO[模块九-学习行为] 按企业/部门/日期聚合统计查询后续实现
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface StudyBehaviorLogMapper extends BaseMapperX<StudyBehaviorLogDO> {
}
