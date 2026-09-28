package cn.iocoder.txgy.module.training.controller.portal.learner.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学员端 - 岗位认证考试资格 Response VO
 *
 * <p>口径（按认证等级递进，不依赖学习地图）：</p>
 * <ul>
 *   <li>岗位相关课程由系统按课程适用岗位自行判断（applicable_posts/课程标题与工种名匹配），每个认证等级最多 2 门必修；</li>
 *   <li>等级与课程等级的对应：未标注等级 → 晋升初级工、L1-L2 → 中级工、L2-L3 → 高级工、L3-L4 → 技师、L4-L5 → 高级技师；</li>
 *   <li>等级递进解锁：完成当前等级全部必修课程后，下一等级课程才开放学习（晋升前不可学高级课程）；</li>
 *   <li>每个等级一场线上理论考试，时长 90 分钟，考试通过即获得该等级认证（当前无实操考核）。</li>
 * </ul>
 */
@Schema(description = "学员端 - 岗位认证考试资格 Response VO")
@Data
public class ExamEligibilityRespVO {

    @Schema(description = "岗位/工种编码或名称", example = "防腐涂装工")
    private String jobCode;

    @Schema(description = "岗位/工种名称", example = "防腐涂装工")
    private String jobName;

    @Schema(description = "认证等级总数", example = "5")
    private Integer levelTotal;

    @Schema(description = "必修课程总数（每级最多 2 门）", example = "10")
    private Integer courseTotal;

    @Schema(description = "已完成课程数", example = "2")
    private Integer courseCompleted;

    @Schema(description = "在线考试时长（分钟）", example = "90")
    private Integer examDurationMinutes;

    @Schema(description = "当前所处认证等级序号（1-5）", example = "1")
    private Integer currentLevelNo;

    @Schema(description = "认证等级列表（按等级序号升序）")
    private List<LevelItem> levels;

    @Schema(description = "认证等级条目")
    @Data
    public static class LevelItem {

        @Schema(description = "认证等级名称：初级工/中级工/高级工/技师/高级技师", example = "初级工")
        private String level;

        @Schema(description = "认证等级序号（1-5）", example = "1")
        private Integer levelNo;

        @Schema(description = "对应课程等级标识（未标注等级显示为 无）", example = "L1-L2")
        private String courseLevel;

        @Schema(description = "是否已解锁（完成上一等级全部必修课程后解锁）", example = "true")
        private Boolean unlocked;

        @Schema(description = "本等级必修课程数（最多 2 门）", example = "2")
        private Integer courseTotal;

        @Schema(description = "本等级已完成课程数", example = "0")
        private Integer courseCompleted;

        @Schema(description = "是否具备本等级考试资格", example = "false")
        private Boolean eligible;

        @Schema(description = "认证考试名称", example = "防腐涂装工初级工认证考试")
        private String examName;

        @Schema(description = "本等级课程及完成情况")
        private List<ExamCourseItem> courses;
    }

    @Schema(description = "岗位课程条目")
    @Data
    public static class ExamCourseItem {

        @Schema(description = "课程编号", example = "1")
        private Long courseId;

        @Schema(description = "课程编码", example = "B0101")
        private String code;

        @Schema(description = "课程标题")
        private String title;

        @Schema(description = "课程学分", example = "2.00")
        private BigDecimal credit;

        @Schema(description = "学习任务状态：0 待学习 1 学习中 2 已完成", example = "1")
        private Integer status;

        @Schema(description = "课程是否已完成", example = "false")
        private Boolean completed;

        @Schema(description = "学习进度（%）", example = "45.50")
        private BigDecimal progressPercent;

        @Schema(description = "课程完成时间")
        private LocalDateTime completedAt;
    }
}
