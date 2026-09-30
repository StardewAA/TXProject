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
import cn.iocoder.txgy.module.bidding.dal.dataobject.file.BiddingFileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.file.BiddingFileMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.file.BiddingFileSourceEnum;
import cn.iocoder.txgy.module.bidding.enums.file.BiddingFileVirusStatusEnum;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BiddingFileServiceImpl implements BiddingFileService {

    /** 默认归档目录（未显式指定时；按目录归档便于运维按业务清理与备份） */
    private static final String DEFAULT_DIRECTORY = "bidding/attachment";

    /**
     * 存储坐标解析：平台文件地址形如 …/infra/file/{configId}/get/{path}。
     * 只取 configId 与 path（存储坐标），**不保留可访问 url**（红线 8：url 为回显快照、不作一致性依据，且 url 即凭据）。
     */
    private static final Pattern STORAGE_COORD_PATTERN =
            Pattern.compile("/infra/file/(\\d+)/get/(.+)$");

    /** 读取文件的 HTTP 客户端（服务端自取字节，不把 url 交给前端）。 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Resource
    private FileApi fileApi;

    @Resource
    private BiddingFileMapper biddingFileMapper;

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
        registerStorageCoord(meta, BiddingFileSourceEnum.BY_URL, null);
        log.info("[BiddingFileServiceImpl][registerByUrl] 补登记成功 fileId={} url={}", vo.getFileId(), vo.getUrl());
        return vo;
    }

    /**
     * 上传并回查元数据，产出「保证带 fileId」的结果。
     *
     * @param withSha256 是否计算 SHA-256（上传通道=true；按 URL 补登记=true 无内容，故不涉及）
     */
    @Override
    public BiddingFileRespVO registerBytes(byte[] content, String name, String directory, String type) {
        if (content == null || content.length == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_UPLOAD_EMPTY);
        }
        String safeName = StringUtils.hasText(name) ? name : "file";
        String safeDirectory = StringUtils.hasText(directory) ? directory : DEFAULT_DIRECTORY;
        String safeType = StringUtils.hasText(type) ? type : "application/octet-stream";
        return register(content, safeName, safeDirectory, safeType, true);
    }

    private BiddingFileRespVO register(byte[] content, String name, String directory, String type, boolean withSha256) {
        // 【2026-09-16 修复】存储名唯一化：infra 的文件 URL 由「目录 + 日期 + 文件名」拼成，
        // 同名文件当天重复上传会写出多条相同 URL；随后 infra 按 URL 反查元数据用 selectOne，
        // 抛 TooManyResultsException → 上传接口 500（现象：第一次能传，再传同一个文件就失败）。
        // 这里只给「存储名」加时间戳前缀保证 URL 唯一，返回给前端的 name 仍是用户原始文件名。
        String storageName = buildUniqueStorageName(name);
        String url = fileApi.createFile(content, storageName, directory, type);
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
        // 展示名回填原始文件名（存储名带时间戳前缀，不该暴露给用户）
        vo.setName(StringUtils.hasText(name) ? name : StrUtil.blankToDefault(meta.getName(), storageName));
        vo.setType(StrUtil.blankToDefault(meta.getType(), type));
        vo.setSize(meta.getSize() != null ? meta.getSize() : (long) content.length);
        vo.setSha256(withSha256 ? sha256Hex(content) : null);
        // 登记存储坐标（file_id → configId/path）：使 /bidding/file/download 可按 id 取文件，前端不再需要裸 url
        registerStorageCoord(meta, BiddingFileSourceEnum.UPLOAD, vo.getSha256());
        log.info("[BiddingFileServiceImpl][register] 上传并注册成功 fileId={} size={} url={}", vo.getFileId(), vo.getSize(), vo.getUrl());
        return vo;
    }

    // ===================== 文件登记与读取（文件收敛：上传走 upload、取文件走 download）=====================

    @Override
    public BiddingFileDO getByFileId(Long fileId) {
        if (fileId == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_NOT_REGISTERED, "null");
        }
        BiddingFileDO file = biddingFileMapper.selectByFileId(fileId);
        if (file == null) {
            // 未登记 = 该 fileId 不是经本模块上传/补登记的，或历史数据（不猜、不跨库查 infra_file，规范红线 6）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_NOT_REGISTERED, fileId);
        }
        return file;
    }

    @Override
    public byte[] download(Long fileId) {
        BiddingFileDO file = getByFileId(fileId);
        // 病毒口径统一（规范 §5 三-5）：INFECTED 一律拒绝下发。
        // 注：证照文件版本另有 cert_file_versions.virus_status（同名同值域），其登记走本模块上传接口（已登记到本表），
        //     故此处拦截即覆盖证照场景；证照域历史上定义了却从未抛出的 CERT_FILE_VIRUS_NOT_CLEAN 语义由本拦截承接。
        if (!BiddingFileVirusStatusEnum.isDownloadable(file.getVirusStatus())) {
            log.warn("[download] 拒绝下发未通过病毒扫描的文件 fileId={} virusStatus={}", fileId, file.getVirusStatus());
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_VIRUS_NOT_CLEAN);
        }
        if (file.getConfigId() == null || !StringUtils.hasText(file.getPath())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_STORAGE_COORD_INVALID);
        }
        // 服务端自取字节：infra 下载端点 @PermitAll（URL 即凭据），故由后端代取，前端全程拿不到 url
        String downloadUrl = resolveDownloadUrl(file.getConfigId(), file.getPath());
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_CONTENT_READ_FAILED,
                        "HTTP " + response.statusCode());
            }
            return response.body();
        } catch (cn.iocoder.txgy.framework.common.exception.ServiceException e) {
            throw e;   // 业务异常原样上抛（病毒未过/坐标非法等）
        } catch (Exception e) {
            log.warn("[download] 文件内容读取失败 fileId={} err={}", fileId, e.getMessage());
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FILE_CONTENT_READ_FAILED, e.getMessage());
        }
    }

    @Override
    public void registerStorageCoordByFileId(Long fileId, String url, String name, Long size, String virusStatus) {
        if (fileId == null || biddingFileMapper.selectByFileId(fileId) != null) {
            return;   // 幂等：已登记不覆盖（避免把已有坐标刷成空）
        }
        Long configId = null;
        String path = null;
        if (StringUtils.hasText(url)) {
            Matcher matcher = STORAGE_COORD_PATTERN.matcher(url);
            if (matcher.find()) {
                configId = Long.valueOf(matcher.group(1));
                path = matcher.group(2);
            }
        }
        BiddingFileDO file = new BiddingFileDO();
        file.setFileId(fileId);
        file.setConfigId(configId);
        file.setPath(path);
        file.setName(name);
        file.setSize(size);
        file.setVirusStatus(StringUtils.hasText(virusStatus) ? virusStatus : BiddingFileVirusStatusEnum.UNKNOWN.name());
        file.setSource(BiddingFileSourceEnum.SERVER.name());
        biddingFileMapper.insert(file);
        log.info("[registerStorageCoordByFileId] 补登记存储坐标 fileId={} configId={} path={}", fileId, configId, path);
    }

    /** 拼 infra 下载端点地址（仅服务端内部使用，不下发前端）。 */
    private String resolveDownloadUrl(Long configId, String path) {
        // 与 infra FileController 的 @GetMapping("/{configId}/get/**") 对齐；基址取文件服务配置，缺省用本地 infra
        String base = fileServiceBaseUrl();
        return base + "/admin-api/infra/file/" + configId + "/get/" + path;
    }

    /** 文件服务基址（可用 bidding.file.service-base-url 覆盖，缺省 infra 本地端口）。 */
    @org.springframework.beans.factory.annotation.Value("${bidding.file.service-base-url:http://127.0.0.1:48088}")
    private String fileServiceBaseUrl;

    private String fileServiceBaseUrl() {
        return StrUtil.removeSuffix(fileServiceBaseUrl, "/");
    }

    /**
     * 从平台文件地址解析存储坐标并登记（幂等：同 fileId 已登记则跳过）。
     *
     * @param meta 元数据（含 id 与 url）
     * @param source 登记来源
     */
    private void registerStorageCoord(FileMetadataRespDTO meta, BiddingFileSourceEnum source, String sha256) {
        if (meta == null || meta.getId() == null) {
            return;
        }
        if (biddingFileMapper.selectByFileId(meta.getId()) != null) {
            return;
        }
        Long configId = null;
        String path = null;
        if (meta.getUrl() != null) {
            Matcher matcher = STORAGE_COORD_PATTERN.matcher(meta.getUrl());
            if (matcher.find()) {   // 只 find 一次：find 会推进匹配位置，重复调用会错位
                configId = Long.valueOf(matcher.group(1));
                path = matcher.group(2);
            }
        }
        if (configId == null || !StringUtils.hasText(path)) {
            // 非本平台文件服务地址：登记元数据但坐标留空 → 下载时按 FILE_STORAGE_COORD_INVALID 明确报错
            log.warn("[registerStorageCoord] 地址不含存储坐标，仅登记元数据 fileId={} url={}", meta.getId(), meta.getUrl());
        }
        BiddingFileDO file = new BiddingFileDO();
        file.setFileId(meta.getId());
        file.setConfigId(configId);
        file.setPath(path);
        file.setName(meta.getName());
        file.setType(meta.getType());
        file.setSize(meta.getSize());
        file.setSha256(sha256);
        file.setVirusStatus(BiddingFileVirusStatusEnum.UNKNOWN.name());
        file.setSource(source.name());
        biddingFileMapper.insert(file);
    }

    /** 存储名唯一化：yyyyMMddHHmmssSSS-随机4位-原文件名（保证 infra 文件 URL 唯一）。 */
    private static String buildUniqueStorageName(String originalName) {
        String safeName = StrUtil.blankToDefault(originalName, "file");
        String stamp = cn.hutool.core.date.DateUtil.format(new java.util.Date(), "yyyyMMddHHmmssSSS");
        String random = cn.hutool.core.util.RandomUtil.randomNumbers(4);
        return stamp + "-" + random + "-" + safeName;
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
