package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * 视频资源 Service
 *
 * @author 云培训学院课题小组
 */
public interface VideoResourceService {

    PageResult<VideoResourceDO> getVideoPage(VideoPageReqVO reqVO);

    VideoResourceDO getVideo(Long id);

    /**
     * 上传视频（MP4/MOV ≤500MB），成功即上线
     */
    Long uploadVideo(MultipartFile file, String title, Long categoryId, String coverUrl,
                     String tags, Integer durationSeconds);

    /**
     * 更新视频元数据
     */
    void updateVideo(Long id, String title, Long categoryId, String coverUrl,
                     String tags, Integer durationSeconds);

    /**
     * 状态流转：DRAFT/ONLINE/OFFLINE/ARCHIVED
     */
    void changeVideoStatus(Long id, String action);

    /**
     * 标记转码成功（易版占位：后续接入转码流水线）
     */
    void markTranscodeSuccess(Long id);

    /**
     * 独立配置学时值（>0，如 0.5/1/1.5）
     */
    void setCreditHours(Long id, BigDecimal creditHours);

    /**
     * 逻辑删除（被引用时禁止删除）
     */
    void deleteVideo(Long id);

}
