package cn.iocoder.txgy.module.training.dal.mysql.basedata;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.LearningMapDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学习地图 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface LearningMapMapper extends BaseMapperX<LearningMapDO> {

    default PageResult<LearningMapDO> selectPage(LearningMapPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<LearningMapDO>()
                .eqIfPresent(LearningMapDO::getJobCode, reqVO.getJobCode())
                .likeIfPresent(LearningMapDO::getJobName, reqVO.getJobName())
                .eqIfPresent(LearningMapDO::getStage, reqVO.getStage())
                .eqIfPresent(LearningMapDO::getStageNo, reqVO.getStageNo())
                .eqIfPresent(LearningMapDO::getCourseCode, reqVO.getCourseCode())
                .likeIfPresent(LearningMapDO::getCourseName, reqVO.getCourseName())
                .betweenIfPresent(LearningMapDO::getCreateTime, reqVO.getCreateTime())
                .orderByAsc(LearningMapDO::getJobCode)
                .orderByAsc(LearningMapDO::getStageNo)
                .orderByAsc(LearningMapDO::getSort)
                .orderByAsc(LearningMapDO::getId));
    }

    /**
     * 按工种查询学习地图课程（按阶段序号 + 阶段内排序）
     */
    default List<LearningMapDO> selectListByJobCode(String jobCode) {
        return selectList(new LambdaQueryWrapperX<LearningMapDO>()
                .eq(LearningMapDO::getJobCode, jobCode)
                .orderByAsc(LearningMapDO::getStageNo)
                .orderByAsc(LearningMapDO::getSort)
                .orderByAsc(LearningMapDO::getId));
    }

    /**
     * 统计某工种下的学习地图课程数量（删除工种前引用校验）
     */
    default Long selectCountByJobCode(String jobCode) {
        return selectCount(new LambdaQueryWrapperX<LearningMapDO>()
                .eq(LearningMapDO::getJobCode, jobCode));
    }

    /**
     * 统计某课程被学习地图引用的次数（课程分类/课程删除保护）
     */
    default Long selectCountByCourseCode(String courseCode) {
        return selectCount(new LambdaQueryWrapperX<LearningMapDO>()
                .eq(LearningMapDO::getCourseCode, courseCode));
    }

}
