package cn.iocoder.txgy.module.training.service.learner;

import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseMediaDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.DocReadLogDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnSessionDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.learning.DocReadLogMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnSessionMapper;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import cn.iocoder.txgy.module.training.service.course.CourseService;
import cn.iocoder.txgy.module.training.service.learning.LearningConfigService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学员学习进度计算器
 *
 * <p>负责把「学员 × 课程」的有效会话、文档翻页明细折算为媒资级进度/学时：
 * <ul>
 *     <li>学时值 = 课程学分，按可学媒资（文档/视频）均摊</li>
 *     <li>视频：完成比例 = min(有效学习秒 / (媒资时长 × 完成比例阈值), 1)</li>
 *     <li>文档：完成比例 = 覆盖页数(停留≥阈值) / 总页数，并兼容无页数兜底（按停留时长）</li>
 *     <li>已获学时 = Σ 各媒资学分 × 完成比例，封顶课程学分</li>
 * </ul>
 *
 * @author 云培训学院课题小组
 */
@Component
public class LearnerProgressCalculator {

    /** 无时长视频/无页数文档的兜底有效学习秒数 */
    private static final int FALLBACK_SECONDS = 1800;

    @Resource
    private CourseService courseService;
    @Resource
    private LearnSessionMapper learnSessionMapper;
    @Resource
    private DocReadLogMapper docReadLogMapper;
    @Resource
    private LearningConfigService configService;

    // ========== 计算结果模型 ==========

    @Data
    public static class MediaStat {

        /** 课程媒资 ID */
        private Long mediaId;
        /** 媒资类型：1 文档 2 视频 */
        private Integer mediaType;
        /** 媒资类型名称 */
        private String mediaTypeName;
        /** 媒资名称 */
        private String name;
        /** 播放/预览地址 */
        private String url;
        /** 视频时长（秒） */
        private Integer durationSeconds;
        /** 视频累计有效学习秒数 */
        private Integer validSeconds;
        /** 文档已覆盖页数 */
        private Integer docCoveredPages;
        /** 文档总页数 */
        private Integer docTotalPages;
        /** 文档停留总秒数（用于轨迹展示与兜底判定） */
        private Integer docStaySeconds;
        /** 完成比例 0~1 */
        private Double ratio;
        /** 是否已完成 */
        private Boolean completed;
        /** 本媒资分摊学分 */
        private BigDecimal creditShare;
        /** 本媒资已获学分 */
        private BigDecimal creditEarned;

    }

    @Data
    public static class TaskProgress {

        /** 学习任务 */
        private LearnerTaskDO task;
        /** 可学媒资统计（按媒资 sort 排序） */
        private List<MediaStat> mediaStats;
        /** 已完成媒资数 */
        private Integer doneCount;
        /** 可学媒资总数 */
        private Integer totalCount;
        /** 平均完成比例（0~1） */
        private Double ratioAvg;
        /** 已获学分（小时） */
        private BigDecimal creditEarned;
        /** 课程学分（小时） */
        private BigDecimal creditTotal;
        /** 是否全部完成 */
        private Boolean allCompleted;

    }

    // ========== 对外计算 ==========

    /**
     * 计算课程学习进度（只读，不落库）。媒资过滤条件见说明。
     */
    public TaskProgress calc(Long userId, Long courseId, LearnerTaskDO task) {
        TaskProgress progress = new TaskProgress();
        progress.setTask(task);

        CourseDO course = courseService.getCourse(courseId);
        List<CourseMediaDO> mediaList = courseService.getMediaList(courseId)
                .stream().sorted(Comparator.comparing(CourseMediaDO::getSort))
                .collect(Collectors.toList());

        // 可学媒资：文档(1)/视频(2)
        List<CourseMediaDO> learnable = mediaList.stream()
                .filter(m -> m.getMediaType() != null
                        && (m.getMediaType().equals(CourseMediaTypeEnum.DOCUMENT.getType())
                        || m.getMediaType().equals(CourseMediaTypeEnum.VIDEO.getType())))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(learnable)) {
            progress.setMediaStats(new ArrayList<>());
            progress.setDoneCount(0);
            progress.setTotalCount(0);
            progress.setRatioAvg(0D);
            progress.setCreditEarned(BigDecimal.ZERO);
            progress.setCreditTotal(course == null ? BigDecimal.ZERO : course.getCredit());
            progress.setAllCompleted(false);
            return progress;
        }

        List<Long> mediaIds = learnable.stream().map(CourseMediaDO::getId).collect(Collectors.toList());
        // 有效会话（正常结束）
        List<LearnSessionDO> sessions = learnSessionMapper.selectListByUserAndCourse(userId, courseId)
                .stream().filter(s -> "ended".equals(s.getStatus()) && mediaIds.contains(s.getMediaId()))
                .collect(Collectors.toList());
        // 视频有效秒
        Map<Long, Integer> validSecondsMap = new HashMap<>();
        for (LearnSessionDO session : sessions) {
            validSecondsMap.merge(session.getMediaId(), session.getValidSeconds() == null ? 0 : session.getValidSeconds(), Integer::sum);
        }
        // 文档翻页明细
        List<DocReadLogDO> docLogs = docReadLogMapper.selectListByUserAndCourse(userId, courseId)
                .stream().filter(log -> mediaIds.contains(log.getMediaId())).collect(Collectors.toList());

