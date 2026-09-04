package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.DocReadLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文档阅读明细 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface DocReadLogMapper extends BaseMapperX<DocReadLogDO> {

    default List<DocReadLogDO> selectListBySession(Long sessionId) {
        return selectList(new LambdaQueryWrapperX<DocReadLogDO>()
                .eq(DocReadLogDO::getSessionId, sessionId)
                .orderByAsc(DocReadLogDO::getPageNo));
    }

    default List<DocReadLogDO> selectListByUserAndCourse(Long userId, Long courseId) {
        return selectList(new LambdaQueryWrapperX<DocReadLogDO>()
                .eq(DocReadLogDO::getUserId, userId)
                .eq(DocReadLogDO::getCourseId, courseId));
    }

}
