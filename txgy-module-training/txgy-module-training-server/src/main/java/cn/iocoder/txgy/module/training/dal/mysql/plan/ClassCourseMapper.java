package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassCourseDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClassCourseMapper extends BaseMapperX<ClassCourseDO> {

    default List<ClassCourseDO> selectListByClassId(Long classId) {
        return selectList(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId)
                .orderByAsc(ClassCourseDO::getSort).orderByAsc(ClassCourseDO::getId));
    }

    default ClassCourseDO selectByClassIdAndCourseId(Long classId, Long courseId) {
        return selectOne(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId)
                .eq(ClassCourseDO::getCourseId, courseId)
                .last("limit 1"));
    }

    default Long selectCountByClassId(Long classId) {
        return selectCount(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId));
    }

    /**
     * 物理清理该班【已逻辑删除】的课程绑定：唯一索引 uk_tcc_class_course(class_id, course_id, deleted) 中
     * deleted 为 bit（0/1），反复定课时旧记录停留在 deleted=1，再次逻辑删除新记录会撞唯一键，故先物理清掉
     */
    @Delete("DELETE FROM training_class_course WHERE class_id = #{classId} AND deleted = 1 AND tenant_id = #{tenantId}")
    int deleteTrashByClassId(@Param("classId") Long classId, @Param("tenantId") Long tenantId);

    default void deleteByClassId(Long classId) {
        deleteTrashByClassId(classId, TenantContextHolder.getRequiredTenantId());
        delete(new LambdaQueryWrapperX<ClassCourseDO>()
                .eq(ClassCourseDO::getClassId, classId));
    }

}
