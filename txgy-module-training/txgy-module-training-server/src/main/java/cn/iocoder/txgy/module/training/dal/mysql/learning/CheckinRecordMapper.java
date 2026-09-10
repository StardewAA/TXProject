package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.CheckinRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学习打卡记录 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface CheckinRecordMapper extends BaseMapperX<CheckinRecordDO> {

    default List<CheckinRecordDO> selectListByUser(Long userId) {
        return selectList(new LambdaQueryWrapperX<CheckinRecordDO>()
                .eq(CheckinRecordDO::getUserId, userId)
                .orderByDesc(CheckinRecordDO::getId));
    }

    default List<CheckinRecordDO> selectListByUserAndCourse(Long userId, Long courseId) {
        return selectList(new LambdaQueryWrapperX<CheckinRecordDO>()
                .eq(CheckinRecordDO::getUserId, userId)
                .eq(CheckinRecordDO::getCourseId, courseId)
                .orderByDesc(CheckinRecordDO::getId));
    }

    default CheckinRecordDO selectBySessionId(Long sessionId) {
        return selectOne(new LambdaQueryWrapperX<CheckinRecordDO>()
                .eq(CheckinRecordDO::getSessionId, sessionId)
                .last("limit 1"));
    }

}
