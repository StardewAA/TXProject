package cn.iocoder.txgy.module.training.service.learning;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.CoursePageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.AdminCourseTrackRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.AssignCandidateRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearnerProgressRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearnerTaskRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningAssignReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningClassStudentRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningStatRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningTaskPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnSessionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.ClassStudentDO;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.StudentTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnSessionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnerTaskMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.ClassStudentMapper;
import cn.iocoder.txgy.module.training.dal.mysql.plan.StudentTaskMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerSourceTypeEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerTaskStatusEnum;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import cn.iocoder.txgy.module.training.service.learner.LearnerProgressCalculator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 管理端-学员学习中心 聚合服务实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class AdminLearningServiceImpl implements AdminLearningService {

    @Resource
    private LearnerTaskMapper learnerTaskMapper;
    @Resource
    private LearnSessionMapper learnSessionMapper;
    @Resource
    private CourseService courseService;
    @Resource
    private LearnerProgressCalculator progressCalculator;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private ClassStudentMapper classStudentMapper;
    @Resource
    private StudentTaskMapper studentTaskMapper;
    @Resource
    private ClassMapper classMapper;

    /**
     * 任务来源：培训班任务（培训管理-学员入班时生成的「学员 × 课程」任务）
     */
    private static final String SOURCE_TYPE_CLASS = "class";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assign(LearningAssignReqVO reqVO) {
        CourseDO course = courseService.getCourse(reqVO.getCourseId());
        if (course == null) {
            throw exception(ErrorCodeConstants.COURSE_NOT_EXISTS);
        }
        if (!Objects.equals(course.getStatus(), CourseStatusEnum.ON_SHELF.getStatus())) {
            throw exception(ErrorCodeConstants.LEARNER_COURSE_NOT_ONLINE);
        }
        int added = 0;
        for (Long userId : reqVO.getUserIds()) {
            LearnerTaskDO exist = learnerTaskMapper.selectByUserIdAndCourseId(userId, reqVO.getCourseId());
            if (exist != null) {
                log.info("[assign][学员 {} 已有课程 {} 任务，幂等跳过]", userId, reqVO.getCourseId());
                continue;
            }
            LearnerTaskDO task = new LearnerTaskDO();
            task.setUserId(userId);
            task.setCourseId(course.getId());
            task.setSourceType(LearnerSourceTypeEnum.ASSIGN.getSourceType());
            task.setStatus(LearnerTaskStatusEnum.TODO.getStatus());
            task.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            task.setCreditTotal(course.getCredit());
            task.setCreditEarned(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            task.setRemark(reqVO.getRemark());
            learnerTaskMapper.insert(task);
            added++;
        }
        return added;
    }

    @Override
    public AssignCandidateRespVO getAssignCandidates() {
        AssignCandidateRespVO respVO = new AssignCandidateRespVO();

        // 1. 已上架 + 当前生效版本的课程
        CoursePageReqVO coursePageReqVO = new CoursePageReqVO();
        coursePageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        coursePageReqVO.setStatus(CourseStatusEnum.ON_SHELF.getStatus());
        coursePageReqVO.setIsCurrent(Boolean.TRUE);
        PageResult<CourseDO> coursePage = courseService.getCoursePage(coursePageReqVO);
        List<AssignCandidateRespVO.CourseItem> courses = new ArrayList<>();
        if (coursePage != null && !CollectionUtils.isEmpty(coursePage.getList())) {
            for (CourseDO course : coursePage.getList()) {
                AssignCandidateRespVO.CourseItem item = new AssignCandidateRespVO.CourseItem();
                item.setId(course.getId());
                item.setTitle(course.getTitle());
                item.setCredit(course.getCredit());
                item.setDurationMinutes(course.getDurationMinutes());
                item.setCoverUrl(course.getCoverUrl());
                item.setLecturer(course.getLecturer());
                courses.add(item);
            }
        }
        respVO.setCourses(courses);

        // 2. 指派候选学员：优先取「培训管理-培训班在班学员」，无在班学员时回退历史学习任务学员
        List<AssignCandidateRespVO.UserItem> users = new ArrayList<>();
        List<LearningClassStudentRespVO> classStudents = listClassStudents();
        if (!CollectionUtils.isEmpty(classStudents)) {
            for (LearningClassStudentRespVO student : classStudents) {
                AssignCandidateRespVO.UserItem item = new AssignCandidateRespVO.UserItem();
                item.setId(student.getUserId());
                item.setNickname(student.getUserName());
                users.add(item);
            }
        } else {
            List<LearnerTaskDO> allTasks = learnerTaskMapper.selectList(
                    new LambdaQueryWrapperX<LearnerTaskDO>().orderByDesc(LearnerTaskDO::getId));
            List<Long> userIds = allTasks.stream().map(LearnerTaskDO::getUserId).distinct()
                    .collect(Collectors.toList());
            Map<Long, String> nicknames = fetchNicknames(userIds);
            for (Long userId : userIds) {
                AssignCandidateRespVO.UserItem item = new AssignCandidateRespVO.UserItem();
                item.setId(userId);
                item.setNickname(nicknames.get(userId));
                users.add(item);
            }
        }
        respVO.setUserCount((long) users.size());
        respVO.setUsers(users);
        return respVO;
    }

    @Override
    public List<LearningClassStudentRespVO> listClassStudents() {
        // 1. 全部在班学员（status=0），按学员去重
        List<ClassStudentDO> students = classStudentMapper.selectList(new LambdaQueryWrapperX<ClassStudentDO>()
                .eq(ClassStudentDO::getStatus, 0)
                .orderByAsc(ClassStudentDO::getId));
        if (CollectionUtils.isEmpty(students)) {
            return Collections.emptyList();
        }
        // 2. 补齐培训班名称
        List<Long> classIds = students.stream().map(ClassStudentDO::getClassId).filter(Objects::nonNull)
                .distinct().collect(Collectors.toList());
        Map<Long, String> classNames = new HashMap<>();
        for (ClassDO classDO : classMapper.selectList(new LambdaQueryWrapperX<ClassDO>()
                .inIfPresent(ClassDO::getId, classIds))) {
            classNames.put(classDO.getId(), classDO.getName());
        }
        // 3. 同一学员可能同时在多个培训班 → 按 userId 去重，班级名称拼接展示
        Map<Long, LearningClassStudentRespVO> merged = new LinkedHashMap<>();
        for (ClassStudentDO student : students) {
            if (student.getUserId() == null) {
                continue;
            }
            LearningClassStudentRespVO vo = merged.get(student.getUserId());
            if (vo == null) {
                vo = new LearningClassStudentRespVO();
                vo.setUserId(student.getUserId());
                vo.setUserName(student.getUserName());
                vo.setDeptName(student.getDeptName());
                vo.setClassName(classNames.get(student.getClassId()));
                vo.setClassCount(1);
                vo.setJoinTime(student.getJoinTime());
                merged.put(student.getUserId(), vo);
            } else {
                String className = classNames.get(student.getClassId());
                if (StringUtils.hasText(className)) {
                    String exist = vo.getClassName();
                    vo.setClassName(StringUtils.hasText(exist) ? exist + "、" + className : className);
                }
                vo.setClassCount(nvlInt(vo.getClassCount()) + 1);
                if (student.getJoinTime() != null
                        && (vo.getJoinTime() == null || student.getJoinTime().isAfter(vo.getJoinTime()))) {
                    vo.setJoinTime(student.getJoinTime());
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public List<LearnerProgressRespVO> getLearnerProgress(Long classId) {
        // 1. 学员档案：姓名 / 部门 / 所在培训班（培训班学员优先，非培训班学员回退系统昵称）
        List<ClassStudentDO> students = classId == null
                ? classStudentMapper.selectList(new LambdaQueryWrapperX<ClassStudentDO>().eq(ClassStudentDO::getStatus, 0))
                : classStudentMapper.selectListByClassId(classId, 0);
        Map<Long, String> deptMap = new HashMap<>();
        Map<Long, String> classNames = new HashMap<>();
        if (classId != null) {
            ClassDO classDO = classMapper.selectById(classId);
            if (classDO != null) {
                classNames.put(classId, classDO.getName());
            }
        } else {
            List<Long> classIds = students.stream().map(ClassStudentDO::getClassId).filter(Objects::nonNull)
                    .distinct().collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(classIds)) {
                for (ClassDO classDO : classMapper.selectList(new LambdaQueryWrapperX<ClassDO>()
                        .inIfPresent(ClassDO::getId, classIds))) {
                    classNames.put(classDO.getId(), classDO.getName());
                }
            }
        }
        Map<Long, String> userNames = new HashMap<>();
        Map<Long, String> userClassNames = new HashMap<>();
        for (ClassStudentDO student : students) {
            if (student.getUserId() == null) {
                continue;
            }
            userNames.putIfAbsent(student.getUserId(), student.getUserName());
            deptMap.putIfAbsent(student.getUserId(), student.getDeptName());
            String className = classNames.get(student.getClassId());
            if (StringUtils.hasText(className)) {
                userClassNames.merge(student.getUserId(), className,
                        (a, b) -> a.contains(b) ? a : a + "、" + b);
            }
        }
        // 2. 合并任务（培训班任务 + 学习任务 → 学员+课程 去重）
        List<UnifiedTask> tasks = buildUnifiedTasks(classId);
        if (CollectionUtils.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        // 3. 按学员聚合
        Map<Long, List<UnifiedTask>> byUser = tasks.stream()
                .collect(Collectors.groupingBy(UnifiedTask::getUserId));
        List<LearnerProgressRespVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<UnifiedTask>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            List<UnifiedTask> userTasks = entry.getValue();
            LearnerProgressRespVO vo = new LearnerProgressRespVO();
            vo.setUserId(userId);
            vo.setUserName(userNames.get(userId));
            if (vo.getUserName() == null) {
                vo.setUserName(fetchNicknames(Collections.singletonList(userId)).get(userId));
            }
            vo.setDeptName(deptMap.get(userId));
            vo.setClassName(userClassNames.get(userId));
            int total = userTasks.size();
            int todo = 0;
            int learning = 0;
            int completed = 0;
            BigDecimal sumPercent = BigDecimal.ZERO;
            BigDecimal sumTotal = BigDecimal.ZERO;
            BigDecimal sumEarned = BigDecimal.ZERO;
            for (UnifiedTask task : userTasks) {
                if (Objects.equals(task.getStatus(), LearnerTaskStatusEnum.COMPLETED.getStatus())) {
                    completed++;
                } else if (Objects.equals(task.getStatus(), LearnerTaskStatusEnum.LEARNING.getStatus())) {
                    learning++;
                } else {
                    todo++;
                }
                sumPercent = sumPercent.add(nvl(task.getProgressPercent()));
                sumTotal = sumTotal.add(nvl(task.getCreditTotal()));
                sumEarned = sumEarned.add(nvl(task.getCreditEarned()));
            }
            vo.setTotalTasks(total);
            vo.setTodoCount(todo);
            vo.setLearningCount(learning);
            vo.setCompletedCount(completed);
            vo.setProgressPercent(total == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : sumPercent.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
            vo.setCreditTotal(sumTotal.setScale(2, RoundingMode.HALF_UP));
            vo.setCreditEarned(sumEarned.setScale(2, RoundingMode.HALF_UP));
            result.add(vo);
        }
        result.sort(Comparator.comparing(LearnerProgressRespVO::getUserId));
        return result;
    }

    @Override
    public PageResult<LearnerTaskRespVO> getTaskPage(LearningTaskPageReqVO reqVO) {
        // 培训班任务（来自培训管理，学员入班时按「学员 × 绑定课程」生成）
        if (SOURCE_TYPE_CLASS.equals(reqVO.getSourceType())) {
            return getClassTaskPage(reqVO);
        }
        LambdaQueryWrapperX<LearnerTaskDO> wrapper = new LambdaQueryWrapperX<LearnerTaskDO>()
                .eqIfPresent(LearnerTaskDO::getUserId, reqVO.getUserId())
                .eqIfPresent(LearnerTaskDO::getCourseId, reqVO.getCourseId())
                .eqIfPresent(LearnerTaskDO::getStatus, reqVO.getStatus())
                .eqIfPresent(LearnerTaskDO::getSourceType, reqVO.getSourceType());
        if (StringUtils.hasText(reqVO.getCourseTitle())) {
            List<Long> courseIds = resolveCourseIdsByTitle(reqVO.getCourseTitle());
            if (CollectionUtils.isEmpty(courseIds)) {
                return new PageResult<>(Collections.emptyList(), 0L);
            }
            wrapper.in(LearnerTaskDO::getCourseId, courseIds);
        }
        if (StringUtils.hasText(reqVO.getUserName())) {
            List<Long> userIds = resolveUserIdsBySnapshotName(reqVO.getUserName());
            if (CollectionUtils.isEmpty(userIds)) {
                return new PageResult<>(Collections.emptyList(), 0L);
            }
            wrapper.in(LearnerTaskDO::getUserId, userIds);
        }
        wrapper.orderByDesc(LearnerTaskDO::getId);
        PageResult<LearnerTaskDO> taskPage = learnerTaskMapper.selectPage(reqVO, wrapper);

        // 学员姓名 / 部门：培训班入班快照优先，其次系统用户昵称
        List<Long> taskUserIds = taskPage.getList().stream().map(LearnerTaskDO::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ClassStudentDO> snapshots = fetchStudentSnapshots(taskUserIds);
        Map<Long, String> nicknames = fetchNicknames(taskUserIds);

        Map<Long, CourseDO> courseCache = new HashMap<>();
        List<LearnerTaskRespVO> result = new ArrayList<>();
        for (LearnerTaskDO task : taskPage.getList()) {
            CourseDO course = courseCache.computeIfAbsent(task.getCourseId(),
                    id -> courseService.getCourse(id));
            LearnerTaskRespVO respVO = BeanUtils.toBean(task, LearnerTaskRespVO.class);
            ClassStudentDO snapshot = snapshots.get(task.getUserId());
            respVO.setUserName(snapshot != null && StringUtils.hasText(snapshot.getUserName())
                    ? snapshot.getUserName() : nicknames.get(task.getUserId()));
            respVO.setDeptName(snapshot == null ? null : snapshot.getDeptName());
            LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(task.getUserId(),
                    task.getCourseId(), task);
            if (course != null) {
                respVO.setCourseTitle(course.getTitle());
                respVO.setCoverUrl(course.getCoverUrl());
                respVO.setLecturer(course.getLecturer());
                respVO.setCourseCredit(course.getCredit());
                respVO.setCourseMediaType(course.getMediaType());
                respVO.setCourseMediaCount(course.getMediaCount());
                respVO.setCourseDurationMinutes(course.getDurationMinutes());
            }
            if (progress != null) {
                int status = task.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : task.getStatus();
                if (progress.getAllCompleted()) {
                    status = LearnerTaskStatusEnum.COMPLETED.getStatus();
                } else if (status != LearnerTaskStatusEnum.COMPLETED.getStatus()
                        && progress.getRatioAvg() != null && progress.getRatioAvg() > 0D) {
                    status = LearnerTaskStatusEnum.LEARNING.getStatus();
                }
                respVO.setStatus(status);
                respVO.setProgressPercent(percent(progress.getRatioAvg()));
                respVO.setCreditTotal(progress.getCreditTotal());
                respVO.setCreditEarned(progress.getCreditEarned());
                if (status == LearnerTaskStatusEnum.COMPLETED.getStatus() && respVO.getCompletedAt() == null) {
                    respVO.setCompletedAt(LocalDateTime.now());
                }
            }
            result.add(respVO);
        }
        return new PageResult<>(result, taskPage.getTotal());
    }

    /**
     * 培训班任务分页：培训管理-培训班学员的「学员 × 课程」任务，映射为学习任务视图展示
     */
    private PageResult<LearnerTaskRespVO> getClassTaskPage(LearningTaskPageReqVO reqVO) {
        LambdaQueryWrapperX<StudentTaskDO> wrapper = new LambdaQueryWrapperX<StudentTaskDO>()
                .eqIfPresent(StudentTaskDO::getUserId, reqVO.getUserId())
                .eqIfPresent(StudentTaskDO::getCourseId, reqVO.getCourseId())
                .eqIfPresent(StudentTaskDO::getStatus, reqVO.getStatus());
        if (StringUtils.hasText(reqVO.getCourseTitle())) {
            List<Long> courseIds = resolveCourseIdsByTitle(reqVO.getCourseTitle());
            if (CollectionUtils.isEmpty(courseIds)) {
                return new PageResult<>(Collections.emptyList(), 0L);
            }
            wrapper.in(StudentTaskDO::getCourseId, courseIds);
        }
        if (StringUtils.hasText(reqVO.getUserName())) {
            List<Long> userIds = resolveUserIdsBySnapshotName(reqVO.getUserName());
            if (CollectionUtils.isEmpty(userIds)) {
                return new PageResult<>(Collections.emptyList(), 0L);
            }
            wrapper.in(StudentTaskDO::getUserId, userIds);
        }
        wrapper.orderByDesc(StudentTaskDO::getId);
        PageResult<StudentTaskDO> taskPage = studentTaskMapper.selectPage(reqVO, wrapper);

        List<Long> taskUserIds = taskPage.getList().stream().map(StudentTaskDO::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, ClassStudentDO> snapshots = fetchStudentSnapshots(taskUserIds);
        Map<Long, String> nicknames = fetchNicknames(taskUserIds);

        Map<Long, CourseDO> courseCache = new HashMap<>();
        List<LearnerTaskRespVO> result = new ArrayList<>();
        for (StudentTaskDO task : taskPage.getList()) {
            CourseDO course = courseCache.computeIfAbsent(task.getCourseId(),
                    id -> courseService.getCourse(id));
            LearnerTaskRespVO respVO = new LearnerTaskRespVO();
            respVO.setUserId(task.getUserId());
            respVO.setCourseId(task.getCourseId());
            respVO.setStatus(task.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : task.getStatus());
            respVO.setSourceType(SOURCE_TYPE_CLASS);
            respVO.setProgressPercent(classTaskPercent(task));
            BigDecimal creditTotal = course == null || course.getCredit() == null
                    ? BigDecimal.ZERO : course.getCredit();
            respVO.setCreditTotal(creditTotal);
            respVO.setCreditEarned(creditTotal.multiply(respVO.getProgressPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            respVO.setCompletedAt(task.getFinishTime());
            respVO.setCreateTime(task.getCreateTime());
            respVO.setCourseTitle(task.getCourseTitle());
            if (course != null) {
                respVO.setCourseTitle(course.getTitle());
                respVO.setCoverUrl(course.getCoverUrl());
                respVO.setLecturer(course.getLecturer());
                respVO.setCourseCredit(course.getCredit());
                respVO.setCourseMediaType(course.getMediaType());
                respVO.setCourseMediaCount(course.getMediaCount());
                respVO.setCourseDurationMinutes(course.getDurationMinutes());
            }
            ClassStudentDO snapshot = snapshots.get(task.getUserId());
            respVO.setUserName(snapshot != null && StringUtils.hasText(snapshot.getUserName())
                    ? snapshot.getUserName() : nicknames.get(task.getUserId()));
            respVO.setDeptName(snapshot == null ? null : snapshot.getDeptName());
            result.add(respVO);
        }
        return new PageResult<>(result, taskPage.getTotal());
    }

    @Override
    public List<LearningStatRespVO> getLearnStats(Long classId) {
        List<UnifiedTask> tasks = buildUnifiedTasks(classId);
        if (CollectionUtils.isEmpty(tasks)) {
            return Collections.emptyList();
        }
        Map<Long, List<UnifiedTask>> byCourse = tasks.stream()
                .collect(Collectors.groupingBy(UnifiedTask::getCourseId));
        List<LearningStatRespVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<UnifiedTask>> entry : byCourse.entrySet()) {
            CourseDO course = courseService.getCourse(entry.getKey());
            LearningStatRespVO stat = new LearningStatRespVO();
            stat.setCourseId(entry.getKey());
            stat.setCourseTitle(course == null ? null : course.getTitle());
            stat.setCoverUrl(course == null ? null : course.getCoverUrl());
            int total = entry.getValue().size();
            int todoCount = 0;
            int learningCount = 0;
            int completedCount = 0;
            BigDecimal sumCreditEarned = BigDecimal.ZERO;
            BigDecimal sumCreditTotal = BigDecimal.ZERO;
            BigDecimal sumProgress = BigDecimal.ZERO;
            for (UnifiedTask task : entry.getValue()) {
                if (Objects.equals(task.getStatus(), LearnerTaskStatusEnum.COMPLETED.getStatus())) {
                    completedCount++;
                } else if (Objects.equals(task.getStatus(), LearnerTaskStatusEnum.LEARNING.getStatus())) {
                    learningCount++;
                } else {
                    todoCount++;
                }
                sumCreditEarned = sumCreditEarned.add(nvl(task.getCreditEarned()));
                sumCreditTotal = sumCreditTotal.add(nvl(task.getCreditTotal()));
                sumProgress = sumProgress.add(nvl(task.getProgressPercent()));
            }
            stat.setTaskTotal(total);
            stat.setTodoCount(todoCount);
            stat.setLearningCount(learningCount);
            stat.setCompletedCount(completedCount);
            stat.setCreditTotal(sumCreditTotal.setScale(2, RoundingMode.HALF_UP));
            stat.setCreditEarned(sumCreditEarned.setScale(2, RoundingMode.HALF_UP));
            stat.setAvgProgressPercent(total == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : sumProgress.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
            stat.setCompletedRate(total == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(completedCount * 100D / total).setScale(2, RoundingMode.HALF_UP));
            result.add(stat);
        }
        result.sort(Comparator.comparing(LearningStatRespVO::getCourseId));
        return result;
    }

    // ========== 学习中心 ←→ 培训管理 数据打通 ==========

    /**
     * 合并「培训班学习任务 training_student_task」与「学习任务 training_learner_task」，
     * 按 学员 + 课程 去重（学习任务的进度更精细，同键优先保留），用于学习统计与学员进度聚合。
     *
     * @param classId 培训班 ID；为空时统计全部在班学员
     */
    private List<UnifiedTask> buildUnifiedTasks(Long classId) {
        // 1. 学员范围：培训班在班学员（classId 为空则取全部在班学员）
        List<ClassStudentDO> students = classId == null
                ? classStudentMapper.selectList(new LambdaQueryWrapperX<ClassStudentDO>()
                .eq(ClassStudentDO::getStatus, 0))
                : classStudentMapper.selectListByClassId(classId, 0);
        Set<Long> scopeUserIds = students.stream().map(ClassStudentDO::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (classId != null && scopeUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, UnifiedTask> merged = new LinkedHashMap<>();
        // 2. 培训班学习任务（班级学习进度看板的任务来源）
        List<StudentTaskDO> classTasks = classId == null
                ? studentTaskMapper.selectList(new LambdaQueryWrapperX<StudentTaskDO>()
                .orderByAsc(StudentTaskDO::getCourseId))
                : studentTaskMapper.selectListByClassId(classId);
        for (StudentTaskDO task : classTasks) {
            if (task.getUserId() == null || task.getCourseId() == null) {
                continue;
            }
            if (classId == null && !scopeUserIds.contains(task.getUserId())) {
                continue; // 已移出培训班的学员任务不计入
            }
            CourseDO course = courseService.getCourse(task.getCourseId());
            UnifiedTask vo = new UnifiedTask();
            vo.setUserId(task.getUserId());
            vo.setCourseId(task.getCourseId());
            vo.setStatus(task.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : task.getStatus());
            vo.setProgressPercent(classTaskPercent(task));
            BigDecimal creditTotal = course == null || course.getCredit() == null
                    ? BigDecimal.ZERO : course.getCredit();
            vo.setCreditTotal(creditTotal);
            vo.setCreditEarned(creditTotal.multiply(vo.getProgressPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            merged.put(taskKey(task.getUserId(), task.getCourseId()), vo);
        }
        // 3. 学习任务（进度由学习轨迹实时计算，同键覆盖培训班任务）
        List<LearnerTaskDO> learnerTasks = learnerTaskMapper.selectList(
                new LambdaQueryWrapperX<LearnerTaskDO>().orderByAsc(LearnerTaskDO::getCourseId));
        for (LearnerTaskDO task : learnerTasks) {
            if (task.getUserId() == null || task.getCourseId() == null) {
                continue;
            }
            if (classId != null && !scopeUserIds.contains(task.getUserId())) {
                continue;
            }
            UnifiedTask vo = new UnifiedTask();
            vo.setUserId(task.getUserId());
            vo.setCourseId(task.getCourseId());
            int status = task.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : task.getStatus();
            LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(task.getUserId(),
                    task.getCourseId(), task);
            if (progress != null) {
                if (progress.getAllCompleted()) {
                    status = LearnerTaskStatusEnum.COMPLETED.getStatus();
                } else if (status != LearnerTaskStatusEnum.COMPLETED.getStatus()
                        && progress.getRatioAvg() != null && progress.getRatioAvg() > 0D) {
                    status = LearnerTaskStatusEnum.LEARNING.getStatus();
                }
                vo.setProgressPercent(percent(progress.getRatioAvg()));
                vo.setCreditTotal(progress.getCreditTotal());
                vo.setCreditEarned(progress.getCreditEarned());
            } else {
                vo.setProgressPercent(percentOfStatus(status));
                vo.setCreditTotal(task.getCreditTotal());
                vo.setCreditEarned(task.getCreditEarned());
            }
            vo.setStatus(status);
            merged.put(taskKey(task.getUserId(), task.getCourseId()), vo);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 培训班任务的进度折算：已完成 100%；课程进行中 / 学时完成 / 任务进行中 50%；其余 0%
     */
    private static BigDecimal classTaskPercent(StudentTaskDO task) {
        if (Objects.equals(task.getStatus(), LearnerTaskStatusEnum.COMPLETED.getStatus())
                || Objects.equals(task.getCourseStatus(), 2)) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        if (Objects.equals(task.getCourseStatus(), 1) || Objects.equals(task.getStudyStatus(), 1)
                || Objects.equals(task.getStatus(), LearnerTaskStatusEnum.LEARNING.getStatus())) {
            return BigDecimal.valueOf(50).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentOfStatus(Integer status) {
        if (Objects.equals(status, LearnerTaskStatusEnum.COMPLETED.getStatus())) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        if (Objects.equals(status, LearnerTaskStatusEnum.LEARNING.getStatus())) {
            return BigDecimal.valueOf(50).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 按学员姓名（培训班入班快照，模糊）解析学员 ID 集合，供学习任务列表按学员筛选
     */
    private List<Long> resolveUserIdsBySnapshotName(String userName) {
        List<ClassStudentDO> students = classStudentMapper.selectList(new LambdaQueryWrapperX<ClassStudentDO>()
                .like(ClassStudentDO::getUserName, userName)
                .eq(ClassStudentDO::getStatus, 0));
        Set<Long> userIds = new LinkedHashSet<>();
        for (ClassStudentDO student : students) {
            if (student.getUserId() != null) {
                userIds.add(student.getUserId());
            }
        }
        return new ArrayList<>(userIds);
    }

    /**
     * 批量获取培训班入班快照（姓名 / 部门），用于任务列表展示
     */
    private Map<Long, ClassStudentDO> fetchStudentSnapshots(List<Long> userIds) {
        Map<Long, ClassStudentDO> map = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return map;
        }
        List<ClassStudentDO> students = classStudentMapper.selectList(new LambdaQueryWrapperX<ClassStudentDO>()
                .in(ClassStudentDO::getUserId, userIds)
                .eq(ClassStudentDO::getStatus, 0));
        for (ClassStudentDO student : students) {
            if (student.getUserId() != null) {
                map.putIfAbsent(student.getUserId(), student);
            }
        }
        return map;
    }

    private static String taskKey(Long userId, Long courseId) {
        return userId + "-" + courseId;
    }

    /**
     * 合并后的统一任务视图（培训班学习任务 + 学习任务）
     */
    @lombok.Data
    private static class UnifiedTask {

        private Long userId;
        private Long courseId;
        /**
         * 任务状态：0 待学习 1 学习中 2 已完成
         */
        private Integer status;
        /**
         * 学习进度（%）
         */
        private BigDecimal progressPercent;
        private BigDecimal creditTotal;
        private BigDecimal creditEarned;

    }

    @Override
    public AdminCourseTrackRespVO getCourseTrack(Long userId, Long courseId) {
        CourseDO course = courseService.getCourse(courseId);
        AdminCourseTrackRespVO respVO = new AdminCourseTrackRespVO();
        respVO.setUserId(userId);
        respVO.setCourseId(courseId);
        respVO.setCourseTitle(course == null ? null : course.getTitle());
        respVO.setCreditTotal(course == null ? null : course.getCredit());
        Map<Long, String> nicknames = fetchNicknames(Collections.singletonList(userId));
        respVO.setNickname(nicknames.get(userId));

        LearnerTaskDO task = learnerTaskMapper.selectByUserIdAndCourseId(userId, courseId);
        if (task != null) {
            respVO.setTaskStatus(task.getStatus());
            LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(userId, courseId, task);
            if (progress != null) {
                respVO.setCreditTotal(progress.getCreditTotal());
                if (progress.getAllCompleted()) {
                    respVO.setTaskStatus(LearnerTaskStatusEnum.COMPLETED.getStatus());
                }
            }
        }
        List<LearnSessionDO> sessions = task == null
                ? learnSessionMapper.selectEndedListByCourseId(courseId)
                : learnSessionMapper.selectEndedListByUserAndCourse(userId, courseId);
        if (course == null) {
            return respVO;
        }
        // 汇总应仅统计本学员的会话
        List<LearnSessionDO> userSessions = new ArrayList<>(sessions.stream()
                .filter(session -> Objects.equals(session.getUserId(), userId))
                .collect(Collectors.toList()));

        Map<Long, List<LearnSessionDO>> byMedia = userSessions.stream()
                .collect(Collectors.groupingBy(LearnSessionDO::getMediaId));
        Map<Long, LearnerProgressCalculator.MediaStat> statMap = new HashMap<>();
        LearnerProgressCalculator.TaskProgress progress = task == null ? null
                : progressCalculator.calc(userId, courseId, task);
        if (progress != null && !CollectionUtils.isEmpty(progress.getMediaStats())) {
            progress.getMediaStats().forEach(stat -> statMap.put(stat.getMediaId(), stat));
        }
        int totalValidSeconds = 0;
        List<AdminCourseTrackRespVO.MediaTrack> mediaTracks = new ArrayList<>();
        List<CourseMediaDO> mediaList = courseService.getMediaList(courseId).stream()
                .sorted(Comparator.comparing(CourseMediaDO::getSort))
                .collect(Collectors.toList());
        for (CourseMediaDO media : mediaList) {
            if (media.getMediaType() == null || (!media.getMediaType().equals(CourseMediaTypeEnum.VIDEO.getType())
                    && !media.getMediaType().equals(CourseMediaTypeEnum.DOCUMENT.getType()))) {
                continue;
            }
            AdminCourseTrackRespVO.MediaTrack track = new AdminCourseTrackRespVO.MediaTrack();
            track.setMediaId(media.getId());
            track.setMediaType(media.getMediaType().toString());
            CourseMediaTypeEnum typeEnum = CourseMediaTypeEnum.getByType(media.getMediaType());
            track.setMediaTypeName(typeEnum == null ? "未知" : typeEnum.getName());
            track.setName(media.getName());
            List<LearnSessionDO> mediaSessions = byMedia.getOrDefault(media.getId(), Collections.emptyList());
            track.setSessionCount(mediaSessions.size());
            int mediaValid = mediaSessions.stream().mapToInt(s -> nvlInt(s.getValidSeconds())).sum();
            totalValidSeconds += mediaValid;
            track.setTotalValidSeconds(mediaValid);
            LearnerProgressCalculator.MediaStat stat = statMap.get(media.getId());
            BigDecimal creditEarned = stat == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : nvl(stat.getCreditEarned());
            track.setCreditEarned(creditEarned);
            track.setCompleted(stat != null && stat.getCompleted());
            track.setRatioPercent(stat == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : percent(stat.getRatio()));
            // 会话明细：按会话有效时长均摊媒体学时
            List<AdminCourseTrackRespVO.SessionTrack> sessionTracks = new ArrayList<>();
            if (!mediaSessions.isEmpty() && mediaValid > 0) {
                for (LearnSessionDO session : mediaSessions) {
                    AdminCourseTrackRespVO.SessionTrack sessionTrack = new AdminCourseTrackRespVO.SessionTrack();
                    sessionTrack.setSessionId(session.getId());
                    sessionTrack.setStartAt(session.getStartAt());
                    sessionTrack.setEndAt(session.getEndAt());
                    sessionTrack.setValidSeconds(session.getValidSeconds());
                    sessionTrack.setEndReason(session.getEndReason());
                    sessionTrack.setCreditEarned(creditEarned.multiply(BigDecimal.valueOf(
                                    nvlInt(session.getValidSeconds())))
                            .divide(BigDecimal.valueOf(mediaValid), 2, RoundingMode.HALF_UP));
                    sessionTracks.add(sessionTrack);
                }
            }
            track.setSessions(sessionTracks);
            mediaTracks.add(track);
        }
        respVO.setTotalValidSeconds(totalValidSeconds);
        BigDecimal totalCreditEarned = mediaTracks.stream()
                .map(AdminCourseTrackRespVO.MediaTrack::getCreditEarned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        respVO.setCreditEarned(totalCreditEarned.setScale(2, RoundingMode.HALF_UP));
        respVO.setMediaTracks(mediaTracks);
        return respVO;
    }

    // ========== 私有方法 ==========

    private List<Long> resolveCourseIdsByTitle(String title) {
        CoursePageReqVO pageReqVO = new CoursePageReqVO();
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        pageReqVO.setTitle(title);
        PageResult<CourseDO> page = courseService.getCoursePage(pageReqVO);
        if (page == null || CollectionUtils.isEmpty(page.getList())) {
            return Collections.emptyList();
        }
        return page.getList().stream().map(CourseDO::getId).collect(Collectors.toList());
    }

    private Map<Long, String> fetchNicknames(List<Long> userIds) {
        Map<Long, String> nicknames = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return nicknames;
        }
        List<Long> distinct = userIds.stream().distinct().collect(Collectors.toList());
        try {
            CommonResult<List<AdminUserRespDTO>> result = adminUserApi.getUserList(distinct);
            if (result != null && result.isSuccess() && !CollectionUtils.isEmpty(result.getData())) {
                for (AdminUserRespDTO user : result.getData()) {
                    if (user != null) {
                        nicknames.put(user.getId(), user.getNickname());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[fetchNicknames][获取用户昵称失败 ids({})，忽略]", userIds, e);
        }
        return nicknames;
    }

    private static BigDecimal percent(Double ratio) {
        if (ratio == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nvlInt(Integer value) {
        return value == null ? 0 : value;
    }

}
