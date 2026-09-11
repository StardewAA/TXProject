package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionTagDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 试题-标签关联 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface QuestionTagMapper extends BaseMapperX<QuestionTagDO> {

    default List<QuestionTagDO> selectListByQuestionId(Long questionId) {
        return selectList(new LambdaQueryWrapperX<QuestionTagDO>()
                .eq(QuestionTagDO::getQuestionId, questionId));
    }

    default List<QuestionTagDO> selectListByTagId(Long tagId) {
        return selectList(new LambdaQueryWrapperX<QuestionTagDO>()
                .eq(QuestionTagDO::getTagId, tagId));
    }

    default List<QuestionTagDO> selectListByQuestionIds(List<Long> questionIds) {
        return selectList(new LambdaQueryWrapperX<QuestionTagDO>()
                .in(QuestionTagDO::getQuestionId, questionIds));
    }

    default void deleteByQuestionId(Long questionId) {
        delete(new LambdaQueryWrapperX<QuestionTagDO>()
                .eq(QuestionTagDO::getQuestionId, questionId));
    }

}
