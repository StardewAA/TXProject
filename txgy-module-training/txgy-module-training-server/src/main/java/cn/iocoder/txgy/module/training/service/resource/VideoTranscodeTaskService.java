package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.module.training.controller.admin.resource.vo.TranscodeCapabilityRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoTranscodeTaskRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoTranscodeTaskDO;

import java.util.List;

/**
 * 视频转码任务 Service
 *
 * @author 云培训学院课题小组
 */
public interface VideoTranscodeTaskService {

    /**
     * 查询某视频的转码任务列表
     */
    List<VideoTranscodeTaskRespVO> getTaskList(Long videoId);

    /**
     * 上传成功后创建转码任务（按目标清晰度过滤：不升码）
     *
     * @return 创建的任务数
     */
    int createTasks(Long videoId, String sourceDefinition, String sourcePath);

    /**
     * 异步提交转码（含临时文件归位）
     *
     * @param tempPath 临时文件相对路径（为空表示已归位）
     */
    void submitTranscode(Long videoId, String tempPath, Long operatorId, String operatorName);

    /**
     * 重新转码失败/待处理的任务
     */
    void retryTranscode(Long videoId);

    /**
     * 转码能力探测（前端据此提示）
     */
    TranscodeCapabilityRespVO getCapability();

    /**
     * 按清晰度解析可播放文件的相对路径（无转码产物返回 null）
     */
    String resolvePlayPath(Long videoId, String definition);

    /**
     * DO 列表（内部使用）
     */
    List<VideoTranscodeTaskDO> getTaskDOList(Long videoId);

    /**
     * 删除视频时清理转码任务
     */
    void deleteTasksByVideoId(Long videoId);

}
