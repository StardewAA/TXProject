package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import cn.iocoder.txgy.module.training.enums.course.CourseStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 课程 DO
 *
 * 对应表 training_course，含版本链字段（root_id + version + is_current）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_course")
@KeySequence("training_course_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseDO extends TenantBaseDO {

    @TableId
    private Long id;

    /**
     * 版本链根课程 ID（同族根，新课程创建后回填为自身 id）
     */
    private Long rootId;
    /**
     * 版本号（从 1 递增）
     */
    private Integer version;
    /**
     * 是否当前生效版本
     */
    private Boolean isCurrent;
    /**
     * 课程分类 ID（资源中心 categories 预留）
     */
    private Long categoryId;
    /**
     * 课程编码（审核通过后锁定，不允许修改）
     */
    private String code;
    /**
     * 课程标题
     */
    private String title;
    /**
     * 课程副标题
     */
    private String subtitle;
    /**
     * 课程简介
     */
    private String summary;
    /**
     * 封面图 URL
     */
    private String coverUrl;
    /**
     * 课程级别：1 初级 2 中级 3 高级（保留兼容字段）
     */
    private Integer level;
    /**
     * 成长等级：APPRENTICE 新手 / JUNIOR 成长 / MIDDLE 骨干 / SENIOR 资深
     * （对应资源中心四级成长体系 course_level）
     */
    private String courseLevel;
    /**
     * 课程学分
     */
    private java.math.BigDecimal credit;
    /**
     * 课程总时长（分钟）
     */
    private Integer durationMinutes;
    /**
     * 面向行业
     */
    private String industry;
    /**
     * 适用岗位/工种（JSON 数组字符串，如 ["管道工","防腐工"]）
     */
    private String applicablePosts;
    /**
     * 资源归属标识（对外部资源中心 owner_flag 的快照）
     */
    private String ownerFlag;
    /**
     * 业务标签（JSON 数组字符串，如 ["岗前培训"]）
     */
    private String bizTags;
    /**
     * 是否必修
     */
    private Boolean isRequired;
    /**
     * 是否复合课程（由多个子模块课程组合）
     */
    private Boolean isComposite;
    /**
     * 复合课程子模块标签（JSON 数组字符串，如 ["管道安装","防腐保温"]）
     */
    private String compositeTags;
    /**
     * 关联结课考试试卷 ID（模块三预留）
     */
    private Long examId;
    /**
     * 主讲教师
     */
    private String lecturer;
    /**
     * 讲师简介
     */
    private String lecturerBrief;
    /**
     * 内容类型（由媒资汇总，见 {@link CourseMediaTypeEnum}）
     */
    private Integer mediaType;
    /**
     * 媒资数量（冗余）
     */
    private Integer mediaCount;
    /**
     * 评论数（冗余）
     */
    private Integer commentCount;
    /**
     * 审核次数（冗余）
     */
    private Integer auditCount;
    /**
     * 状态：见 {@link CourseStatusEnum}
     */
    private Integer status;
    /**
     * 最近提交人 ID
     */
    private Long submitterId;
    /**
     * 最近提交时间
     */
    private LocalDateTime submittedAt;
    /**
     * 最近审核完成人 ID
     */
    private Long reviewerId;
    /**
     * 最近审核完成时间
     */
    private LocalDateTime reviewedAt;
    /**
     * 最近驳回原因（驳回必填）
     */
    private String rejectReason;
    /**
     * 最近审核时间
     */
    private LocalDateTime lastAuditTime;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
