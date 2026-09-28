package cn.iocoder.txgy.module.training.dal.dataobject.resource;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频转码任务 DO
 *
 * <p>对应表 training_video_transcode_task：上传成功后按目标清晰度（360P/480P/720P）
 * 生成转码任务，由后台线程池异步执行，前端可轮询进度。</p>
 *
 * @author 云培训学院课题小组
 */
@TableName("training_video_transcode_task")
@KeySequence("training_video_transcode_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class VideoTranscodeTaskDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 视频资源 ID
     */
    private Long videoId;
    /**
     * 目标清晰度：2160P / 1080P / 720P / 480P / 360P
     */
    private String definition;
    /**
     * 转码输出高度（像素），如 720
     */
    private Integer height;
    /**
     * 任务状态：0 待转码 1 转码中 2 成功 3 失败 4 跳过
     */
    private Integer status;
    /**
     * 进度百分比（0-100）
     */
    private Integer progress;
    /**
     * 源文件相对路径
     */
    private String sourcePath;
    /**
     * 输出文件相对路径（成功时写入）
     */
    private String outputPath;
    /**
     * 输出文件大小（字节）
     */
    private Long fileSize;
    /**
     * ffmpeg 执行命令（排障用）
     */
    private String command;
    /**
     * 失败原因
     */
    private String errorMsg;
    /**
     * 重试次数
     */
    private Integer retryCount;
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    @TableLogic
    private Boolean deleted;

}
