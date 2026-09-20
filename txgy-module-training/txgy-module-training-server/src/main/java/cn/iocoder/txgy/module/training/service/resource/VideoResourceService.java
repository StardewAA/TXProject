package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoDefinitionRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.VideoUploadRespVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * 视频资源 Service
 *
 * @author 云培训学院课题小组
 */
public interface VideoResourceService {

    PageResult<VideoResourceDO> getVideoPage(VideoPageReqVO reqVO);

    VideoResourceDO getVideo(Long id);

    /**
     * 上传视频（短视频 / 录播，MP4/MOV ≤4GB），成功即上线
     *
     * <p>异步上传：请求线程只负责接收文件并落临时目录、落库元数据与创建转码任务，
     * 文件归位与 360P/480P/720P 转码均由后台线程池执行，接口立即返回。</p>
     *
     * @param videoType  视频类型：1 短视频 2 录播
     * @param definition 原始清晰度：2160P / 1080P / 720P / 480P / 360P
     */
    VideoUploadRespVO uploadVideo(MultipartFile file, String title, Long categoryId, String coverUrl,
                                  String tags, Integer durationSeconds, Integer videoType, String definition);

    /**
     * 更新视频元数据（含视频类型与原始清晰度）
     */
    void updateVideo(Long id, String title, Long categoryId, String coverUrl,
                     String tags, Integer durationSeconds, Integer videoType, String definition);

    /**
     * 查询可播放的清晰度列表（原始清晰度 + 更低档位，弱网可向下切换）
     */
    List<VideoDefinitionRespVO> getDefinitionOptions(Long id);

    /**
     * 保存转码后的多码率清晰度地址（转码流水线回调 / 管理员维护）
     */
    void saveDefinitionOptions(Long id, List<VideoDefinitionRespVO> options);

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
