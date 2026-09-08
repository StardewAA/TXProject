package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ClassStudentMapper extends BaseMapperX<ClassStudentDO> {

    default List<ClassStudentDO> selectListByClassId(Long classId, Integer status) {
        return selectList(new LambdaQueryWrapperX<ClassStudentDO>()
                .eq(ClassStudentDO::getClassId, classId)
                .eqIfPresent(ClassStudentDO::getStatus, status)
                .orderByAsc(ClassStudentDO::getId));
    }

    default ClassStudentDO selectByClassIdAndUserId(Long classId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<ClassStudentDO>()
                .eq(ClassStudentDO::getClassId, classId)
                .eq(ClassStudentDO::getUserId, userId)
                .last("limit 1"));
    }

    default PageResult<ClassStudentDO> selectPage(ClassStudentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ClassStudentDO>()
                .eq(ClassStudentDO::getClassId, reqVO.getClassId())
                .eq(ClassStudentDO::getStatus, 0)
                .likeIfPresent(ClassStudentDO::getUserName, reqVO.getUserName())
                .likeIfPresent(ClassStudentDO::getDeptName, reqVO.getDeptName())
                .orderByDesc(ClassStudentDO::getId));
    }

}
