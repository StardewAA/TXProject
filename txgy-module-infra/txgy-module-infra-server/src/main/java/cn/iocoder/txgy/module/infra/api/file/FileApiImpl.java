package cn.iocoder.txgy.module.infra.api.file;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.infra.api.file.dto.FileBindReqDTO;
import cn.iocoder.txgy.module.infra.api.file.dto.FileCreateReqDTO;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import cn.iocoder.txgy.module.infra.api.file.dto.FileOssPromoteRetryRespDTO;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.infra.service.file.FileService;
import cn.iocoder.txgy.module.infra.service.file.OssPromoteRetryResult;
import lombok.SneakyThrows;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public CommonResult<String> createFile(FileCreateReqDTO createReqDTO) {
        return success(fileService.createFile(createReqDTO.getContent(), createReqDTO.getName(),
                createReqDTO.getDirectory(), createReqDTO.getType()));
    }

    @Override
    public CommonResult<String> presignGetUrl(String url, Integer expirationSeconds) {
        return success(fileService.presignGetUrl(url, expirationSeconds));
    }

    @Override
    public CommonResult<FileMetadataRespDTO> getMetadataByUrl(String url) {
        return success(BeanUtils.toBean(fileService.getFileByUrl(url), FileMetadataRespDTO.class));
    }

    @Override
    @SneakyThrows
    public CommonResult<byte[]> getFileContentByUrl(String url) {
        return success(fileService.getFileContentByUrl(url));
    }

    @Override
    public CommonResult<Integer> bindFile(FileBindReqDTO reqDTO) {
        return success(fileService.bindFiles(reqDTO));
    }

    @Override
    public CommonResult<FileOssPromoteRetryRespDTO> retryOssPromotion(Long fileId, int limit) {
        OssPromoteRetryResult result = fileId != null
                ? fileService.retryOssPromotion(fileId)
                : fileService.retryPublicOssPromotion(limit);
        return success(BeanUtils.toBean(result, FileOssPromoteRetryRespDTO.class));
    }

}
