package cn.iocoder.txgy.module.hrm.service.common;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.HRM_RESUME_FILE_SIZE_EXCEEDED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.HRM_RESUME_FILE_TYPE_INVALID;

/**
 * 简历附件校验器。
 *
 * <p>前端已经在选择文件时限制「pdf/doc/docx + 10MB」，但接口可被直接调用，
 * 因此在服务端对简历附件的扩展名与大小做二次校验。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmResumeFileValidator {

    /** 允许的简历附件扩展名 */
    private static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "doc", "docx");

    /** 简历附件大小上限：10MB */
    private static final long MAX_RESUME_SIZE = 10L * 1024 * 1024;

    @Resource
    private FileApi fileApi;

    /**
     * 校验简历附件。校验不通过时抛出业务异常。
     *
     * @param fileName 附件名称
     * @param fileUrl  附件访问地址
     */
    public void validate(String fileName, String fileUrl) {
        // 1. 扩展名校验
        String extension = StrUtil.subAfter(StrUtil.blankToDefault(fileName, fileUrl), ".", true);
        if (StrUtil.isBlank(extension) || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw exception(HRM_RESUME_FILE_TYPE_INVALID);
        }
        // 2. 大小校验：取不到元数据时跳过（例如外部直链），由前端与存储层兜底
        if (StrUtil.isBlank(fileUrl) || !fileUrl.startsWith("http")) {
            return;
        }
        FileMetadataRespDTO metadata = null;
        try {
            metadata = fileApi.getMetadataByUrl(fileUrl).getCheckedData();
        } catch (Exception ex) {
            // 文件元数据不可用时不影响主流程，仅记录日志
            log.warn("[validate][文件({}) 元数据获取失败，跳过大小校验]", fileUrl, ex);
        }
        if (metadata != null && metadata.getSize() != null && metadata.getSize() > MAX_RESUME_SIZE) {
            throw exception(HRM_RESUME_FILE_SIZE_EXCEEDED);
        }
    }

}
