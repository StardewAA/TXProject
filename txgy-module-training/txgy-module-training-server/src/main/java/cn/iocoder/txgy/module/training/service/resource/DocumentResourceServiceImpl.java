package cn.iocoder.txgy.module.training.service.resource;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageCountRespVO;
import cn.iocoder.txgy.module.training.controller.admin.resource.vo.DocumentPageReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.resource.DocumentResourceDO;
import cn.iocoder.txgy.module.training.dal.mysql.resource.DocumentResourceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.ResourceReferenceMapper;
import cn.iocoder.txgy.module.training.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceConstants;
import cn.iocoder.txgy.module.training.enums.resource.ResourceStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档资源 Service 实现
 *
 * @author 云培训学院课题小组
 */
@Slf4j
@Service
public class DocumentResourceServiceImpl implements DocumentResourceService {

    @Resource
    private DocumentResourceMapper documentMapper;
    @Resource
    private ResourceReferenceMapper referenceMapper;
    @Resource
    private ResourceFileStorageService fileStorage;
    @Resource
    private ResourceCategoryService categoryService;
    @Resource
    private ResourcePageCountService pageCountService;
    @Resource
    private OperationLogService operationLogService;

    @Override
    public PageResult<DocumentResourceDO> getDocumentPage(DocumentPageReqVO reqVO) {
        return documentMapper.selectPage(reqVO);
    }

