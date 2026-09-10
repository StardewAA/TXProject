package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerCourseCardRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerCourseDetailRespVO;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.LearnerMediaItemVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnerTaskMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerSourceTypeEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerTaskStatusEnum;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 学员课程服务实现
 *
 * @author 云培训学院课题小组
 */
@Service
@Slf4j
public class LearnerCourseServiceImpl implements LearnerCourseService {

    @Resource
    private LearnerTaskMapper learnerTaskMapper;
    @Resource
    private CourseService courseService;
    @Resource
    private LearnerProgressCalculator progressCalculator;

    @Override
    public List<LearnerCourseCardRespVO> getMyCourses(Long userId) {
        List<LearnerTaskDO> tasks = learnerTaskMapper.selectList(
                new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<LearnerTaskDO>()
                        .eq(LearnerTaskDO::getUserId, userId)
                        .orderByDesc(LearnerTaskDO::getId));
        if (CollectionUtils.isEmpty(tasks)) {
            return new ArrayList<>();
        }
        List<LearnerCourseCardRespVO> result = new ArrayList<>();
        for (LearnerTaskDO task : tasks) {
            CourseDO course = courseService.getCourse(task.getCourseId());
            if (course == null) {
                continue;
            }
            LearnerProgressCalculator.TaskProgress progress = progressCalculator.calc(userId, task.getCourseId(), task);
            LearnerCourseCardRespVO card = new LearnerCourseCardRespVO();
            card.setTaskId(task.getId());
            card.setCourseId(task.getCourseId());
            card.setSourceType(task.getSourceType());
            int status = task.getStatus() == null ? LearnerTaskStatusEnum.TODO.getStatus() : task.getStatus();
            if (progress.getAllCompleted()) {
                status = LearnerTaskStatusEnum.COMPLETED.getStatus();
            } else if (status != LearnerTaskStatusEnum.COMPLETED.getStatus()
                    && (progress.getRatioAvg() != null && progress.getRatioAvg() > 0D)) {
                status = LearnerTaskStatusEnum.LEARNING.getStatus();
            }
            card.setStatus(status);
            card.setCourseTitle(course.getTitle());
            card.setCoverUrl(course.getCoverUrl());
            card.setLecturer(course.getLecturer());
            card.setCourseCredit(course.getCredit());
            card.setCourseMediaType(course.getMediaType());
            card.setCourseDurationMinutes(course.getDurationMinutes());
            card.setTotalCount(progress.getTotalCount());
            card.setDoneCount(progress.getDoneCount());
            card.setProgressPercent(percent(progress.getRatioAvg()));
            card.setCreditTotal(progress.getCreditTotal());
            card.setCreditEarned(progress.getCreditEarned());
            card.setStartedAt(task.getStartedAt());
            card.setCompletedAt(task.getCompletedAt());
            card.setCreateTime(task.getCreateTime());
            result.add(card);
        }
        return result;
    }

