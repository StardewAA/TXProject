package cn.iocoder.txgy.module.training.service.learner;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.controller.portal.learner.vo.ExamEligibilityRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.CourseDO;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.LearnerTaskDO;
import cn.iocoder.txgy.module.training.dal.mysql.course.CourseMapper;
import cn.iocoder.txgy.module.training.dal.mysql.learning.LearnerTaskMapper;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import cn.iocoder.txgy.module.training.enums.learner.LearnerTaskStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.training.enums.ErrorCodeConstants.EXAM_ELIGIBILITY_JOB_EMPTY;

/**
 * 学员端 - 岗位认证考试资格 Service 实现
 *
 * <p>口径（按认证等级递进，不依赖学习地图）：</p>
 * <ul>
 *   <li>课程与岗位的相关性由系统自行判断：课程适用岗位（applicable_posts，顿号分隔）精确命中工种名，
 *       或课程标题包含工种名；每个认证等级最多取 2 门（岗位专属课程优先）；</li>
 *   <li>等级与课程等级对应：未标注等级 → 初级工、L1-L2 → 中级工、L2-L3 → 高级工、
 *       L3-L4 → 技师、L4-L5 → 高级技师；</li>
 *   <li>等级递进解锁：完成当前等级全部必修课程后，下一等级课程才开放（晋升前不可学高级课程）；
 *       某等级无相关课程时视为直接满足，不阻塞后续等级；</li>
 *   <li>每个等级一场线上理论考试（90 分钟），完成该等级必修课程即可参加，通过即获得认证（无实操考核）。</li>
 * </ul>
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
@Slf4j
public class LearnerExamServiceImpl implements LearnerExamService {

    /**
     * 每个认证等级的必修课程上限
     */
    private static final int MAX_COURSES_PER_LEVEL = 2;

    /**
     * 在线理论考试时长（分钟）
     */
    private static final int EXAM_DURATION_MINUTES = 90;

    /**
     * 认证等级定义：等级名 + 对应课程等级标识（null 表示未标注等级的课程）
     */
    private static final List<LevelDef> LEVEL_DEFS = Arrays.asList(
            new LevelDef("初级工", 1, null),
            new LevelDef("中级工", 2, "L1-L2"),
            new LevelDef("高级工", 3, "L2-L3"),
            new LevelDef("技师", 4, "L3-L4"),
            new LevelDef("高级技师", 5, "L4-L5"));

    private record LevelDef(String level, int levelNo, String courseLevel) {
    }

    @Resource
    private CourseMapper courseMapper;
    @Resource
    private LearnerTaskMapper learnerTaskMapper;
    @Resource
    private LearnerProgressCalculator progressCalculator;

    @Override
    public List<String> getExamJobs() {
        // 工种来自上架课程的适用岗位（顿号分隔）去重
        return loadOnShelfCourses().stream()
                .map(this::splitPosts)
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public ExamEligibilityRespVO getExamEligibility(Long userId, String jobCode) {
        if (StrUtil.isBlank(jobCode)) {
            throw exception(EXAM_ELIGIBILITY_JOB_EMPTY);
        }
        ExamEligibilityRespVO respVO = new ExamEligibilityRespVO();
        respVO.setJobCode(jobCode);
        respVO.setJobName(jobCode);
        respVO.setExamDurationMinutes(EXAM_DURATION_MINUTES);

        // 与岗位相关的上架课程（自行判断相关性）
        List<CourseDO> related = loadOnShelfCourses().stream()
                .filter(c -> matchesJob(c, jobCode))
                .toList();

        // 按认证等级分组，每级最多 2 门（岗位专属课程优先，其次按编码）
        List<ExamEligibilityRespVO.LevelItem> levels = new ArrayList<>(LEVEL_DEFS.size());
        int completedTotal = 0;
        int courseTotal = 0;
        int currentLevelNo = LEVEL_DEFS.size();
        boolean prevLevelDone = true; // 上一等级必修是否全部完成（递进解锁）

        for (LevelDef def : LEVEL_DEFS) {
            List<CourseDO> levelCourses = related.stream()
                    .filter(c -> Objects.equals(normalizeLevel(c.getCourseLevel()), def.courseLevel()))
                    .sorted(Comparator
                            .comparingInt((CourseDO c) -> splitPosts(c).size())
                            .thenComparing(CourseDO::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(MAX_COURSES_PER_LEVEL)
                    .toList();

            ExamEligibilityRespVO.LevelItem item = new ExamEligibilityRespVO.LevelItem();
            item.setLevel(def.level());
            item.setLevelNo(def.levelNo());
            item.setCourseLevel(def.courseLevel() == null ? "无" : def.courseLevel());
            item.setUnlocked(prevLevelDone);

            int completed = 0;
            List<ExamEligibilityRespVO.ExamCourseItem> items = new ArrayList<>(levelCourses.size());
            for (CourseDO course : levelCourses) {
                ExamEligibilityRespVO.ExamCourseItem courseItem = buildCourseItem(userId, course);
                items.add(courseItem);
                if (Boolean.TRUE.equals(courseItem.getCompleted())) {
                    completed++;
                }
            }
            item.setCourses(items);
            item.setCourseTotal(levelCourses.size());
            item.setCourseCompleted(completed);
            // 该等级考试资格：已解锁 且（无必修课程或全部完成）
            boolean eligible = prevLevelDone && (levelCourses.isEmpty() || completed >= levelCourses.size());
            item.setEligible(eligible);
            item.setExamName(jobCode + def.level() + "认证考试");

            completedTotal += completed;
            courseTotal += levelCourses.size();
            if (prevLevelDone && !eligible) {
                currentLevelNo = Math.min(currentLevelNo, def.levelNo());
            }
            // 本等级满足（课程完成或无课程）才解锁下一等级
            prevLevelDone = eligible;
            levels.add(item);
        }

        respVO.setLevels(levels);
        respVO.setLevelTotal(levels.size());
        respVO.setCourseTotal(courseTotal);
        respVO.setCourseCompleted(completedTotal);
        respVO.setCurrentLevelNo(currentLevelNo);
        return respVO;
    }

    /**
     * 组装单门课程的完成情况
     */
    private ExamEligibilityRespVO.ExamCourseItem buildCourseItem(Long userId, CourseDO course) {
        ExamEligibilityRespVO.ExamCourseItem item = new ExamEligibilityRespVO.ExamCourseItem();
        item.setCourseId(course.getId());
        item.setCode(course.getCode());
        item.setTitle(course.getTitle());
        item.setCredit(course.getCredit());
        item.setStatus(LearnerTaskStatusEnum.TODO.getStatus());
        item.setProgressPercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        LearnerTaskDO task = learnerTaskMapper.selectByUserIdAndCourseId(userId, course.getId());
        LearnerProgressCalculator.TaskProgress progress =
                task != null ? progressCalculator.calc(userId, course.getId(), task) : null;
        boolean completed = progress != null && progress.getAllCompleted();
        if (completed) {
            item.setStatus(LearnerTaskStatusEnum.COMPLETED.getStatus());
        } else if (task != null && task.getStatus() != null) {
            item.setStatus(task.getStatus());
        }
        item.setCompleted(completed);
        item.setProgressPercent(progress != null && progress.getRatioAvg() != null
                ? BigDecimal.valueOf(progress.getRatioAvg() * 100).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        item.setCompletedAt(task != null ? task.getCompletedAt() : null);
        return item;
    }

    /**
     * 课程是否与岗位相关：适用岗位精确命中工种名，或课程标题包含工种名
     */
    private boolean matchesJob(CourseDO course, String jobName) {
        List<String> posts = splitPosts(course);
        for (String post : posts) {
            if (jobName.equals(post)) {
                return true;
            }
        }
        return StrUtil.isNotBlank(course.getTitle()) && course.getTitle().contains(jobName);
    }

    /**
     * 拆分课程适用岗位（顿号/逗号分隔）
     */
    private List<String> splitPosts(CourseDO course) {
        String posts = course.getApplicablePosts();
        if (StrUtil.isBlank(posts)) {
            return List.of();
        }
        return Arrays.stream(posts.split("[、,，]"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    /**
     * 归一化课程等级标识（空串归一为 null，即未标注等级）
     */
    private String normalizeLevel(String courseLevel) {
        return StrUtil.isBlank(courseLevel) ? null : courseLevel.trim();
    }

    /**
     * 加载当前生效且已上架的课程
     */
    private List<CourseDO> loadOnShelfCourses() {
        return courseMapper.selectList(new LambdaQueryWrapperX<CourseDO>()
                        .eq(CourseDO::getIsCurrent, true)
                        .eq(CourseDO::getStatus, CourseStatusEnum.ON_SHELF.getStatus()))
                .stream()
                .collect(Collectors.toMap(CourseDO::getCode, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new))
                .values().stream().toList();
    }
}
