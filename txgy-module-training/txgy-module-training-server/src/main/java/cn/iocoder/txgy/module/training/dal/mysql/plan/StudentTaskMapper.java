package cn.iocoder.txgy.module.training.dal.mysql.plan;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StudentTaskMapper extends BaseMapperX<StudentTaskDO> {

    default List<StudentTaskDO> selectListByClassId(Long classId) {
        return selectList(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId));
    }

    default List<StudentTaskDO> selectListByClassIdAndUserIds(Long classId, Collection<Long> userIds) {
        return selectList(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .inIfPresent(StudentTaskDO::getUserId, userIds));
    }

    default StudentTaskDO selectByClassUserCourse(Long classId, Long userId, Long courseId) {
        return selectOne(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .eq(StudentTaskDO::getUserId, userId)
                .eq(StudentTaskDO::getCourseId, courseId)
                .last("limit 1"));
    }

    /**
     * 物理清理【已逻辑删除】的学员任务：唯一索引 uk_tst_class_user_course(class_id, user_id, course_id, deleted)
     * 含 deleted(bit)，学员移出后重新入班再移出会撞唯一键，故逻辑删除前先物理清掉历史记录
     */
    @Delete("<script>DELETE FROM training_student_task WHERE class_id = #{classId} AND deleted = 1"
            + " AND tenant_id = #{tenantId}"
            + "<if test=\"userIds != null and userIds.size() &gt; 0\"> AND user_id IN"
            + " <foreach collection=\"userIds\" item=\"userId\" open=\"(\" separator=\",\" close=\")\">#{userId}</foreach>"
            + "</if></script>")
    int deleteTrash(@Param("classId") Long classId, @Param("tenantId") Long tenantId,
                    @Param("userIds") Collection<Long> userIds);

    default void deleteByClassId(Long classId) {
        deleteTrash(classId, TenantContextHolder.getRequiredTenantId(), null);
        delete(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId));
    }

    default void deleteByClassIdAndUserIds(Long classId, Collection<Long> userIds) {
        deleteTrash(classId, TenantContextHolder.getRequiredTenantId(), userIds);
        delete(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .inIfPresent(StudentTaskDO::getUserId, userIds));
    }

    default Long selectCountUnfinishedByClassId(Long classId) {
        return selectCount(new LambdaQueryWrapperX<StudentTaskDO>()
                .eq(StudentTaskDO::getClassId, classId)
                .ne(StudentTaskDO::getStatus, 2));
    }

}