    @Override
    public LearnerCourseDetailRespVO getCourseDetail(Long userId, Long courseId) {
        CourseDO course = courseService.getCourse(courseId);
        if (course == null) {
            throw exception(ErrorCodeConstants.COURSE_NOT_EXISTS);
        }
        LearnerTaskDO task = learnerTaskMapper.selectByUserIdAndCourseId(userId, courseId);
        LearnerProgressCalculator.TaskProgress progress = task != null
                ? progressCalculator.calc(userId, courseId, task) : null;

        LearnerCourseDetailRespVO detail = new LearnerCourseDetailRespVO();
        detail.setCourseId(course.getId());
        detail.setTitle(course.getTitle());
        detail.setCoverUrl(course.getCoverUrl());
        detail.setSummary(course.getSummary());
        detail.setCredit(course.getCredit());
        detail.setDurationMinutes(course.getDurationMinutes());
        detail.setCourseMediaType(course.getMediaType());
        detail.setLecturer(course.getLecturer());
        if (task != null) {
            detail.setTaskId(task.getId());
            detail.setSourceType(task.getSourceType());
            detail.setStartedAt(task.getStartedAt());
            detail.setCompletedAt(task.getCompletedAt());
        }
        if (progress != null) {
            detail.setTaskStatus(progress.getAllCompleted() ? LearnerTaskStatusEnum.COMPLETED.getStatus()
                    : LearnerTaskStatusEnum.LEARNING.getStatus());
            detail.setProgressPercent(percent(progress.getRatioAvg()));
            detail.setCreditTotal(progress.getCreditTotal());
            detail.setCreditEarned(progress.getCreditEarned());
            detail.setDoneCount(progress.getDoneCount());
            detail.setTotalCount(progress.getTotalCount());
        } else {
            detail.setTaskStatus(LearnerTaskStatusEnum.TODO.getStatus());
            detail.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            detail.setCreditTotal(course.getCredit() == null ? BigDecimal.ZERO : course.getCredit());
            detail.setCreditEarned(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            detail.setDoneCount(0);
            detail.setTotalCount(0);
        }

        Map<Long, LearnerProgressCalculator.MediaStat> statMap = new HashMap<>();
        if (progress != null && !CollectionUtils.isEmpty(progress.getMediaStats())) {
            progress.getMediaStats().forEach(stat -> statMap.put(stat.getMediaId(), stat));
        }
        List<LearnerMediaItemVO> medias = new ArrayList<>();
        List<CourseMediaDO> mediaList = courseService.getMediaList(courseId).stream()
                .sorted(Comparator.comparing(CourseMediaDO::getSort))
                .collect(Collectors.toList());
        for (CourseMediaDO media : mediaList) {
            LearnerMediaItemVO item = new LearnerMediaItemVO();
            item.setMediaId(media.getId());
            item.setMediaType(media.getMediaType());
            CourseMediaTypeEnum typeEnum = CourseMediaTypeEnum.getByType(media.getMediaType());
            item.setMediaTypeName(typeEnum == null ? "未知" : typeEnum.getName());
            item.setName(media.getName());
            item.setUrl(media.getUrl());
            item.setDurationSeconds(media.getDurationSeconds());
            item.setSort(media.getSort());
            boolean learnable = media.getMediaType() != null
                    && (media.getMediaType().equals(CourseMediaTypeEnum.DOCUMENT.getType())
                    || media.getMediaType().equals(CourseMediaTypeEnum.VIDEO.getType()));
            item.setLearnable(learnable);
            LearnerProgressCalculator.MediaStat stat = statMap.get(media.getId());
            if (stat != null) {
                item.setValidSeconds(stat.getValidSeconds());
                item.setDocCoveredPages(stat.getDocCoveredPages());
                item.setDocTotalPages(stat.getDocTotalPages());
                item.setProgressPercent(percent(stat.getRatio()));
                item.setCompleted(stat.getCompleted());
                item.setCreditShare(stat.getCreditShare());
                item.setCreditEarned(stat.getCreditEarned());
            } else {
                item.setValidSeconds(0);
                item.setDocCoveredPages(0);
                item.setDocTotalPages(0);
                item.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                item.setCompleted(false);
                if (learnable) {
                    item.setCreditShare(BigDecimal.ZERO);
                }
                item.setCreditEarned(BigDecimal.ZERO);
            }
            medias.add(item);
        }
        detail.setMedias(medias);
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enrollSelf(Long userId, Long courseId) {
        CourseDO course = courseService.getCourse(courseId);
        if (course == null) {
            throw exception(ErrorCodeConstants.COURSE_NOT_EXISTS);
        }
        if (!Objects.equals(course.getStatus(), CourseStatusEnum.ON_SHELF.getStatus())) {
            throw exception(ErrorCodeConstants.LEARNER_COURSE_NOT_ONLINE);
        }
        LearnerTaskDO exist = learnerTaskMapper.selectByUserIdAndCourseId(userId, courseId);
        if (exist != null) {
            log.info("[enrollSelf][用户 {} 已选学课程 {}，幂等跳过]", userId, courseId);
            return;
        }
        LearnerTaskDO task = new LearnerTaskDO();
        task.setUserId(userId);
        task.setCourseId(courseId);
        task.setSourceType(LearnerSourceTypeEnum.SELF.getSourceType());
        task.setStatus(LearnerTaskStatusEnum.TODO.getStatus());
        task.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        task.setCreditTotal(course.getCredit());
        task.setCreditEarned(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        learnerTaskMapper.insert(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshTaskProgress(LearnerProgressCalculator.TaskProgress progress) {
        LearnerTaskDO task = progress.getTask();
        if (task == null) {
            return;
        }
        LearnerTaskDO update = new LearnerTaskDO();
        update.setId(task.getId());
        update.setProgressPercent(percent(progress.getRatioAvg()));
        update.setCreditTotal(progress.getCreditTotal());
        update.setCreditEarned(progress.getCreditEarned());
        int status = LearnerTaskStatusEnum.LEARNING.getStatus();
        if (progress.getAllCompleted()) {
            status = LearnerTaskStatusEnum.COMPLETED.getStatus();
            if (task.getCompletedAt() == null) {
                update.setCompletedAt(LocalDateTime.now());
            }
        }
        update.setStatus(status);
        learnerTaskMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskLearning(LearnerTaskDO task) {
        if (task == null) {
            return;
        }
        LearnerTaskDO update = new LearnerTaskDO();
        update.setId(task.getId());
        update.setStatus(LearnerTaskStatusEnum.LEARNING.getStatus());
        if (task.getStartedAt() == null) {
            update.setStartedAt(LocalDateTime.now());
        }
        learnerTaskMapper.updateById(update);
    }

    private static BigDecimal percent(Double ratio) {
        if (ratio == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP);
    }

}
