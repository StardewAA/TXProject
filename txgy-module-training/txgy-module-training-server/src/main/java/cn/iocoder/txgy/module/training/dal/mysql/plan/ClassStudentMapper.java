package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassStudentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /**
     * 物理清理该班【已逻辑删除】的学员绑定：唯一索引 uk_tcs_class_user(class_id, user_id, deleted) 含 deleted(bit)，
     * 学员移出后重新入班再删除会撞唯一键，故逻辑删除前先物理清掉历史记录
     */
    @Delete("DELETE FROM training_class_student WHERE class_id = #{classId} AND deleted = 1 AND tenant_id = #{tenantId}")
    int deleteTrashByClassId(@Param("classId") Long classId, @Param("tenantId") Long tenantId);

    default void deleteByClassId(Long classId) {
        deleteTrashByClassId(classId, TenantContextHolder.getRequiredTenantId());
        delete(new LambdaQueryWrapperX<ClassStudentDO>()
                .eq(ClassStudentDO::getClassId, classId));
    }

}