        BigDecimal credit = course == null || course.getCredit() == null ? BigDecimal.ZERO : course.getCredit();
        int count = learnable.size();
        BigDecimal share = count > 0 ? credit.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        double videoRatio = configService.getDouble(LearningConfigService.KEY_VIDEO_COMPLETE_RATIO, 0.9D);
        double docRatio = configService.getDouble(LearningConfigService.KEY_DOC_COMPLETE_RATIO, 0.8D);
        int docMinStay = configService.getInt(LearningConfigService.KEY_DOC_MIN_STAY_SECONDS, 5);

        List<MediaStat> stats = new ArrayList<>();
        int doneCount = 0;
        double sumRatio = 0D;
        BigDecimal earned = BigDecimal.ZERO;
        for (int i = 0; i < learnable.size(); i++) {
            CourseMediaDO media = learnable.get(i);
            MediaStat stat = new MediaStat();
            stat.setMediaId(media.getId());
            stat.setMediaType(media.getMediaType());
            stat.setMediaTypeName(CourseMediaTypeEnum.getByType(media.getMediaType()).getName());
            stat.setName(media.getName());
            stat.setUrl(media.getUrl());
            stat.setDurationSeconds(media.getDurationSeconds());
            // 学分分摊：最后一个吸收尾差
            if (i == learnable.size() - 1) {
                BigDecimal used = share.multiply(BigDecimal.valueOf(learnable.size() - 1));
                stat.setCreditShare(credit.subtract(used));
            } else {
                stat.setCreditShare(share);
            }

            double ratio;
            if (media.getMediaType().equals(CourseMediaTypeEnum.VIDEO.getType())) {
                int valid = validSecondsMap.getOrDefault(media.getId(), 0);
                stat.setValidSeconds(valid);
                int duration = media.getDurationSeconds() == null || media.getDurationSeconds() <= 0
                        ? FALLBACK_SECONDS : media.getDurationSeconds();
                ratio = Math.min(1D, (double) valid / (duration * videoRatio));
                stat.setRatio(ratio);
                stat.setCompleted(ratio >= 1D);
                stat.setDocCoveredPages(0);
                stat.setDocTotalPages(0);
            } else {
                // 文档：优先按页覆盖，其次按停留时长兜底
                List<DocReadLogDO> mediaLogs = docLogs.stream()
                        .filter(log -> log.getMediaId().equals(media.getId())).collect(Collectors.toList());
                Map<Integer, Integer> stayByPage = new HashMap<>();
                int maxTotalPages = 0;
                long totalStay = 0;
                for (DocReadLogDO log : mediaLogs) {
                    stayByPage.merge(log.getPageNo() == null ? 0 : log.getPageNo(),
                            log.getStaySeconds() == null ? 0 : log.getStaySeconds(), Integer::sum);
                    totalStay += log.getStaySeconds() == null ? 0 : log.getStaySeconds();
                    if (log.getTotalPages() != null) {
                        maxTotalPages = Math.max(maxTotalPages, log.getTotalPages());
                    }
                }
                long covered = stayByPage.entrySet().stream()
                        .filter(e -> e.getValue() >= docMinStay).count();
                stat.setDocCoveredPages((int) covered);
                stat.setDocTotalPages(maxTotalPages);
                stat.setDocStaySeconds((int) totalStay);
                stat.setValidSeconds((int) totalStay);
                if (maxTotalPages > 0) {
                    ratio = Math.min(1D, (double) covered / (maxTotalPages * docRatio));
                } else {
                    ratio = Math.min(1D, (double) totalStay / FALLBACK_SECONDS);
                }
                stat.setRatio(ratio);
                stat.setCompleted(ratio >= 1D);
            }

            sumRatio += ratio;
            if (stat.getCompleted()) {
                doneCount++;
            }
            BigDecimal mediaEarned = stat.getCreditShare().multiply(BigDecimal.valueOf(stat.getRatio()))
                    .setScale(2, RoundingMode.HALF_UP);
            stat.setCreditEarned(mediaEarned);
            earned = earned.add(mediaEarned);
            stats.add(stat);
        }
        progress.setMediaStats(stats);
        progress.setDoneCount(doneCount);
        progress.setTotalCount(count);
        progress.setRatioAvg(count > 0 ? BigDecimal.valueOf(sumRatio / count).setScale(4, RoundingMode.HALF_UP).doubleValue() : 0D);
        progress.setCreditEarned(earned.min(credit).setScale(2, RoundingMode.HALF_UP));
        progress.setCreditTotal(credit);
        progress.setAllCompleted(doneCount >= count);
        return progress;
    }

}
