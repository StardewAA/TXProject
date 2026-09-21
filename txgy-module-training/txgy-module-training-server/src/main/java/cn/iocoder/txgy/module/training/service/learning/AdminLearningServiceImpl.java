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
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearnerTaskRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningAssignReqVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningStatRespVO;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.LearningTaskPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnSessionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnSessionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnerTaskMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        // 2. 已有学习任务的学员（作为历史学员候选）
        List<LearnerTaskDO> allTasks = learnerTaskMapper.selectList(
                new LambdaQueryWrapperX<LearnerTaskDO>().orderByDesc(LearnerTaskDO::getId));
        List<Long> userIds = allTasks.stream().map(LearnerTaskDO::getUserId).distinct().collect(Collectors.toList());
        respVO.setUserCount((long) userIds.size());
        Map<Long, String> nicknames = fetchNicknames(userIds);
        List<AssignCandidateRespVO.UserItem> users = new ArrayList<>();
        for (Long userId : userIds) {
            AssignCandidateRespVO.UserItem item = new AssignCandidateRespVO.UserItem();
            item.setId(userId);
            item.setNickname(nicknames.get(userId));
            users.add(item);
        }
        respVO.setUsers(users);
        return respVO;
    }

    @Override
    public PageResult<LearnerTaskRespVO> getTaskPage(LearningTaskPageReqVO reqVO) {
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
        wrapper.orderByDesc(LearnerTaskDO::getId);
        PageResult<LearnerTaskDO> taskPage = learnerTaskMapper.selectPage(reqVO, wrapper);

        Map<Long, CourseDO> courseCache = new HashMap<>();
        List<LearnerTaskRespVO> result = new ArrayList<>();
        for (LearnerTaskDO task : taskPage.getList()) {
            CourseDO course = courseCache.computeIfAbsent(task.getCourseId(),
                    id -> courseService.getCourse(id));
            LearnerTaskRespVO respVO = BeanUtils.toBean(task, LearnerTaskRespVO.class);
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

    @Override
    public List<LearningStatRespVO> getLearnStats() {
        List<LearnerTaskDO> allTasks = learnerTaskMapper.selectList(
                new LambdaQueryWrapperX<LearnerTaskDO>().orderByAsc(LearnerTaskDO::getCourseId));
        if (CollectionUtils.isEmpty(allTasks)) {
            return Collections.emptyList();
        }
        Map<Long, List<LearnerTaskDO>> byCourse = allTasks.stream()
                .collect(Collectors.groupingBy(LearnerTaskDO::getCourseId));
        List<LearningStatRespVO> result = new ArrayList<>();
        for (Map.Entry<Long, List<LearnerTaskDO>> entry : byCourse.entrySet()) {
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
            BigDecimal sumProgress = BigDecimal.ZERO;
            for (LearnerTaskDO task : entry.getValue()) {
                LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(task.getUserId(),
                        task.getCourseId(), task);
                boolean allDone = progress != null && progress.getAllCompleted();
                if (allDone || Objects.equals(task.getStatus(), LearnerTaskStatusEnum.COMPLETED.getStatus())) {
                    completedCount++;
                } else if (progress != null && progress.getRatioAvg() != null && progress.getRatioAvg() > 0D
                        || Objects.equals(task.getStatus(), LearnerTaskStatusEnum.LEARNING.getStatus())) {
                    learningCount++;
                } else {
                    todoCount++;
                }
                if (progress != null) {
                    sumCreditEarned = sumCreditEarned.add(nvl(progress.getCreditEarned()));
                    sumProgress = sumProgress.add(nvl(percent(progress.getRatioAvg())));
                }
            }
            stat.setTaskTotal(total);
            stat.setTodoCount(todoCount);
            stat.setLearningCount(learningCount);
            stat.setCompletedCount(completedCount);
            stat.setCreditTotal(entry.getValue().get(0).getCreditTotal());
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
