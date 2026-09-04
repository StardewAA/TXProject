package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 视频资源 DO
 *
 * 对应表 training_video_resource（MP4/MOV ≤500MB，Range 播放、学时独立配置）
 *
 * @author 云培训学院课题小组
 */
@TableName("training_video_resource")
@KeySequence("training_video_resource_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoResourceDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 视频标题
     */
    private String title;
    /**
     * 所属分类 ID
     */
    private Long categoryId;
    /**
     * 封面图 URL（可选）
     */
    private String coverUrl;
    /**
     * 原始文件名
     */
    private String fileName;
    /**
     * 文件相对存储路径
     */
    private String filePath;
    /**
     * 文件类型：mp4 / mov
     */
    private String fileType;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 视频时长（秒）
     */
    private Integer durationSeconds;
    /**
     * 转码状态：0 未转码 1 排队 2 处理中 3 成功 4 失败
     */
    private Integer transcodeStatus;
    /**
     * 学时值（独立配置，示例 0.5 / 1 / 1.5）
     */
    private BigDecimal creditHours;
    /**
     * 标签（逗号分隔）
     */
    private String tags;
    /**
     * 状态：1 草稿 2 上线 3 下架 4 归档
     */
    private Integer status;
    /**
     * 播放次数
     */
    private Integer viewCount;
    /**
     * 上传人 ID
     */
    private Long uploaderId;
    /**
     * 上传人姓名快照
     */
    private String uploaderName;

    @TableLogic
    private Boolean deleted;

}
