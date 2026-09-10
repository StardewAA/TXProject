package cn.iocoder.txgy.module.training.dal.dataobject.course;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.txgy.module.training.enums.course.CourseMediaTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程媒资 DO
 *
 * 对应表 training_course_media（文档/视频/图片绑定，资源中心媒资预留）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_course_media")
@KeySequence("training_course_media_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseMediaDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 课程 ID
     */
    private Long courseId;
    /**
     * 媒资类型：见 {@link CourseMediaTypeEnum}
     */
    private Integer mediaType;
    /**
     * 媒资名称
     */
    private String name;
    /**
     * 访问地址
     */
    private String url;
    /**
     * 外部资源引用 ID（对接资源中心媒资 ID / VR 项目编号；自含媒资时为 NULL，占位）
     */
    private String mediaRefId;
    /**
     * 文件大小（字节）
     */
    private Long sizeBytes;
    /**
     * 视频时长（秒）
     */
    private Integer durationSeconds;
    /**
     * 排序
     */
    private Integer sort;

    /**
     * 逻辑删除标记（框架逻辑删除）
     */
    @TableLogic
    private Boolean deleted;

}
