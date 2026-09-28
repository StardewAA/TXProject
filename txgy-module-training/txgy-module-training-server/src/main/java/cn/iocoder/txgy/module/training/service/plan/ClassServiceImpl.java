package cn.iocoder.txgy.module.training.service.plan;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassCourseRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassRespVO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.ClassSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassCourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassCourseMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassStudentMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.StudentTaskMapper;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.enums.plan.ClassStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 培训班 Service 实现类（模块三：培训管理核心）
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class ClassServiceImpl {

    @Resource
    private ClassMapper classMapper;
    @Resource
    private ClassCourseMapper classCourseMapper;
    @Resource
    private ClassStudentMapper classStudentMapper;
    @Resource
    private StudentTaskMapper studentTaskMapper;
    @Resource
    private CourseMapper courseMapper;

    // ========== 培训班 CRUD ==========

    @Transactional(rollbackFor = Exception.class)
    public Long createClass(ClassSaveReqVO createReqVO) {
        // 1. 校验培训班名称唯一
        validateClassNameDuplicate(null, createReqVO.getName());
        // 2. 插入主表
        ClassDO classDO = BeanUtils.toBean(createReqVO, ClassDO.class);
        classDO.setStatus(ClassStatusEnum.DRAFT.getStatus());
        classDO.setCourseCount(0);
        classDO.setStudentCount(0);
        classMapper.insert(classDO);
        // 3. 绑定课程清单（四定-定课）
        if (CollUtil.isNotEmpty(createReqVO.getCourseIds())) {
            bindCourses(classDO.getId(), createReqVO.getCourseIds());
        }
        return classDO.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateClass(ClassSaveReqVO updateReqVO) {
        // 1. 校验存在且可编辑（仅草稿）
        ClassDO classDO = validateClassEditable(updateReqVO.getId());
        validateClassNameDuplicate(updateReqVO.getId(), updateReqVO.getName());
        // 2. 更新基本信息
        ClassDO updateObj = BeanUtils.toBean(updateReqVO, ClassDO.class);
        classMapper.updateById(updateObj);
        // 3. 全量替换课程清单：已开班的学员任务会失效，仅当无在班学员时允许调整
        if (updateReqVO.getCourseIds() != null) {
            List<ClassStudentDO> students = classStudentMapper.selectListByClassId(classDO.getId(), 0);
            if (CollUtil.isNotEmpty(students)) {
                throw exception(PLAN_CLASS_COURSE_CLASS_FROZEN);
            }
            bindCourses(classDO.getId(), updateReqVO.getCourseIds());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteClass(Long id) {
        ClassDO classDO = validateClassExists(id);
        // 草稿（尚未开班）与已结束（已完成 / 已取消）允许删除；进行中的班仍在学习周期内，不允许删除
        boolean deletable = ClassStatusEnum.DRAFT.getStatus().equals(classDO.getStatus())
                || ClassStatusEnum.CANCELED.getStatus().equals(classDO.getStatus())
                || ClassStatusEnum.FINISHED.getStatus().equals(classDO.getStatus());
        if (!deletable) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_DELETE, getStatusName(classDO.getStatus()));
        }
        // 连带清理：课程绑定、学员绑定、学员学习任务（均逻辑删除，保留历史审计）
        classCourseMapper.deleteByClassId(id);
        studentTaskMapper.deleteByClassId(id);
        classStudentMapper.deleteByClassId(id);
        classMapper.deleteById(id);
    }

    public PageResult<ClassRespVO> getClassPage(ClassPageReqVO pageReqVO) {
        PageResult<ClassDO> pageResult = classMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(pageResult, ClassRespVO.class);
    }

    public ClassRespVO getClass(Long id) {
        ClassDO classDO = classMapper.selectById(id);
        if (classDO == null) {
            return null;
        }
        ClassRespVO respVO = BeanUtils.toBean(classDO, ClassRespVO.class);
        List<ClassCourseDO> courses = classCourseMapper.selectListByClassId(id);
        respVO.setCourseIds(courses.stream().map(ClassCourseDO::getCourseId).toList());
        return respVO;
    }

    // ========== 课程清单（四定-定课） ==========

    public List<ClassCourseRespVO> getClassCourseList(Long classId) {
        validateClassExists(classId);
        List<ClassCourseDO> courses = classCourseMapper.selectListByClassId(classId);
        List<ClassCourseRespVO> result = new ArrayList<>();
        for (ClassCourseDO course : courses) {
            ClassCourseRespVO respVO = new ClassCourseRespVO();
            respVO.setCourseId(course.getCourseId());
            respVO.setCourseCode(course.getCourseCode());
            respVO.setCourseTitle(course.getCourseTitle());
            respVO.setDurationMinutes(course.getDurationMinutes());
            result.add(respVO);
        }
        return result;
    }

    // ========== 班级状态机（定时-开班/结业/取消） ==========

    @Transactional(rollbackFor = Exception.class)
    public void startClass(Long id) {
        ClassDO classDO = validateClassExists(id);
        if (!ClassStatusEnum.DRAFT.getStatus().equals(classDO.getStatus())) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_START, getStatusName(classDO.getStatus()));
        }
        // 开班前校验：已绑定课程 + 已设置截止时间
        Long courseCount = classCourseMapper.selectCountByClassId(id);
        if (courseCount == null || courseCount <= 0) {
            throw exception(PLAN_CLASS_COURSE_REQUIRED_START);
        }
        if (classDO.getDeadline() == null) {
            throw exception(PLAN_CLASS_DEADLINE_REQUIRED_START);
        }
        ClassDO update = new ClassDO();
        update.setId(id);
        update.setStatus(ClassStatusEnum.RUNNING.getStatus());
        update.setCode(classDO.getCode() == null ? "TB" + id : classDO.getCode());
        update.setStartTime(classDO.getStartTime() == null ? LocalDateTime.now() : classDO.getStartTime());
        classMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishClass(Long id) {
        ClassDO classDO = validateClassExists(id);
        if (!ClassStatusEnum.RUNNING.getStatus().equals(classDO.getStatus())) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_FINISH, getStatusName(classDO.getStatus()));
        }
        ClassDO update = new ClassDO();
        update.setId(id);
        update.setStatus(ClassStatusEnum.FINISHED.getStatus());
        classMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelClass(Long id) {
        ClassDO classDO = validateClassExists(id);
        boolean allowed = ClassStatusEnum.DRAFT.getStatus().equals(classDO.getStatus())
                || ClassStatusEnum.RUNNING.getStatus().equals(classDO.getStatus());
        if (!allowed) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_CANCEL, getStatusName(classDO.getStatus()));
        }
        ClassDO update = new ClassDO();
        update.setId(id);
        update.setStatus(ClassStatusEnum.CANCELED.getStatus());
        classMapper.updateById(update);
    }

    // ========== 私有方法 ==========

    /**
     * 全量绑定课程清单：校验课程为【已上架】后先清空再重建，并刷新课程数冗余
     */
    private void bindCourses(Long classId, List<Long> courseIds) {
        classCourseMapper.deleteByClassId(classId);
        int sort = 0;
        for (Long courseId : courseIds) {
            CourseDO course = courseMapper.selectById(courseId);
            if (course == null) {
                throw exception(COURSE_NOT_EXISTS);
            }
            // 仅已上架课程可被培训班引用（草稿/待审核等课程不允许定课）
            if (!CourseStatusEnum.ON_SHELF.getStatus().equals(course.getStatus())) {
                throw exception(PLAN_CLASS_COURSE_ONLY_ONLINE, course.getTitle());
            }
            if (classCourseMapper.selectByClassIdAndCourseId(classId, courseId) != null) {
                throw exception(PLAN_CLASS_COURSE_DUPLICATE);
            }
            ClassCourseDO bind = new ClassCourseDO();
            bind.setClassId(classId);
            bind.setCourseId(courseId);
            bind.setCourseCode(course.getCode());
            bind.setCourseTitle(course.getTitle());
            bind.setDurationMinutes(course.getDurationMinutes());
            bind.setSort(sort++);
            classCourseMapper.insert(bind);
        }
        refreshCourseCount(classId);
    }

    /**
     * 刷新培训班课程数冗余
     */
    private void refreshCourseCount(Long classId) {
        Long count = classCourseMapper.selectCountByClassId(classId);
        ClassDO update = new ClassDO();
        update.setId(classId);
        update.setCourseCount(count == null ? 0 : count.intValue());
        classMapper.updateById(update);
    }

    private void validateClassNameDuplicate(Long id, String name) {
        ClassDO classDO = classMapper.selectByName(name);
        if (classDO == null) {
            return;
        }
        if (id == null || !classDO.getId().equals(id)) {
            throw exception(PLAN_CLASS_NAME_DUPLICATE, name);
        }
    }

    private ClassDO validateClassExists(Long id) {
        ClassDO classDO = classMapper.selectById(id);
        if (classDO == null) {
            throw exception(PLAN_CLASS_NOT_EXISTS);
        }
        return classDO;
    }

    private ClassDO validateClassEditable(Long id) {
        ClassDO classDO = validateClassExists(id);
        if (!ClassStatusEnum.DRAFT.getStatus().equals(classDO.getStatus())) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_UPDATE, getStatusName(classDO.getStatus()));
        }
        return classDO;
    }

    private String getStatusName(Integer status) {
        ClassStatusEnum statusEnum = ClassStatusEnum.getByStatus(status);
        return statusEnum == null ? String.valueOf(status) : statusEnum.getName();
    }

    boolean isEditableForStudent(ClassDO classDO) {
        return ClassStatusEnum.DRAFT.getStatus().equals(classDO.getStatus())
                || ClassStatusEnum.RUNNING.getStatus().equals(classDO.getStatus());
    }

    boolean isActiveForRemind(ClassDO classDO) {
        return ClassStatusEnum.DRAFT.getStatus().equals(classDO.getStatus())
                || ClassStatusEnum.RUNNING.getStatus().equals(classDO.getStatus());
    }

    Integer countActiveStudents(Long classId) {
        List<ClassStudentDO> students = classStudentMapper.selectListByClassId(classId, 0);
        return students.size();
    }

    ClassDO exists(Long id) {
        return validateClassExists(id);
    }

    String getDeadlineText(ClassDO classDO) {
        return classDO.getDeadline() == null ? "待定" : Objects.toString(classDO.getDeadline().withSecond(0).withNano(0), "待定");
    }

}
