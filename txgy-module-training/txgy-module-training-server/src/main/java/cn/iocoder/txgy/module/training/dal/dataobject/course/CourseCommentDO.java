package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程评论 DO
 *
 * 对应表 training_course_comment（学员课程详情页评论）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_course_comment")
@KeySequence("training_course_comment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseCommentDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 父评论 ID（0 = 一级评论，预留回复能力）
     */
    private Long parentId;
    /**
     * 评论内容（≤500 字）
     */
    private String content;
    /**
     * 评论用户 ID
     */
    private Long userId;
    /**
     * 评论用户姓名快照
     */
    private String userName;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
