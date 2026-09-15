package cn.iocoder.txgy.module.training.service.plan;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.training.controller.admin.plan.vo.*;
import cn.iocoder.txgy.module.training.dal.dataobject.plan.*;
import cn.iocoder.txgy.module.training.dal.mysql.plan.*;
import cn.iocoder.txgy.module.training.enums.plan.ClassStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.*;

/**
 * 培训班学员 Service 实现类（四定-定人 / 班级学习进度看板 / 进度登记）
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class ClassStudentServiceImpl {

    @Resource
    private ClassServiceImpl classService;
    @Resource
    private ClassMapper classMapper;
    @Resource
    private ClassCourseMapper classCourseMapper;
    @Resource
    private ClassStudentMapper classStudentMapper;
    @Resource
    private StudentTaskMapper studentTaskMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    /**
     * 单个培训班在班学员上限（手动添加 / Excel 导入 / 按参与对象筛选 / 导入指定班级统一受限）
     */
    public static final Integer MAX_STUDENT_SIZE = 50;

    /**
     * 学员加入来源：1 手动添加 2 Excel 批量导入 3 按参与对象筛选 5 导入并指定班级
     */
    private static final Integer SOURCE_MANUAL = 1;
    private static final Integer SOURCE_IMPORT = 2;
    private static final Integer SOURCE_IMPORT_ALLOCATE = 5;

    // ========== 学员管理 ==========

    /**
     * 批量添加学员，并自动为每个学员生成「课程 × 学员」学习任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer addStudents(ClassStudentAddReqVO reqVO) {
        // 1. 校验培训班可管理学员
        ClassDO classDO = classService.exists(reqVO.getClassId());
        if (!classService.isEditableForStudent(classDO)) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_STUDENT, getStatusName(classDO.getStatus()));
        }
        // 2. 校验已绑定课程（学员入班需生成学习任务）
        List<ClassCourseDO> courses = classCourseMapper.selectListByClassId(reqVO.getClassId());
        if (CollUtil.isEmpty(courses)) {
            throw exception(PLAN_STUDENT_COURSE_REQUIRED);
        }
        // 3. 查询用户昵称 / 部门快照（容错：RPC 失败则降级为空）
        Map<Long, AdminUserRespDTO> userMap = fetchUserMap(reqVO.getUserIds());
        Map<Long, String> deptNameMap = fetchDeptNameMap(userMap.values());
        List<ClassStudentDO> all = classStudentMapper.selectListByClassId(reqVO.getClassId(), null);
        Map<Long, ClassStudentDO> userIndex = all.stream()
                .collect(Collectors.toMap(ClassStudentDO::getUserId, item -> item, (a, b) -> a));
        // 在班学员上限校验：超出上限则整体失败回滚，避免班级规模失控（恢复在班的学员同样占用名额）
        int inClassCount = (int) userIndex.values().stream()
                .filter(item -> Objects.equals(item.getStatus(), 0)).count();
        int added = 0;
        for (Long userId : reqVO.getUserIds()) {
            AdminUserRespDTO user = userMap.get(userId);
            ClassStudentDO exist = userIndex.get(userId);
            if (exist != null && Objects.equals(exist.getStatus(), 0)) {
                continue; // 已在班，幂等跳过
            }
            if (inClassCount + added >= MAX_STUDENT_SIZE) {
                throw exception(PLAN_STUDENT_LIMIT_EXCEEDED, MAX_STUDENT_SIZE, inClassCount,
                        MAX_STUDENT_SIZE - inClassCount);
            }
            if (exist != null && Objects.equals(exist.getStatus(), 1)) {
                // 曾被移除 → 恢复在班，并重新生成学习任务；同步刷新姓名/部门/来源快照
                ClassStudentDO restore = new ClassStudentDO();
                restore.setId(exist.getId());
                restore.setStatus(0);
                restore.setUserName(Optional.ofNullable(user).map(AdminUserRespDTO::getNickname).orElse(null));
                restore.setDeptName(fetchDeptName(user, deptNameMap));
                restore.setSource(reqVO.getSource() == null ? SOURCE_MANUAL : reqVO.getSource());
                restore.setJoinTime(LocalDateTime.now());
                restore.setRemovedTime(null);
                restore.setRemovedReason(null);
                classStudentMapper.updateById(restore);
            } else {
                ClassStudentDO student = new ClassStudentDO();
                student.setClassId(reqVO.getClassId());
                student.setUserId(userId);
                student.setUserName(Optional.ofNullable(user).map(AdminUserRespDTO::getNickname).orElse(null));
                student.setDeptName(fetchDeptName(user, deptNameMap));
                student.setSource(reqVO.getSource() == null ? SOURCE_MANUAL : reqVO.getSource());
                student.setStatus(0);
                student.setJoinTime(LocalDateTime.now());
                classStudentMapper.insert(student);
            }
            // 4. 自动生成学习任务（学员 × 绑定课程）
            createTasks(reqVO.getClassId(), userId, courses);
            added++;
        }
        // 5. 刷新在班学员数冗余
        refreshStudentCount(reqVO.getClassId());
        return added;
    }

    /**
     * 移出学员：保留在班历史（status=1），逻辑删除其未完成任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long classId, Long userId, String reason) {
        ClassDO classDO = classService.exists(classId);
        if (!classService.isEditableForStudent(classDO)) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_STUDENT, getStatusName(classDO.getStatus()));
        }
        ClassStudentDO student = classStudentMapper.selectByClassIdAndUserId(classId, userId);
        if (student == null || !Objects.equals(student.getStatus(), 0)) {
            throw exception(PLAN_STUDENT_NOT_EXISTS);
        }
        ClassStudentDO update = new ClassStudentDO();
        update.setId(student.getId());
        update.setStatus(1);
        update.setRemovedTime(LocalDateTime.now());
        update.setRemovedReason(StrUtil.isBlank(reason) ? "管理员移除" : reason);
        classStudentMapper.updateById(update);
        // 任务随之作废（逻辑删除），学员重新入班会再生成
        studentTaskMapper.deleteByClassIdAndUserIds(classId, Collections.singletonList(userId));
        refreshStudentCount(classId);
    }

    /**
     * Excel 批量导入学员（文档 3-2）：按【手机号】匹配系统账号，复用批量添加学员逻辑（自动生成学习任务）
     *
     * @return 成功新增数 + 失败行明细
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassStudentImportRespVO importStudents(Long classId, List<ClassStudentImportExcelVO> rows) {
        // 1. 校验培训班可管理学员、已绑定课程（与手动添加保持一致）
        ClassDO classDO = classService.exists(classId);
        if (!classService.isEditableForStudent(classDO)) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_STUDENT, getStatusName(classDO.getStatus()));
        }
        if (CollUtil.isEmpty(classCourseMapper.selectListByClassId(classId))) {
            throw exception(PLAN_STUDENT_COURSE_REQUIRED);
        }
        // 2. 逐行匹配手机号 → 系统账号（演示规模单行 RPC 足够，失败行仅提示不中断）
        List<ClassStudentImportRespVO.FailRow> failRows = new ArrayList<>();
        Set<Long> userIdSet = new LinkedHashSet<>();
        int rowNumber = 1; // 第 1 行为表头，数据从第 2 行起
        for (ClassStudentImportExcelVO row : rows) {
            rowNumber++;
            String mobile = StrUtil.trimToNull(row.getMobile());
            if (mobile == null) {
                failRows.add(new ClassStudentImportRespVO.FailRow(rowNumber, null, "手机号为空"));
                continue;
            }
            AdminUserRespDTO user;
            try {
                user = adminUserApi.getUserByMobile(mobile).getCheckedData();
            } catch (Throwable ex) {
                log.warn("[importStudents][按手机号查询用户失败，忽略该行] mobile={}", mobile, ex);
                user = null;
            }
            if (user == null || user.getId() == null) {
                failRows.add(new ClassStudentImportRespVO.FailRow(rowNumber, mobile, "未找到对应系统账号"));
                continue;
            }
            userIdSet.add(user.getId());
        }
        // 3. 复用批量添加学员逻辑（自动生成学习任务、已在班幂等跳过），来源标记为「Excel 批量导入」
        int added = 0;
        if (CollUtil.isNotEmpty(userIdSet)) {
            ClassStudentAddReqVO reqVO = new ClassStudentAddReqVO();
            reqVO.setClassId(classId);
            reqVO.setUserIds(new ArrayList<>(userIdSet));
            reqVO.setSource(SOURCE_IMPORT);
            added = addStudents(reqVO);
        }
        ClassStudentImportRespVO respVO = new ClassStudentImportRespVO();
        respVO.setSuccessCount(added);
        respVO.setFailRows(failRows);
        return respVO;
    }

    /**
     * 解析 Excel 学员名单（按【手机号】匹配系统账号，不落库），供人工为每位学员指定要加入的培训班
     *
     * @param rows Excel 数据行（姓名 / 手机号）
     * @return 匹配成功的学员名单 + 失败行明细
     */
    public ClassStudentImportParseRespVO parseImportRows(List<ClassStudentImportExcelVO> rows) {
        List<ClassStudentImportRespVO.FailRow> failRows = new ArrayList<>();
        List<ClassStudentImportParseRespVO.ImportUser> parsedUsers = new ArrayList<>();
        Map<Long, AdminUserRespDTO> userMap = new LinkedHashMap<>();
        int rowNumber = 1; // 第 1 行为表头，数据从第 2 行起
        for (ClassStudentImportExcelVO row : rows) {
            rowNumber++;
            String mobile = StrUtil.trimToNull(row.getMobile());
            if (mobile == null) {
                failRows.add(new ClassStudentImportRespVO.FailRow(rowNumber, null, "手机号为空"));
                continue;
            }
            AdminUserRespDTO user;
            try {
                user = adminUserApi.getUserByMobile(mobile).getCheckedData();
            } catch (Throwable ex) {
                log.warn("[parseImportRows][按手机号查询用户失败，忽略该行] mobile={}", mobile, ex);
                user = null;
            }
            if (user == null || user.getId() == null) {
                failRows.add(new ClassStudentImportRespVO.FailRow(rowNumber, mobile, "未找到对应系统账号"));
                continue;
            }
            ClassStudentImportParseRespVO.ImportUser item = new ClassStudentImportParseRespVO.ImportUser();
            item.setRowNumber(rowNumber);
            item.setUserId(user.getId());
            item.setMobile(mobile);
            parsedUsers.add(item);
            userMap.putIfAbsent(user.getId(), user);
        }
        // 补齐姓名 / 部门快照（RPC 失败降级为空，不影响名单展示）
        Map<Long, String> deptNameMap = fetchDeptNameMap(userMap.values());
        for (ClassStudentImportParseRespVO.ImportUser item : parsedUsers) {
            AdminUserRespDTO user = userMap.get(item.getUserId());
            item.setUserName(user == null ? null : user.getNickname());
            item.setDeptName(fetchDeptName(user, deptNameMap));
        }
        ClassStudentImportParseRespVO respVO = new ClassStudentImportParseRespVO();
        respVO.setMatchedCount(parsedUsers.size());
        respVO.setUsers(parsedUsers);
        respVO.setFailRows(failRows);
        return respVO;
    }

    /**
     * 按人工指定的「学员 → 培训班」明细批量入班（导入名单后手动选班入班）
     *
     * <p>入班规则：
     * <ol>
     *   <li>仅处理已指定培训班的学员；未指定培训班的学员不入班（前端不提交即可）；</li>
     *   <li>同一学员在同一培训班仅入班一次（明细重复时自动去重）；</li>
     *   <li>目标培训班需存在、可维护学员（草稿 / 进行中）且已绑定课程，否则整体失败回滚；</li>
     *   <li>单个培训班在班学员上限 {@link #MAX_STUDENT_SIZE} 人，超出时整体失败回滚并提示剩余名额。</li>
     * </ol>
     *
     * @param reqVO 入班明细
     * @return 各班入班结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ClassStudentImportAllocateRespVO importAndAllocate(ClassStudentImportAllocateReqVO reqVO) {
        // 1. 按培训班聚合明细（同一学员在同一培训班去重，保持提交顺序；跨培训班不去重）
        Map<Long, LinkedHashSet<Long>> classUserMap = new LinkedHashMap<>();
        for (ClassStudentImportAllocateReqVO.Item item : reqVO.getItems()) {
            if (item == null || item.getUserId() == null || item.getClassId() == null) {
                continue;
            }
            classUserMap.computeIfAbsent(item.getClassId(), key -> new LinkedHashSet<>()).add(item.getUserId());
        }
        if (classUserMap.isEmpty()) {
            throw exception(PLAN_STUDENT_IMPORT_ALLOC_EMPTY);
        }
        // 2. 批量获取用户姓名 / 部门快照（用于入班结果展示）
        List<Long> allUserIds = classUserMap.values().stream()
                .flatMap(Collection::stream).distinct().collect(Collectors.toList());
        Map<Long, AdminUserRespDTO> userMap = fetchUserMap(allUserIds);
        Map<Long, String> deptNameMap = fetchDeptNameMap(userMap.values());
        // 3. 逐班校验（状态 / 课程 / 名额）并落库
        List<ClassStudentImportAllocateRespVO.ClassResult> classResults = new ArrayList<>();
        int successCount = 0;
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : classUserMap.entrySet()) {
            Long classId = entry.getKey();
            List<Long> userIds = new ArrayList<>(entry.getValue());
            ClassDO classDO = classService.exists(classId);
            if (!classService.isEditableForStudent(classDO)) {
                throw exception(PLAN_STUDENT_IMPORT_ALLOC_CLASS_NOT_ALLOWED, classDO.getName(),
                        getStatusName(classDO.getStatus()));
            }
            if (CollUtil.isEmpty(classCourseMapper.selectListByClassId(classId))) {
                throw exception(PLAN_STUDENT_COURSE_REQUIRED);
            }
            Set<Long> inClassUserIds = classStudentMapper.selectListByClassId(classId, 0).stream()
                    .map(ClassStudentDO::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
            int beforeCount = inClassUserIds.size();
            long addableCount = userIds.stream().filter(userId -> !inClassUserIds.contains(userId)).count();
            if (beforeCount + addableCount > MAX_STUDENT_SIZE) {
                throw exception(PLAN_STUDENT_IMPORT_ALLOC_LIMIT_EXCEEDED, classDO.getName(), MAX_STUDENT_SIZE,
                        beforeCount, Math.max(0, MAX_STUDENT_SIZE - beforeCount));
            }
            // 4. 复用批量添加学员逻辑（自动生成学习任务、已在班幂等跳过），来源标记为「导入并指定班级」
            ClassStudentAddReqVO addReqVO = new ClassStudentAddReqVO();
            addReqVO.setClassId(classId);
            addReqVO.setUserIds(userIds);
            addReqVO.setSource(SOURCE_IMPORT_ALLOCATE);
            int added = addStudents(addReqVO);
            successCount += added;
            ClassStudentImportAllocateRespVO.ClassResult classResult = new ClassStudentImportAllocateRespVO.ClassResult();
            classResult.setClassId(classId);
            classResult.setClassName(classDO.getName());
            classResult.setBeforeCount(beforeCount);
            classResult.setAddedCount(added);
            classResult.setAfterCount(beforeCount + added);
            classResult.setRemaining(Math.max(0, MAX_STUDENT_SIZE - beforeCount - added));
            classResult.setUsers(userIds.stream()
                    .map(userId -> buildAllocateUser(userId, userMap, deptNameMap)).collect(Collectors.toList()));
            classResults.add(classResult);
        }
        ClassStudentImportAllocateRespVO respVO = new ClassStudentImportAllocateRespVO();
        respVO.setSuccessCount(successCount);
        respVO.setMaxSize(MAX_STUDENT_SIZE);
        respVO.setClassResults(classResults);
        return respVO;
    }

    public PageResult<ClassStudentRespVO> getStudentPage(ClassStudentPageReqVO pageReqVO) {
        classService.exists(pageReqVO.getClassId());
        PageResult<ClassStudentDO> pageResult = classStudentMapper.selectPage(pageReqVO);
        return cn.iocoder.txgy.framework.common.util.object.BeanUtils.toBean(pageResult, ClassStudentRespVO.class);
    }

    public List<ClassStudentDO> listActiveStudents(Long classId) {
        return classStudentMapper.selectListByClassId(classId, 0);
    }

    public List<StudentTaskDO> listTasks(Long classId) {
        return studentTaskMapper.selectListByClassId(classId);
    }

    /**
     * 学员在该培训班下的学习任务列表（进度登记弹窗使用）
     */
    public List<StudentTaskDO> getStudentTaskList(Long classId, Long userId) {
        classService.exists(classId);
        return studentTaskMapper.selectListByClassIdAndUserIds(classId, Collections.singletonList(userId));
    }

    // ========== 班级学习进度看板（3-3） ==========

    /**
     * 进度看板：按在班学员聚合任务维度（学时/课程/考试/证书），
     * 并支持按 未开始/进行中/已完成/考试未通过 四类维度筛选
     */
    public PageResult<ClassProgressRespVO> getProgressPage(ClassProgressPageReqVO pageReqVO) {
        classService.exists(pageReqVO.getClassId());
        List<ClassStudentDO> students = classStudentMapper.selectListByClassId(pageReqVO.getClassId(), 0);
        List<StudentTaskDO> tasks = studentTaskMapper.selectListByClassId(pageReqVO.getClassId());
        Map<Long, List<StudentTaskDO>> taskMap = tasks.stream()
                .collect(Collectors.groupingBy(StudentTaskDO::getUserId));
        // 1. 聚合成进度视图
        List<ClassProgressRespVO> all = new ArrayList<>();
        for (ClassStudentDO student : students) {
            List<StudentTaskDO> studentTasks = taskMap.getOrDefault(student.getUserId(), Collections.emptyList());
            ClassProgressRespVO respVO = buildProgress(student, studentTasks);
            // 2. 条件筛选（学员姓名 / 进度维度）
            if (StrUtil.isNotBlank(pageReqVO.getUserName())
                    && !StrUtil.contains(respVO.getUserName() == null ? "" : respVO.getUserName(), pageReqVO.getUserName())) {
                continue;
            }
            if (pageReqVO.getOverallStatus() != null
                    && !Objects.equals(pageReqVO.getOverallStatus(), respVO.getOverallStatus())) {
                continue;
            }
            all.add(respVO);
        }
        // 3. 内存分页（班级规模有限，聚合在应用层完成）
        long total = all.size();
        int pageNo = pageReqVO.getPageNo();
        int pageSize = pageReqVO.getPageSize();
        int fromIndex = Math.min((pageNo - 1) * pageSize, (int) total);
        int toIndex = Math.min(fromIndex + pageSize, (int) total);
        List<ClassProgressRespVO> pageList = fromIndex >= toIndex
                ? Collections.emptyList() : all.subList(fromIndex, toIndex);
        return new PageResult<>(pageList, total);
    }

    // ========== 学习进度登记（3-3 数据推进入口） ==========

    /**
     * 登记单条学习任务进度（教务代录；正式推进由学员端学习记录、考试模块承载，模块五）
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerProgress(ProgressRegisterReqVO reqVO) {
        StudentTaskDO task = studentTaskMapper.selectById(reqVO.getTaskId());
        if (task == null) {
            throw exception(PLAN_TASK_NOT_EXISTS);
        }
        ClassDO classDO = classService.exists(task.getClassId());
        // 草稿/已取消的培训班不允许登记进度
        boolean allowRegister = ClassStatusEnum.RUNNING.getStatus().equals(classDO.getStatus())
                || ClassStatusEnum.FINISHED.getStatus().equals(classDO.getStatus());
        if (!allowRegister) {
            throw exception(PLAN_CLASS_STATUS_NOT_ALLOW_STUDENT, getStatusName(classDO.getStatus()));
        }
        StudentTaskDO update = new StudentTaskDO();
        update.setId(task.getId());
        Integer studyStatus = task.getStudyStatus();
        if (Boolean.TRUE.equals(reqVO.getStudyFinished())) {
            update.setStudyStatus(1);
            studyStatus = 1;
        }
        Integer courseStatus = task.getCourseStatus();
        if (reqVO.getCourseStatus() != null) {
            update.setCourseStatus(reqVO.getCourseStatus());
            courseStatus = reqVO.getCourseStatus();
        }
        Integer examStatus = task.getExamStatus();
        if (reqVO.getExamStatus() != null) {
            update.setExamStatus(reqVO.getExamStatus());
            examStatus = reqVO.getExamStatus();
        }
        if (Boolean.TRUE.equals(reqVO.getCertStatus())) {
            update.setCertStatus(1);
        }
        // 重算任务总状态：课程完成 + 学时完成 + 考试未挂科 → 已完成
        if (Objects.equals(courseStatus, 2) && Objects.equals(studyStatus, 1)
                && !Objects.equals(examStatus, 2)) {
            update.setStatus(2);
            update.setFinishTime(LocalDateTime.now());
        } else if (!Objects.equals(task.getStatus(), 2)
                && (Objects.equals(courseStatus, 2) || Objects.equals(studyStatus, 1)
                || (examStatus != null && !Objects.equals(examStatus, 0)))) {
            update.setStatus(1);
        } else if (Objects.equals(examStatus, 2)) {
            update.setStatus(1);
            update.setFinishTime(null);
        }
        studentTaskMapper.updateById(update);
    }

    // ========== 私有方法 ==========

    private void createTasks(Long classId, Long userId, List<ClassCourseDO> courses) {
        for (ClassCourseDO course : courses) {
            if (studentTaskMapper.selectByClassUserCourse(classId, userId, course.getCourseId()) != null) {
                continue;
            }
            StudentTaskDO task = new StudentTaskDO();
            task.setClassId(classId);
            task.setUserId(userId);
            task.setCourseId(course.getCourseId());
            task.setCourseTitle(course.getCourseTitle());
            task.setStudyStatus(0);
            task.setCourseStatus(0);
            task.setExamStatus(0);
            task.setCertStatus(0);
            task.setStatus(0);
            studentTaskMapper.insert(task);
        }
    }

    private ClassProgressRespVO buildProgress(ClassStudentDO student, List<StudentTaskDO> tasks) {
        ClassProgressRespVO respVO = new ClassProgressRespVO();
        respVO.setUserId(student.getUserId());
        respVO.setUserName(student.getUserName());
        respVO.setDeptName(student.getDeptName());
        int total = tasks.size();
        respVO.setTotalTasks(total);
        long finished = tasks.stream().filter(item -> Objects.equals(item.getStatus(), 2)).count();
        respVO.setFinishedTasks((int) finished);
        boolean allStudy = total > 0 && tasks.stream().allMatch(item -> Objects.equals(item.getStudyStatus(), 1));
        boolean allCourse = total > 0 && tasks.stream().allMatch(item -> Objects.equals(item.getCourseStatus(), 2));
        boolean anyExamFail = tasks.stream().anyMatch(item -> Objects.equals(item.getExamStatus(), 2));
        boolean allExamPass = total > 0 && tasks.stream().allMatch(item -> Objects.equals(item.getExamStatus(), 1));
        boolean allCert = total > 0 && tasks.stream().allMatch(item -> Objects.equals(item.getCertStatus(), 1));
        respVO.setStudyFinished(allStudy);
        respVO.setCourseFinished(allCourse);
        respVO.setCertFinished(allCert);
        respVO.setExamStatus(anyExamFail ? 2 : (allExamPass && allCourse ? 1 : 0));
        // 进度维度：0 未开始 1 进行中 2 已完成 3 考试未通过
        int overall;
        if (anyExamFail) {
            overall = 3;
        } else if (finished == total && total > 0) {
            overall = 2;
        } else if (tasks.stream().anyMatch(item -> !Objects.equals(item.getStatus(), 0))) {
            overall = 1;
        } else {
            overall = 0;
        }
        respVO.setOverallStatus(overall);
        respVO.setProgressPercent(total == 0 ? 0 : (int) Math.round(finished * 100.0 / total));
        return respVO;
    }

    private void refreshStudentCount(Long classId) {
        ClassDO update = new ClassDO();
        update.setId(classId);
        update.setStudentCount(classService.countActiveStudents(classId));
        classMapper.updateById(update);
    }

    /**
     * 批量获取用户信息（用于姓名快照）；RPC 异常时降级，不影响主流程
     */
    private Map<Long, AdminUserRespDTO> fetchUserMap(List<Long> userIds) {
        try {
            return adminUserApi.getUserMap(userIds);
        } catch (Throwable ex) {
            log.warn("[fetchUserMap][获取用户信息失败，降级 userName 为空] userIds={}", userIds, ex);
            return Collections.emptyMap();
        }
    }

    /**
     * 批量获取用户所属部门名称（用于部门/班组快照）；RPC 异常时降级为空，不影响主流程
     */
    private Map<Long, String> fetchDeptNameMap(Collection<AdminUserRespDTO> users) {
        Set<Long> deptIds = users.stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyMap();
        }
        try {
            return deptApi.getDeptList(deptIds).getCheckedData().stream()
                    .collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName, (a, b) -> a));
        } catch (Throwable ex) {
            log.warn("[fetchDeptNameMap][获取部门信息失败，降级 deptName 为空] deptIds={}", deptIds, ex);
            return Collections.emptyMap();
        }
    }

    private String fetchDeptName(AdminUserRespDTO user, Map<Long, String> deptNameMap) {
        return user == null || user.getDeptId() == null ? null : deptNameMap.get(user.getDeptId());
    }

    /**
     * 组装入班学员展示信息（姓名 / 部门快照，RPC 降级时为空）
     */
    private ClassStudentImportAllocateRespVO.User buildAllocateUser(Long userId, Map<Long, AdminUserRespDTO> userMap,
                                                                    Map<Long, String> deptNameMap) {
        ClassStudentImportAllocateRespVO.User user = new ClassStudentImportAllocateRespVO.User();
        user.setUserId(userId);
        AdminUserRespDTO userDTO = userMap.get(userId);
        user.setUserName(userDTO == null ? null : userDTO.getNickname());
        user.setDeptName(fetchDeptName(userDTO, deptNameMap));
        return user;
    }

    private String getStatusName(Integer status) {
        ClassStatusEnum statusEnum = ClassStatusEnum.getByStatus(status);
        return statusEnum == null ? String.valueOf(status) : statusEnum.getName();
    }

}
