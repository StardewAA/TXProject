package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.DocStatusRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerTrackRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerTrackSummaryRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.CheckinRecordDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnSessionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LiveRoomDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.CheckinRecordMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnSessionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnerTaskMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LiveRoomMapper;
import cn.iocoder.txgy.module.training.enums.learner.LearnMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerTaskStatusEnum;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 学员端 - 个人学习轨迹 Service 实现
 *
 * <p>轨迹口径：仅统计已结束(ended)会话的有效秒数；学时折算采用服务端媒体级
 * 汇总（LearnerProgressCalculator），并按会话有效时长占比摊到各会话明细。</p>
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class LearnerTrackServiceImpl implements LearnerTrackService {

    @Resource
    private LearnerTaskMapper learnerTaskMapper;
    @Resource
    private LearnSessionMapper learnSessionMapper;
    @Resource
    private CheckinRecordMapper checkinRecordMapper;
    @Resource
    private LiveRoomMapper liveRoomMapper;
    @Resource
    private CourseService courseService;
    @Resource
    private LearnerProgressCalculator progressCalculator;

    @Override
    public PageResult<LearnerTrackRespVO> getMyTracks(Long userId, String mediaType, PageParam pageParam) {
        List<LearnSessionDO> sessions = learnSessionMapper.selectEndedListByUser(userId);
        if (CollectionUtils.isEmpty(sessions)) {
            return new PageResult<>(new ArrayList<>(), 0L);
        }
        List<LearnerTrackRespVO> rows = buildTrackRows(userId, sessions);
        if (StringUtils.hasText(mediaType)) {
            rows = rows.stream()
                    .filter(row -> mediaType.equals(row.getMediaType()))
                    .collect(Collectors.toList());
        }
        // 分页（行数一般较小，直接切片）
        int fromIndex = Math.min((pageParam.getPageNo() - 1) * pageParam.getPageSize(), rows.size());
        int toIndex = Math.min(fromIndex + pageParam.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(fromIndex, toIndex), (long) rows.size());
    }

    @Override
    public LearnerTrackSummaryRespVO getMySummary(Long userId) {
        LearnerTrackSummaryRespVO summary = new LearnerTrackSummaryRespVO();

        // 1. 课程维度：任务与学时
        List<LearnerTaskDO> tasks = learnerTaskMapper.selectList(
                new LambdaQueryWrapperX<LearnerTaskDO>()
                        .eq(LearnerTaskDO::getUserId, userId));
        int taskTotal = tasks.size();
        int taskCompleted = 0;
        BigDecimal creditTotal = BigDecimal.ZERO;
        BigDecimal creditEarned = BigDecimal.ZERO;
        List<LearnerTrackSummaryRespVO.CourseRow> courseRows = new ArrayList<>();
        if (!CollectionUtils.isEmpty(tasks)) {
            for (LearnerTaskDO task : tasks) {
                CourseDO course = courseService.getCourse(task.getCourseId());
                LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(userId,
                        task.getCourseId(), task);
                LearnerTrackSummaryRespVO.CourseRow row = new LearnerTrackSummaryRespVO.CourseRow();
                row.setCourseId(task.getCourseId());
                row.setCourseTitle(course == null ? null : course.getTitle());
                int status = task.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : task.getStatus();
                if (progress != null) {
                    if (progress.getAllCompleted()) {
                        status = LearnerTaskStatusEnum.COMPLETED.getStatus();
                        taskCompleted++;
                    } else if (status != LearnerTaskStatusEnum.COMPLETED.getStatus()
                            && progress.getRatioAvg() != null && progress.getRatioAvg() > 0D) {
                        status = LearnerTaskStatusEnum.LEARNING.getStatus();
                    }
                    creditTotal = creditTotal.add(nvl(progress.getCreditTotal()));
                    creditEarned = creditEarned.add(nvl(progress.getCreditEarned()));
                    row.setProgressPercent(percent(progress.getRatioAvg()));
                    row.setCreditTotal(progress.getCreditTotal());
                    row.setCreditEarned(progress.getCreditEarned());
                } else {
                    if (Objects.equals(status, LearnerTaskStatusEnum.COMPLETED.getStatus())) {
                        taskCompleted++;
                    }
                    creditTotal = creditTotal.add(nvl(task.getCreditTotal()));
                    creditEarned = creditEarned.add(nvl(task.getCreditEarned()));
                    row.setProgressPercent(task.getProgressPercent());
                    row.setCreditTotal(task.getCreditTotal());
                    row.setCreditEarned(task.getCreditEarned());
                }
                row.setTaskStatus(status);
                courseRows.add(row);
            }
        }
        summary.setTaskTotalCount(taskTotal);
        summary.setTaskCompletedCount(taskCompleted);
        summary.setCreditTotal(creditTotal.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalCreditEarned(creditEarned.setScale(2, RoundingMode.HALF_UP));

        // 2. 会话与类型维度
        List<LearnSessionDO> sessions = learnSessionMapper.selectEndedListByUser(userId);
        int totalValidSeconds = sessions.stream()
                .mapToInt(s -> s.getValidSeconds() == null ? 0 : s.getValidSeconds()).sum();
        summary.setTotalValidSeconds(totalValidSeconds);
        Map<String, List<LearnSessionDO>> byType = sessions.stream()
                .collect(Collectors.groupingBy(LearnSessionDO::getMediaType));
        List<LearnerTrackSummaryRespVO.TypeRow> typeRows = new ArrayList<>();
        for (Map.Entry<String, List<LearnSessionDO>> entry : byType.entrySet()) {
            LearnerTrackSummaryRespVO.TypeRow row = new LearnerTrackSummaryRespVO.TypeRow();
            row.setMediaType(entry.getKey());
            LearnMediaTypeEnum typeEnum = LearnMediaTypeEnum.getByType(entry.getKey());
            row.setMediaTypeName(typeEnum == null ? "未知" : typeEnum.getName());
            row.setSessionCount(entry.getValue().size());
            row.setTotalValidSeconds(entry.getValue().stream()
                    .mapToInt(s -> s.getValidSeconds() == null ? 0 : s.getValidSeconds()).sum());
            typeRows.add(row);
        }
        summary.setTypeRows(typeRows);
        summary.setCourseRows(courseRows);

        // 3. 打卡次数
        Long checkinCount = checkinRecordMapper.selectCount(
                new LambdaQueryWrapperX<CheckinRecordDO>()
                        .eq(CheckinRecordDO::getUserId, userId));
        summary.setCheckinCount(checkinCount == null ? 0L : checkinCount);
        return summary;
    }

    @Override
    public DocStatusRespVO getDocStatus(Long userId, Long courseId, Long mediaId) {
        DocStatusRespVO respVO = new DocStatusRespVO();
        respVO.setMediaId(mediaId);
        LearnerTaskDO task = learnerTaskMapper.selectByUserIdAndCourseId(userId, courseId);
        if (task == null) {
            resetDocStatus(respVO);
            return respVO;
        }
        LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(userId, courseId, task);
        if (progress == null || CollectionUtils.isEmpty(progress.getMediaStats())) {
            resetDocStatus(respVO);
            return respVO;
        }
        for (LearnerProgressCalculator.MediaStat stat : progress.getMediaStats()) {
            if (Objects.equals(stat.getMediaId(), mediaId)) {
                respVO.setCompleted(stat.getCompleted());
                respVO.setValidSeconds(stat.getValidSeconds() == null ? 0 : stat.getValidSeconds());
                respVO.setCoveredPages(stat.getDocCoveredPages() == null ? 0 : stat.getDocCoveredPages());
                respVO.setTotalPages(stat.getDocTotalPages() == null ? 0 : stat.getDocTotalPages());
                respVO.setProgressPercent(percent(stat.getRatio()));
                return respVO;
            }
        }
        resetDocStatus(respVO);
        return respVO;
    }

    @Override
    public List<CheckinRecordDO> getMyCheckins(Long userId, Long courseId) {
        if (courseId != null) {
            return checkinRecordMapper.selectListByUserAndCourse(userId, courseId);
        }
        return checkinRecordMapper.selectListByUser(userId);
    }

    // ========== 私有方法 ==========

    /**
     * 将会话明细转轨迹行：学时按媒体级汇总学分 × 会话有效占比分摊。
     */
    private List<LearnerTrackRespVO> buildTrackRows(Long userId, List<LearnSessionDO> sessions) {
        // 按课程分组加载上下文（缓存）
        Map<Long, CourseDO> courseCache = new HashMap<>();
        Map<Long, List<CourseMediaDO>> mediaCache = new HashMap<>();
        Map<Long, LearnerTaskDO> taskCache = new HashMap<>();
        Map<Long, LearnerProgressCalculator.TaskProgress> progressCache = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> creditByCourseMedia = new HashMap<>();
        Map<Long, Map<Long, Integer>> validByCourseMedia = new HashMap<>();
        Map<Long, String> liveRoomTitleCache = new HashMap<>();

        List<Long> courseIds = sessions.stream().map(LearnSessionDO::getCourseId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        for (Long courseId : courseIds) {
            CourseDO course = courseService.getCourse(courseId);
            if (course != null) {
                courseCache.put(courseId, course);
                mediaCache.put(courseId, courseService.getMediaList(courseId));
            }
            LearnerTaskDO task = learnerTaskMapper.selectByUserIdAndCourseId(userId, courseId);
            if (task != null) {
                taskCache.put(courseId, task);
                LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(userId, courseId, task);
                progressCache.put(courseId, progress);
                if (progress != null && !CollectionUtils.isEmpty(progress.getMediaStats())) {
                    Map<Long, BigDecimal> creditMap = progress.getMediaStats().stream().collect(Collectors.toMap(
                            LearnerProgressCalculator.MediaStat::getMediaId,
                            stat -> nvl(stat.getCreditEarned())));
                    creditByCourseMedia.put(courseId, creditMap);
                }
            }
        }
        // 会话有效秒按 课程-媒资 聚合
        sessions.forEach(session -> {
            if (session.getCourseId() == null) {
                return;
            }
            validByCourseMedia.computeIfAbsent(session.getCourseId(), k -> new HashMap<>())
                    .merge(session.getMediaId(), nvlInt(session.getValidSeconds()), Integer::sum);
        });

        List<LearnerTrackRespVO> rows = new ArrayList<>(sessions.size());
        for (LearnSessionDO session : sessions) {
            LearnerTrackRespVO row = new LearnerTrackRespVO();
            row.setSessionId(session.getId());
            row.setCourseId(session.getCourseId());
            row.setMediaId(session.getMediaId());
            row.setMediaType(session.getMediaType());
            LearnMediaTypeEnum typeEnum = LearnMediaTypeEnum.getByType(session.getMediaType());
            row.setMediaTypeName(typeEnum == null ? "未知" : typeEnum.getName());
            row.setStartAt(session.getStartAt());
            row.setEndAt(session.getEndAt());
            row.setValidSeconds(session.getValidSeconds());
            row.setEndReason(session.getEndReason());
            if (session.getCourseId() != null) {
                CourseDO course = courseCache.get(session.getCourseId());
                row.setCourseTitle(course == null ? null : course.getTitle());
                String mediaName = resolveMediaName(mediaCache.get(session.getCourseId()), session.getMediaId());
                row.setMediaName(mediaName);
                BigDecimal credit = BigDecimal.ZERO;
                boolean completed = false;
                Map<Long, BigDecimal> creditMap = creditByCourseMedia.get(session.getCourseId());
                LearnerProgressCalculator.TaskProgress progress = progressCache.get(session.getCourseId());
                if (creditMap != null) {
                    BigDecimal mediaCredit = creditMap.get(session.getMediaId());
                    Integer mediaValid = validByCourseMedia
                            .getOrDefault(session.getCourseId(), Map.of())
                            .getOrDefault(session.getMediaId(), 0);
                    if (mediaCredit != null && mediaValid != null && mediaValid > 0) {
                        credit = mediaCredit.multiply(BigDecimal.valueOf(nvlInt(session.getValidSeconds())))
                                .divide(BigDecimal.valueOf(mediaValid), 2, RoundingMode.HALF_UP);
                    }
                    if (progress != null && !CollectionUtils.isEmpty(progress.getMediaStats())) {
                        for (LearnerProgressCalculator.MediaStat stat : progress.getMediaStats()) {
                            if (Objects.equals(stat.getMediaId(), session.getMediaId())) {
                                completed = stat.getCompleted();
                                break;
                            }
                        }
                    }
                }
                row.setCreditEarned(credit);
                row.setCompleted(completed);
            } else {
                // 直播/回放独立会话：无课程，名称取直播房间标题
                String title = liveRoomTitleCache.computeIfAbsent(session.getMediaId(), id -> {
                    LiveRoomDO room = liveRoomMapper.selectById(id);
                    return room == null ? null : room.getTitle();
                });
                row.setMediaName(title);
                row.setCreditEarned(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                row.setCompleted(false);
            }
            rows.add(row);
        }
        // 新会话在前
        rows.sort(Comparator.comparing(LearnerTrackRespVO::getSessionId, Comparator.reverseOrder()));
        return rows;
    }

    private String resolveMediaName(List<CourseMediaDO> medias, Long mediaId) {
        if (CollectionUtils.isEmpty(medias)) {
            return null;
        }
        for (CourseMediaDO media : medias) {
            if (Objects.equals(media.getId(), mediaId)) {
                return media.getName();
            }
        }
        return null;
    }

    private void resetDocStatus(DocStatusRespVO respVO) {
        respVO.setCompleted(false);
        respVO.setValidSeconds(0);
        respVO.setCoveredPages(0);
        respVO.setTotalPages(0);
        respVO.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
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
