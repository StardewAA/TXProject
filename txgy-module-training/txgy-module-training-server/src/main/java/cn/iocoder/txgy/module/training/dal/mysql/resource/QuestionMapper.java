package cn.iocoder.txgy.module.training.dal.mysql.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.QuestionPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.QuestionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 试题 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface QuestionMapper extends BaseMapperX<QuestionDO> {

    default PageResult<QuestionDO> selectPage(QuestionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<QuestionDO>()
                .likeIfPresent(QuestionDO::getStem, reqVO.getKeyword())
                .eqIfPresent(QuestionDO::getQtype, reqVO.getQtype())
                .eqIfPresent(QuestionDO::getDifficulty, reqVO.getDifficulty())
                .eqIfPresent(QuestionDO::getStatus, reqVO.getStatus())
                .inIfPresent(QuestionDO::getId, reqVO.getQuestionIds())
                .orderByDesc(QuestionDO::getId));
    }

    default Long selectCountByStatus(Integer status) {
        return selectCount(new LambdaQueryWrapperX<QuestionDO>()
                .eqIfPresent(QuestionDO::getStatus, status));
    }

    default Long selectCountByQtype(Integer qtype) {
        return selectCount(new LambdaQueryWrapperX<QuestionDO>()
                .eqIfPresent(QuestionDO::getQtype, qtype));
    }

    default List<QuestionDO> selectListByIds(Collection<Long> ids) {
        return selectList(new LambdaQueryWrapperX<QuestionDO>()
                .in(QuestionDO::getId, ids));
    }

}