    @Override
    public DocumentResourceDO getDocument(Long id) {
        DocumentResourceDO document = documentMapper.selectById(id);
        if (document == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.DOCUMENT_NOT_EXISTS);
        }
        return document;
    }

    @Override
    public List<Long> uploadDocuments(List<MultipartFile> files, String title, Long categoryId,
                                      String bizCategory, Integer estMinutes) {
        if (files == null || files.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_FILE_EMPTY);
        }
        boolean multiple = files.size() > 1;
        List<Long> ids = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            // 多文件时标题追加序号后缀，避免多条文档记录同名
            String docTitle = multiple ? StrUtil.trim(title) + "(" + (i + 1) + ")" : StrUtil.trim(title);
            ids.add(uploadOne(files.get(i), docTitle, categoryId, bizCategory, estMinutes));
        }
        return ids;
    }

    private Long uploadOne(MultipartFile file, String title, Long categoryId,
                           String bizCategory, Integer estMinutes) {
        // 1. 校验
        String ext = validateFile(file);
        categoryService.validateCategory(categoryId, ResourceConstants.TYPE_DOC);
        // 2. 自动识别文档页数（识别失败兜底 1 页，不阻断上传）
        DocumentPageCountRespVO pageInfo = pageCountService.count(file);
        // 3. 保存文件
        String filePath = fileStorage.store(file, "doc", ext);
        // 4. 落库（上传即上线，便于课程引用与即时预览）
        DocumentResourceDO document = new DocumentResourceDO();
        document.setTitle(title);
        document.setCategoryId(categoryId);
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileType(ext);
        document.setFileSize(file.getSize());
        document.setPageCount(pageInfo.getPageCount());
        document.setBizCategory(bizCategory);
        // 预估阅读时长 = 总页数 × 10 分钟/页；显式传入时以传入值为准
        document.setEstMinutes(estMinutes != null ? estMinutes : pageInfo.getEstMinutes());
        document.setStatus(ResourceStatusEnum.ONLINE.getStatus());
        document.setViewCount(0);
        document.setReadSeconds(0);
        document.setUploaderId(SecurityFrameworkUtils.getLoginUserId());
        document.setUploaderName(SecurityFrameworkUtils.getLoginUserNickname());
        documentMapper.insert(document);
        operationLogService.createLog(ResourceConstants.ACTION_UPLOAD, ResourceConstants.TARGET_DOCUMENT,
                document.getId(), "上传文档《" + title + "》，大小 " + formatSize(file.getSize())
                        + "，识别页数 " + pageInfo.getPageCount() + " 页，预估阅读 "
                        + document.getEstMinutes() + " 分钟");
        return document.getId();
    }

    @Override
    public DocumentPageCountRespVO countDocumentPages(MultipartFile file) {
        validateFile(file);
        return pageCountService.count(file);
    }

    @Override
    public void updateDocument(Long id, String title, Long categoryId,
                               String bizCategory, Integer estMinutes) {
        DocumentResourceDO old = getDocument(id);
        categoryService.validateCategory(categoryId, ResourceConstants.TYPE_DOC);
        DocumentResourceDO update = new DocumentResourceDO();
        update.setId(id);
        update.setTitle(StrUtil.trim(title));
        update.setCategoryId(categoryId);
        update.setBizCategory(bizCategory);
        update.setEstMinutes(estMinutes);
        documentMapper.updateById(update);
        operationLogService.createLog(ResourceConstants.ACTION_UPDATE, ResourceConstants.TARGET_DOCUMENT,
                id, "更新文档《" + old.getTitle() + "》元数据");
    }

    @Override
    public void changeDocumentStatus(Long id, String action) {
        DocumentResourceDO document = getDocument(id);
        int from = document.getStatus();
        ResourceStatusEnum to = checkStatusAction(from, action);
        DocumentResourceDO update = new DocumentResourceDO();
        update.setId(id);
        update.setStatus(to.getStatus());
        documentMapper.updateById(update);
        String actionName = ResourceStatusEnum.fromStatus(to.getStatus()).getName();
        operationLogService.createLog(action, ResourceConstants.TARGET_DOCUMENT, id,
                "文档《" + document.getTitle() + "》状态流转：" + fromName(from) + " → " + actionName);
    }

    @Override
    public void reportReading(Long id, Integer seconds) {
        DocumentResourceDO document = getDocument(id);
        DocumentResourceDO update = new DocumentResourceDO();
        update.setId(id);
        update.setViewCount(document.getViewCount() + 1);
        update.setReadSeconds(document.getReadSeconds() + Math.max(0, seconds == null ? 0 : seconds));
        documentMapper.updateById(update);
        // 阅读上报不写操作日志（高频动作），仅累加统计
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long id) {
        DocumentResourceDO document = getDocument(id);
        Long refCount = referenceMapper.selectCountByResource(ResourceConstants.TARGET_DOCUMENT, id);
        if (refCount != null && refCount > 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.DOCUMENT_REFERENCED_DELETE_FORBIDDEN);
        }
        documentMapper.deleteById(id);
        operationLogService.createLog(ResourceConstants.ACTION_DELETE, ResourceConstants.TARGET_DOCUMENT,
                id, "删除文档《" + document.getTitle() + "》");
    }

    // ========== 私有方法 ==========

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.RESOURCE_FILE_EMPTY);
        }
        String ext = ResourceConstants.getFileExt(file.getOriginalFilename());
        if (!ResourceConstants.DOC_ALLOWED_EXTS.contains(ext)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.DOCUMENT_FILE_TYPE_INVALID);
        }
        if (file.getSize() > ResourceConstants.MAX_DOC_SIZE) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.DOCUMENT_FILE_SIZE_EXCEED);
        }
        return ext;
    }

    private ResourceStatusEnum checkStatusAction(int from, String action) {
        if (ResourceConstants.ACTION_ONLINE.equals(action)) {
            // 草稿或下架后可上线
            if (from == ResourceStatusEnum.DRAFT.getStatus()
                    || from == ResourceStatusEnum.OFFLINE.getStatus()) {
                return ResourceStatusEnum.ONLINE;
            }
        } else if (ResourceConstants.ACTION_OFFLINE.equals(action)) {
            if (from == ResourceStatusEnum.ONLINE.getStatus()) {
                return ResourceStatusEnum.OFFLINE;
            }
        } else if (ResourceConstants.ACTION_ARCHIVE.equals(action)) {
            if (from != ResourceStatusEnum.ARCHIVED.getStatus()
                    && from != ResourceStatusEnum.DRAFT.getStatus()) {
                return ResourceStatusEnum.ARCHIVED;
            }
        } else if (ResourceConstants.ACTION_DRAFT.equals(action)) {
            // 归档撤销，恢复为草稿
            if (from == ResourceStatusEnum.ARCHIVED.getStatus()) {
                return ResourceStatusEnum.DRAFT;
            }
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.DOCUMENT_STATUS_CHANGE_INVALID);
    }

    private String fromName(int status) {
        ResourceStatusEnum e = ResourceStatusEnum.fromStatus(status);
        return e == null ? String.valueOf(status) : e.getName();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        }
        return String.format("%.1fMB", bytes / 1024.0 / 1024.0);
    }

}
