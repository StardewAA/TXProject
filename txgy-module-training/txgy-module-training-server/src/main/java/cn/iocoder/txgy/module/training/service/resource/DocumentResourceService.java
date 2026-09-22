package cn.iocoder.txgy.module.training.service.resource;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageCountRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.DocumentResourceDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档资源 Service
 *
 * @author 云培训学院课题小组
 */
public interface DocumentResourceService {

    PageResult<DocumentResourceDO> getDocumentPage(DocumentPageReqVO reqVO);

    DocumentResourceDO getDocument(Long id);

    /**
     * 识别文档页数，并按 总页数 × 10 分钟/页 换算预估阅读时长
     */
    DocumentPageCountRespVO countDocumentPages(MultipartFile file);

    /**
     * 批量上传文档（PDF/Word/WPS/图片 ≤50MB/个），成功即上线，返回资源 ID 列表
     *
     * <p>支持一次性上传多个文件：每个文件独立识别页数与预估阅读时长；
     * 多文件时标题自动追加序号后缀（如《作业规程》(1)、《作业规程》(2)）</p>
     */
    List<Long> uploadDocuments(List<MultipartFile> files, String title, Long categoryId,
                               String bizCategory, Integer estMinutes);

    /**
     * 更新文档元数据（标题/分类/业务分类/预估阅读）
     */
    void updateDocument(Long id, String title, Long categoryId,
                        String bizCategory, Integer estMinutes);

    /**
     * 状态流转：DRAFT 草稿 / ONLINE 上线 / OFFLINE 下架 / ARCHIVED 归档
     */
    void changeDocumentStatus(Long id, String action);

    /**
     * 阅读上报：预览次数 +1、停留秒数累加
     */
    void reportReading(Long id, Integer seconds);

    /**
     * 逻辑删除（被引用时禁止删除）
     */
    void deleteDocument(Long id);

}
