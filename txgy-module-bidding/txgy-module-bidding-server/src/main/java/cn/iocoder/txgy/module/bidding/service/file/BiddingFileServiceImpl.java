package cn.iocoder.txgy.module.bidding.service.file;

// [规范依据]《招投标模块开发规范》§5 三-5（文件经 FileApi；库内只记 file_id + sha256 + size，URL 非一致性依据）
//   + 红线 8（附件/版式文件三件套）+ §4.7 段位表（101=公告与文件）。
// [业务目标] 修复「上传成功却不返回 file_id」导致的业务断链：
//   平台 `POST /infra/file/upload` 的响应体是 URL 字符串（不含 id），前端拿不到 file_id，
//   业务表（notice.pdf_file_id / notice_external_attachment.file_id）无法关联文件，
//   表单只能提示"需后端支持"甚至要求用户手填 ID。本实现把上传结果补全为 file_id。
// [为什么] file_id 由 infra 侧 `infra_file` 表在落库时生成，唯一可靠的取法是上传后用
//   `FileApi.getMetadataByUrl(url)` 回查元数据；查不到即视为注册失败并**显式抛错**，
//   绝不返回"只有 URL"的成功响应——否则脏数据会一路带到发布/审核环节（宁失败，不脏数据）。

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.file.vo.BiddingFileRespVO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;

@Slf4j
@Service
public class BiddingFileServiceImpl implements BiddingFileService {

    /** 默认归档目录（未显式指定时；按目录归档便于运维按业务清理与备份） */
    private static final String DEFAULT_DIRECTORY = "bidding/attachment";

    @Resource
    private FileApi fileApi;

    @Override
    public BiddingFileRespVO upload(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_UPLOAD_EMPTY);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            // 读流失败属上传链路异常：按注册失败返回明确提示（不吞异常、不返回半成品）
            log.warn("[BiddingFileServiceImpl][upload] 读取上传流失败: {}", e.getMessage());
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_REGISTER_FAILED);
        }
        String name = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "file";
        String type = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        return register(content, name, StringUtils.hasText(directory) ? directory : DEFAULT_DIRECTORY, type, true);
    }

    @Override
    public BiddingFileRespVO registerByUrl(String url) {
        if (!StringUtils.hasText(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_URL_INVALID);
        }
        // 只向 infra 查询元数据（不主动下载该地址），因此不存在 SSRF 面
        // 适配 infra FileApi 契约变更（返回 CommonResult）：错误信封时 getCheckedData 抛 ServiceException；
        // 保留 null 容忍，查不到元数据时仍走下方的"URL 无效"分支
        cn.iocoder.txgy.framework.common.pojo.CommonResult<FileMetadataRespDTO> metaResult = fileApi.getMetadataByUrl(url.trim());
        FileMetadataRespDTO meta = metaResult == null ? null : metaResult.getCheckedData();
        if (meta == null || meta.getId() == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_URL_INVALID);
        }
        BiddingFileRespVO vo = new BiddingFileRespVO();
        vo.setFileId(meta.getId());
        vo.setUrl(StrUtil.blankToDefault(meta.getUrl(), url.trim()));
        vo.setName(meta.getName());
        vo.setType(meta.getType());
        vo.setSize(meta.getSize());
        // sha256 无法由元数据推导（需文件内容）：保留 null 并在此说明，调用方如需三件套请走上传接口
        log.info("[BiddingFileServiceImpl][registerByUrl] 补登记成功 fileId={} url={}", vo.getFileId(), vo.getUrl());
        return vo;
    }

    /**
     * 上传并回查元数据，产出「保证带 fileId」的结果。
     *
     * @param withSha256 是否计算 SHA-256（上传通道=true；按 URL 补登记=true 无内容，故不涉及）
     */
    private BiddingFileRespVO register(byte[] content, String name, String directory, String type, boolean withSha256) {
        String url = fileApi.createFile(content, name, directory, type);
        if (!StringUtils.hasText(url)) {
            log.warn("[BiddingFileServiceImpl][register] 文件服务未返回访问地址 name={}", name);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_REGISTER_FAILED);
        }
        cn.iocoder.txgy.framework.common.pojo.CommonResult<FileMetadataRespDTO> metaResult = fileApi.getMetadataByUrl(url);
        FileMetadataRespDTO meta = metaResult == null ? null : metaResult.getCheckedData();
        if (meta == null || meta.getId() == null) {
            // 用户口径：上传成功但响应体缺失 file_id → 明确报错，禁止回退到"让用户手填 ID"
            log.warn("[BiddingFileServiceImpl][register] 文件已上传但未取到 file_id（元数据缺失）url={}", url);
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_REGISTER_FAILED);
        }
        BiddingFileRespVO vo = new BiddingFileRespVO();
        vo.setFileId(meta.getId());
        vo.setUrl(StrUtil.blankToDefault(meta.getUrl(), url));
        vo.setName(StrUtil.blankToDefault(meta.getName(), name));
        vo.setType(StrUtil.blankToDefault(meta.getType(), type));
        vo.setSize(meta.getSize() != null ? meta.getSize() : (long) content.length);
        vo.setSha256(withSha256 ? sha256Hex(content) : null);
        log.info("[BiddingFileServiceImpl][register] 上传并注册成功 fileId={} size={} url={}", vo.getFileId(), vo.getSize(), vo.getUrl());
        return vo;
    }

    /** SHA-256 十六进制（红线 8 三件套之一；与库内 sha256 列口径一致） */
    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // 摘要算法必然存在；真异常时不让"没有哈希"变成静默成功（红线 8 要求三件套齐备）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_REGISTER_FAILED);
        }
    }

}
