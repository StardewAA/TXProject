package cn.iocoder.txgy.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.http.HttpUtils;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.LoginUser;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.infra.api.file.dto.FileBindReqDTO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileUploadTicketDO;
import cn.iocoder.txgy.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.txgy.module.infra.dal.mysql.file.FileUploadTicketMapper;
import cn.iocoder.txgy.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.txgy.module.infra.enums.file.FileAccessTypeEnum;
import cn.iocoder.txgy.module.infra.enums.file.FileBindStatusEnum;
import cn.iocoder.txgy.module.infra.enums.file.FileBizTypeEnum;
import cn.iocoder.txgy.module.infra.framework.file.core.enums.FileReadAccessEnum;
import cn.iocoder.txgy.module.infra.framework.file.core.utils.FilePathUtils;
import cn.iocoder.txgy.module.infra.framework.file.core.utils.FileTypeUtils;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_BIND_BIZ_CONFLICT;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_BIND_OWNER_MISMATCH;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_BIND_OWNER_UNKNOWN;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_BIND_TENANT_MISMATCH;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_REGISTER_NO_TICKET;
import static cn.iocoder.txgy.module.infra.enums.ErrorCodeConstants.FILE_REGISTER_OWNER_CONFLICT;

/**
 * 文件 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    /**
     * 上传文件的前缀，是否包含日期（yyyyMMdd）
     *
     * 目的：按照日期，进行分目录
     */
    static boolean PATH_PREFIX_DATE_ENABLE = true;
    /**
     * 上传文件的后缀，是否启用
     *
     * 算法：当前时间戳（毫秒）+ 5 位随机数；目的是保证文件的唯一性，避免覆盖
     * 定制：可按需调整成 UUID、或者其他方式
     *
     * 安全说明（2026-09-24 开启）：
     * 关闭时，同一天同名文件会生成**相同 path**，后一次上传会直接覆盖前一次的对象，
     * 既造成数据相互覆盖，又会让 (configId, path) 出现「一址多记录」的归属歧义。
     * 开启后每次上传 path 唯一，从根本上消除「同路径覆盖 / 后插记录遮蔽原归属」。
     */
    static boolean PATH_SUFFIX_TIMESTAMP_ENABLE = true;
    /**
     * 后缀是否作为上级目录
     *
     * true：{@code yyyyMMdd/<后缀>/原文件名.ext}；保留原文件名
     * false：{@code yyyyMMdd/原文件名_<后缀>.ext}；后缀拼到文件名
     */
    static boolean PATH_SUFFIX_AS_DIRECTORY = true;

    /**
     * 拥有文件管理权限的后台用户标识（用于放行后台运维查看）
     */
    private static final String PERMISSION_FILE_QUERY = "infra:file:query";

    /** 从文件 URL 提取存储配置编号：/infra/file/{configId}/get/{path} */
    private static final java.util.regex.Pattern INFRA_FILE_URL_CONFIG_PATTERN =
            java.util.regex.Pattern.compile("/infra/file/(\\d+)/");

    @Resource
    private FileConfigService fileConfigService;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private FileUploadTicketMapper fileUploadTicketMapper;

    @Resource
    private SecurityFrameworkService securityFrameworkService;

    /**
     * 待绑定文件对上传者本人的可读窗口（分钟），同时也是孤儿清理的最早时间点。
     *
     * 默认 24 小时：新上传（尚未回绑业务）的文件在窗口内一定可读；窗口过后且**未被任何业务
     * 单据引用**才会进入孤儿清理候选。业务提交时会通过 FileApi.bindFile 立即回绑，正常情况
     * 秒级即转为 BOUND/PUBLIC，不再受该窗口影响。
     */
    @Value("${txgy.file.access.unbound-window-minutes:1440}")
    private long unboundWindowMinutes = 1440;

    /**
     * 预签名上传凭证的有效期（分钟），用于前端直传（模式二）的登记校验
     */
    @Value("${txgy.file.access.upload-ticket-minutes:30}")
    private long uploadTicketMinutes = 30;

    /**
     * 公开资源的**目标**存储配置编号（0 = 关闭 OSS 提升，公开资源继续走 master 的网关读取）。
     *
     * <b>OSS 提升的准确含义（安全关键）</b>：
     * 文件**上传时一律落到 master**，绝不由客户端传入的 directory 决定存储位置 ——
     * 否则客户端只要传 `directory=mall/product`，就能把本应私有的凭证图直接写进公开 OSS，
     * 等于用一个请求参数绕过整套访问控制。
     *
     * 只有在**服务端绑定确认**（{@code bindFile} 且 accessType=PUBLIC、业务类型允许公开）
     * 之后，才把内容复制到该配置并把 url 改写成 OSS 直连地址。
     * 未被确认公开的（含「待绑定」草稿与孤儿文件）永远留在 master，由网关按鉴权返回。
     *
     * 这也不等于迁移历史图片：存量文件留在原存储，历史迁移另行处理。
     * master 保持不变（ID 4）。
     */
    @Value("${txgy.file.public-oss-config-id:0}")
    private long publicOssConfigId = 0;

    @Override
    public PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO) {
        return fileMapper.selectPage(pageReqVO);
    }

    @Override
    @SneakyThrows
    public String createFile(byte[] content, String name, String directory, String type) {
        // 1.1 处理 name 的合法性，禁止携带目录路径
        name = FilePathUtils.validateFileName(name);

        // 1.2.1 处理 type 为空的情况
        if (StrUtil.isEmpty(type)) {
            type = FileTypeUtils.getMineType(content, name);
        }
        // 1.2.2 处理 name 为空的情况
        if (StrUtil.isEmpty(name)) {
            name = DigestUtil.sha256Hex(content);
        }
        if (StrUtil.isEmpty(FileUtil.extName(name))) {
            // 如果 name 没有后缀 type，则补充后缀
            String extension = FileTypeUtils.getExtension(type);
            if (StrUtil.isNotEmpty(extension)) {
                name = name + extension;
            }
        }

        // 2.1 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, directory);
        // 2.2 上传到文件存储器：**一律落到 master**。
        //     刻意不按 directory 分流：directory 是客户端传参，用它决定存储位置等于
        //     让客户端自选"是否存到公开 OSS"，会直接绕过访问控制。
        //     公开存储只在服务端绑定确认后由 promoteToPublicOss 完成。
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        String url = client.upload(content, path, type);

        // 3. 保存到数据库（访问控制：默认私有 + 待绑定）
        fileMapper.insert(buildNewFileDO(client.getId(), name, path, url, type, (long) content.length, directory));
        return url;
    }

    /**
     * 构造新上传文件的记录（含访问控制元数据）
     *
     * 安全约定：上传结果**一律先落为私有 + 待绑定**，公开必须由服务端后续绑定确认。
     */
    private FileDO buildNewFileDO(Long configId, String name, String path, String url, String type,
                                  Long size, String directory) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return new FileDO().setConfigId(configId)
                .setName(name).setPath(path).setUrl(url).setType(type).setSize(size)
                // 归属信息全部取自可信登录上下文，不接受客户端传入
                .setTenantId(TenantContextHolder.getTenantId())
                .setOwnerUserType(loginUser != null ? loginUser.getUserType() : null)
                .setOwnerUserId(loginUser != null ? loginUser.getId() : null)
                // 业务类型仅做服务端归一化，不产生授权效果
                .setBizType(FileBizTypeEnum.getByDirectory(directory).name())
                .setAccessType(FileAccessTypeEnum.PRIVATE.getAccessType())
                .setBindStatus(FileBindStatusEnum.UNBOUND.getStatus())
                .setBindExpireTime(LocalDateTime.now().plusMinutes(unboundWindowMinutes));
    }

    @VisibleForTesting
    String generateUploadPath(String name, String directory) {
        // 1.1 处理 name 和 directory 的合法性
        name = FilePathUtils.validateFileName(name);
        FilePathUtils.validatePath(name);
        FilePathUtils.validateDirectory(directory);
        // 1.2 生成前缀、后缀
        String prefix = null;
        if (PATH_PREFIX_DATE_ENABLE) {
            prefix = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), PURE_DATE_PATTERN);
        }
        String suffix = null;
        if (PATH_SUFFIX_TIMESTAMP_ENABLE) {
            // 5 位随机数，避免同一毫秒内的重复
            suffix = String.valueOf(System.currentTimeMillis()) + RandomUtil.randomInt(10000, 100000);
        }

        // 2.1 先拼接 suffix 后缀
        if (StrUtil.isNotEmpty(suffix)) {
            if (PATH_SUFFIX_AS_DIRECTORY) {
                name = suffix + StrUtil.SLASH + name;
            } else {
                String ext = FileUtil.extName(name);
                if (StrUtil.isNotEmpty(ext)) {
                    name = FileUtil.mainName(name) + StrUtil.C_UNDERLINE + suffix + StrUtil.DOT + ext;
                } else {
                    name = name + StrUtil.C_UNDERLINE + suffix;
                }
            }
        }
        // 2.2 再拼接 prefix 前缀
        if (StrUtil.isNotEmpty(prefix)) {
            name = prefix + StrUtil.SLASH + name;
        }
        // 2.3 最后拼接 directory 目录
        if (StrUtil.isNotEmpty(directory)) {
            name = directory + StrUtil.SLASH + name;
        }
        return name;
    }

    @Override
    @SneakyThrows
    public FilePresignedUrlRespVO presignPutUrl(String name, String directory) {
        // 1. 生成上传的 path，需要保证唯一
        String path = generateUploadPath(name, directory);

        // 2. 获取文件预签名地址：同样固定 master，与 createFile 的落地存储保持一致
        //    （上传凭证也按 master 的 configId 签发）。
        FileClient fileClient = fileConfigService.getMasterFileClient();
        String uploadUrl = fileClient.presignPutUrl(path);
        String visitUrl = fileClient.presignGetUrl(path, null);

        // 3. 为当前登录身份签发上传凭证：后续 /file/create 必须消费该凭证才允许登记，
        //    从而杜绝「知道他人 path 就重新登记冒领」。
        issueUploadTicket(fileClient.getId(), path);

        return new FilePresignedUrlRespVO().setConfigId(fileClient.getId())
                .setPath(path).setUploadUrl(uploadUrl).setUrl(visitUrl);
    }

    /**
     * 签发上传凭证（记录服务端为该登录身份生成了该 path）
     */
    private void issueUploadTicket(Long configId, String path) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            // 未登录不签发：/file/create 将因缺少凭证而拒绝登记
            log.warn("[issueUploadTicket][未登录身份，跳过签发(path={})]", path);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // 清理该 key 的历史凭证 + 全局过期凭证，保持表小
        fileUploadTicketMapper.deleteByKey(configId, path, loginUser.getUserType(), loginUser.getId());
        fileUploadTicketMapper.deleteExpired(now);
        fileUploadTicketMapper.insert(FileUploadTicketDO.builder()
                .configId(configId).path(path)
                .tenantId(TenantContextHolder.getTenantId())
                .ownerUserType(loginUser.getUserType())
                .ownerUserId(loginUser.getId())
                .expireTime(now.plusMinutes(uploadTicketMinutes))
                .build());
    }

    /**
     * 消费上传凭证：仅当存在「本人 + 同 configId + 同 path」的有效凭证时返回 true，并删除该凭证
     */
    private boolean consumeUploadTicket(Long configId, String path) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            return false;
        }
        FileUploadTicketDO ticket = fileUploadTicketMapper.selectValid(configId, path,
                loginUser.getUserType(), loginUser.getId(), LocalDateTime.now());
        if (ticket == null) {
            return false;
        }
        fileUploadTicketMapper.deleteById(ticket.getId());
        return true;
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        // 按 URL 中携带的 configId 解析 client，而不是想当然用 master ——
        // 公开口径的文件已分流到 OSS（其 URL 是直连域名、不含 configId），这类地址本就可直接访问。
        FileClient fileClient = resolveClientForUrl(url);
        try {
            String presignedUrl = fileClient.presignGetUrl(url, expirationSeconds);
            return StrUtil.blankToDefault(presignedUrl, url);
        } catch (UnsupportedOperationException ex) {
            // 部分存储实现（例如数据库存储）不支持预签名，此时原地址本身即可直接访问，
            // 直接降级返回原地址，避免简历预览/下载等只读场景整体失败。
            log.warn("[presignGetUrl][存储({}) 不支持预签名，降级返回原地址({})]",
                    fileClient.getClass().getSimpleName(), url);
            return url;
        }
    }

    /**
     * 把**服务端已确认公开**的文件内容提升到公开 OSS，返回 {@code [configId, url]}。
     *
     * 前置条件（由调用方保证）：文件归属已核验、业务类型允许公开、且本次绑定确认为 PUBLIC。
     *
     * 返回 null 表示"不提升"，此时文件继续留在 master —— 这仍然是**安全**的：
     * 公开资源经网关读取时本就允许匿名访问，只是少了 CDN/直连的收益。
     * 因此提升失败一律降级处理，绝不阻断业务事务。
     *
     * 注意：master 上的副本保留（不做删除），避免复制中途失败导致两端都没有内容。
     */
    private String[] promoteToPublicOss(FileDO file) {
        if (publicOssConfigId <= 0) {
            return null;
        }
        try {
            FileClient master = fileConfigService.getMasterFileClient();
            FileClient oss = fileConfigService.getFileClient(publicOssConfigId);
            if (master == null || oss == null || oss.getId() == null) {
                return null;
            }
            if (oss.getId().equals(file.getConfigId())) {
                return null; // 已在公开存储
            }
            if (file.getConfigId() == null || !file.getConfigId().equals(master.getId())) {
                // 文件不在 master（历史数据或其它存储）：不做跨存储搬运，保持原状
                return null;
            }
            byte[] content = master.getContent(file.getPath());
            if (content == null || content.length == 0) {
                log.warn("[promoteToPublicOss][文件({}) 在 master 上无内容，跳过提升]", file.getId());
                return null;
            }
            String url = oss.upload(content, file.getPath(), file.getType());
            if (StrUtil.isEmpty(url)) {
                return null;
            }
            // 提升成功：标记 promote_status=2，供运维/定时任务识别为"已发布到公开桶"
            fileMapper.updateById(new FileDO().setId(file.getId()).setPromoteStatus(2));
            log.info("[promoteToPublicOss][文件({}) 已提升到公开存储({})：{}]", file.getId(), oss.getId(), url);
            return new String[]{String.valueOf(oss.getId()), url};
        } catch (Exception ex) {
            // 提升失败：记录失败次数 + 末次错误 + promote_status=3，保留 master（仍经网关匿名可读）。
            // 不抛异常 —— 后续可由重试端点 / 定时任务安全补传。
            int failCount = (file.getPromoteFailCount() == null ? 0 : file.getPromoteFailCount()) + 1;
            fileMapper.updateById(new FileDO().setId(file.getId())
                    .setPromoteStatus(3)
                    .setPromoteFailCount(failCount)
                    .setPromoteLastError(StrUtil.subPre(ex.getMessage(), 500)));
            log.warn("[promoteToPublicOss][文件({}) 提升到公开存储({}) 失败（第{}次），保留 master：{}]",
                    file.getId(), publicOssConfigId, failCount, ex.getMessage());
            return null;
        }
    }

    /**
     * 文件是否已成功发布到公开 OSS（用于幂等判断，避免重复搬运/重复计数）
     */
    private boolean isPromotedToOss(FileDO file) {
        return file.getConfigId() != null && file.getConfigId().equals(publicOssConfigId)
                && (file.getPromoteStatus() == null || file.getPromoteStatus() == 2);
    }

    /**
     * 从文件 URL 解析对应的存储 client：命中 {@code /infra/file/{configId}/...} 用该配置；
     * 解析不出（如 OSS 直连域名）或解析异常时回退 master。
     */
    private FileClient resolveClientForUrl(String url) {
        FileClient master = fileConfigService.getMasterFileClient();
        Assert.notNull(master, "客户端(master) 不能为空");
        if (StrUtil.isEmpty(url)) {
            return master;
        }
        java.util.regex.Matcher matcher = INFRA_FILE_URL_CONFIG_PATTERN.matcher(url);
        if (!matcher.find()) {
            return master;
        }
        try {
            FileClient client = fileConfigService.getFileClient(Long.valueOf(matcher.group(1)));
            return client != null ? client : master;
        } catch (Exception ex) {
            log.warn("[resolveClientForUrl][按 URL({}) 解析存储配置失败，回退 master：{}]", url, ex.getMessage());
            return master;
        }
    }

    @Override
    public Long createFile(FileCreateReqVO createReqVO) {
        // 1. 校验参数的合法性
        FilePathUtils.validatePath(createReqVO.getPath());
        createReqVO.setName(FilePathUtils.validateFileName(createReqVO.getName()));
        // 移除 URL 中的查询参数（例如签名参数），保证 URL 的唯一性
        createReqVO.setUrl(HttpUtils.removeUrlQuery(createReqVO.getUrl()));

        Long configId = createReqVO.getConfigId();
        String path = createReqVO.getPath();
        // 登记 = 「认领上传者」的动作，必须基于可信登录身份；tenant-id 头不能代替登录
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw exception(FILE_REGISTER_NO_TICKET);
        }

        // 2. 该 (configId, path) 已有存活记录时：
        //    2.1 同一归属 → 幂等更新元数据（**不新增记录**，避免读取时被后插入的记录遮蔽）
        //    2.2 不同归属 → 拒绝（防止知道他人 path 就重新登记来冒领）
        FileDO exists = fileMapper.selectFirstByConfigIdAndPath(configId, path);
        if (exists != null) {
            if (!isSameOwner(exists, loginUser)) {
                log.warn("[createFile][拒绝冒领：configId({}) path({}) 已归属(类型={},编号={})，登记人(类型={},编号={})]",
                        configId, path, exists.getOwnerUserType(), exists.getOwnerUserId(),
                        loginUser.getUserType(), loginUser.getId());
                throw exception(FILE_REGISTER_OWNER_CONFLICT);
            }
            fileMapper.updateById(new FileDO().setId(exists.getId())
                    .setName(createReqVO.getName()).setUrl(createReqVO.getUrl())
                    .setType(createReqVO.getType()).setSize(createReqVO.getSize()));
            return exists.getId();
        }

        // 3. 全新登记：必须持有服务端为该登录身份签发的上传凭证（presigned-url 时下发）
        if (!consumeUploadTicket(configId, path)) {
            log.warn("[createFile][拒绝登记：configId({}) path({}) 无有效上传凭证]", configId, path);
            throw exception(FILE_REGISTER_NO_TICKET);
        }

        // 4. 保存到数据库（模式二：前端直传后回登记，同样按私有 + 待绑定落库）
        FileDO file = BeanUtils.toBean(createReqVO, FileDO.class);
        file.setTenantId(TenantContextHolder.getTenantId())
                .setOwnerUserType(loginUser.getUserType())
                .setOwnerUserId(loginUser.getId())
                .setAccessType(FileAccessTypeEnum.PRIVATE.getAccessType())
                .setBindStatus(FileBindStatusEnum.UNBOUND.getStatus())
                .setBindExpireTime(LocalDateTime.now().plusMinutes(unboundWindowMinutes));
        if (StrUtil.isEmpty(file.getBizType())) {
            // 仅做服务端归一化，不产生授权效果
            file.setBizType(FileBizTypeEnum.getByDirectory(path).name());
        }
        fileMapper.insert(file);
        return file.getId();
    }

    @Override
    public FileDO getFile(Long id) {
        return validateFileExists(id);
    }

    @Override
    public FileDO getFileByUrl(String url) {
        // 同一个 URL 可能因为重复上传而存在多条文件记录，此处取最新一条，
        // 避免 selectOne 抛 TooManyResultsException 导致按 URL 查元数据整体失败。
        List<FileDO> files = fileMapper.selectList(FileDO::getUrl, HttpUtils.removeUrlQuery(url));
        return CollUtil.isEmpty(files) ? null
                : files.stream().max(Comparator.comparing(FileDO::getId)).orElse(null);
    }

    @Override
    public void deleteFile(Long id) throws Exception {
        // 1.1 校验存在
        FileDO file = validateFileExists(id);
        // 1.2 校验路径合法性，避免误删文件存储器中的其他文件
        FilePathUtils.validatePath(file.getPath());

        // 2.1 从文件存储器中删除
        FileClient client = fileConfigService.getFileClient(file.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", file.getConfigId());
        client.delete(file.getPath());

        // 2.2 删除记录
        fileMapper.deleteById(id);
    }

    @Override
    @SneakyThrows
    public void deleteFileList(List<Long> ids) {
        // 删除文件
        List<FileDO> files = fileMapper.selectByIds(ids);
        for (FileDO file : files) {
            FilePathUtils.validatePath(file.getPath());
            // 获取客户端
            FileClient client = fileConfigService.getFileClient(file.getConfigId());
            Assert.notNull(client, "客户端({}) 不能为空", file.getPath());
            // 删除文件
            client.delete(file.getPath());
        }

        // 删除记录
        fileMapper.deleteByIds(ids);
    }

    private FileDO validateFileExists(Long id) {
        FileDO fileDO = fileMapper.selectById(id);
        if (fileDO == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        return fileDO;
    }

    @Override
    public byte[] getFileContent(Long configId, String path) throws Exception {
        // 1. 校验路径合法性
        FilePathUtils.validatePath(path);

        // 2.1 获取客户端
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);
        // 2.2 获取文件内容
        return client.getContent(path);
    }

    @Override
    public FileDO getFileByConfigIdAndPath(Long configId, String path) {
        return fileMapper.selectFirstByConfigIdAndPath(configId, path);
    }

    @Override
    @SneakyThrows
    public byte[] getFileContentByUrl(String url) throws Exception {
        // 1. 按访问地址获得文件元数据
        FileDO file = getFileByUrl(url);
        if (file == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        // 2. 读取文件内容
        return getFileContent(file.getConfigId(), file.getPath());
    }

    // ==================== 访问控制 ====================

    /**
     * 解析待读取的文件记录。
     *
     * 先按 (configId, path) 精确定位（锚定最早记录、避免后插记录遮蔽原归属）；
     * 若查不到，再用 path 兜底反查——用于「服务端绑定确认后提升到公开 OSS」的场景：
     * 文件 config_id 已变为 OSS 配置，但业务实体仍持有上传期返回的网关 URL（内嵌原 master config_id）。
     * 兜底命中后，后续内容读取走文件**真实**的 config_id（OSS 配置），从而把公开资源真正从 OSS 取回，
     * 而不是因为 config_id 不匹配而解析失败、导致页面图片 404。
     */
    private FileDO resolveFileForRead(Long configId, String path) {
        FileDO file = fileMapper.selectFirstByConfigIdAndPath(configId, path);
        if (file != null) {
            return file;
        }
        return fileMapper.selectFirstByPath(path);
    }

    @Override
    public FileReadAccessEnum checkReadAccess(Long configId, String path) {
        // 读取判定锚定「最早记录」（原始登记者），避免后插记录遮蔽原归属
        FileDO file = resolveFileForRead(configId, path);
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();

        // 1. 无文件记录：无法确认归属 → 拒绝（匿名引导登录，已登录直接拒绝）
        if (file == null) {
            return loginUser == null ? FileReadAccessEnum.NEED_LOGIN : FileReadAccessEnum.FORBIDDEN;
        }

        // 2. 公开资源：匿名可读
        if (isPublicResource(file)) {
            return FileReadAccessEnum.ALLOW;
        }

        // 3. 私有资源：必须登录
        if (loginUser == null) {
            return FileReadAccessEnum.NEED_LOGIN;
        }

        // 4. 租户边界：**必须先于任何特权放行**（含后台 infra:file:query）。
        //    文件带明确租户归属时，仅该租户下的身份可继续判定；否则一律拒绝。
        //    这样「其他租户的后台用户即使拥有 infra:file:query」也无法越租户读取。
        if (file.getTenantId() != null && !file.getTenantId().equals(loginUser.getTenantId())) {
            return FileReadAccessEnum.FORBIDDEN;
        }

        // 5. 上传者本人（严格：用户类型 + 用户编号都需一致；历史数据缺用户类型时不放行，
        //    避免「会员与后台账号编号相同」时互相越权）
        if (isOwner(file, loginUser)) {
            // 未绑定草稿：仅在待绑定窗口内对上传者本人可读，超期则**不再因"本人"放行**。
            // 注意这里刻意「继续往下判定」而不是直接拒绝：
            //   - 非特权身份（例如会员）后续步骤都不会命中，结果仍是拒绝，语义不变；
            //   - 但若本人同样是**同租户**且持 infra:file:query 的后台运维（第 8 步），
            //     仍应能读到该文件——否则会出现「同一后台身份，走本人分支被拒、
            //     走运维分支被放行」的自相矛盾。
            boolean expiredUnbound = Objects.equals(file.getBindStatus(), FileBindStatusEnum.UNBOUND.getStatus())
                    && file.getBindExpireTime() != null
                    && file.getBindExpireTime().isBefore(LocalDateTime.now());
            if (!expiredUnbound) {
                return FileReadAccessEnum.ALLOW;
            }
        }

        // 6. 已绑定业务单据的可读参与方（严格：类型:编号 完全匹配）
        if (Objects.equals(file.getBindStatus(), FileBindStatusEnum.BOUND.getStatus())
                && containsParticipant(file.getBindParticipants(), loginUser.getUserType(), loginUser.getId())) {
            return FileReadAccessEnum.ALLOW;
        }

        // 7. 平台运维：同租户（第 4 步已保证）且**显式持有** infra:file:query 的后台用户。
        //    这里刻意**不再**提供「同租户任意后台账号即可读」的兜底：那等于租户内无差别放行，
        //    绕过了店铺 / 单据 / 客服授权范围。业务侧如需让特定后台角色读取，应把它作为
        //    participant 传进 bindFile（第 6 步），而不是依赖租户内全量放行。
        if (UserTypeEnum.ADMIN.getValue().equals(loginUser.getUserType())
                && securityFrameworkService.hasPermission(PERMISSION_FILE_QUERY)) {
            return FileReadAccessEnum.ALLOW;
        }

        // 8. 其余一律拒绝
        return FileReadAccessEnum.FORBIDDEN;
    }

    @Override
    public void handleReadRequest(HttpServletRequest request, HttpServletResponse response,
                                  Long configId, boolean inline) throws Exception {
        // 1. 解析请求中的文件路径（/get/ 或 /preview/ 之后的部分）
        String path = StrUtil.subAfter(request.getRequestURI(), inline ? "/preview/" : "/get/", false);
        if (StrUtil.isEmpty(path)) {
            throw new IllegalArgumentException("结尾的 path 路径必须传递");
        }
        // 解码，解决中文、%、+ 等特殊字符路径的问题
        path = HttpUtils.decodeUrlPath(path);

        // 2. 访问控制校验：公开匿名可读；私有需登录且通过归属/参与方校验；无法确认归属即拒绝
        FileReadAccessEnum access = checkReadAccess(configId, path);
        if (access != FileReadAccessEnum.ALLOW) {
            int status = access == FileReadAccessEnum.NEED_LOGIN
                    ? HttpStatus.UNAUTHORIZED.value() : HttpStatus.FORBIDDEN.value();
            String message = access == FileReadAccessEnum.NEED_LOGIN
                    ? "该文件为私有资源，请先登录" : "无权访问该文件";
            log.warn("[handleReadRequest][configId({}) path({}) 访问被拒：{}]", configId, path, access.getName());
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JsonUtils.toJsonString(CommonResult.error(status, message)));
            return;
        }

        // 3. 读取内容（用文件真实的 config_id 取回：提升到 OSS 的公开资源会从 OSS 读取，
        //     网关 URL 内嵌的原 master config 不匹配也能正确解析）
        FileDO file = resolveFileForRead(configId, path);
        if (file == null) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        // ACL is checked above. Only a server-confirmed PUBLIC file in explicit public
        // HTTPS storage can bypass the SDK content proxy. Private files never redirect.
        if (Integer.valueOf(1).equals(file.getAccessType()) && isPromotedToOss(file)) {
            var config = fileConfigService.getFileConfig(file.getConfigId());
            if (config != null && config.getConfig() instanceof cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClientConfig s3Config) {
                // Building a public URL needs no credentials, SDK initialization or network call.
                String publicUrl = new cn.iocoder.txgy.module.infra.framework.file.core.client.s3.S3FileClient(
                        file.getConfigId(), s3Config).publicReadUrl(file.getPath());
                if (publicUrl != null) {
                    response.setHeader("Cache-Control", "no-store");
                    response.sendRedirect(publicUrl);
                    return;
                }
            }
        }
        byte[] content = getFileContent(file.getConfigId(), file.getPath());
        if (content == null) {
            log.warn("[handleReadRequest][configId({}) path({}) 文件不存在]", configId, path);
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }
        String filename = file != null && StrUtil.isNotEmpty(file.getName())
                ? file.getName() : FileUtil.getName(path);
        if (inline) {
            FileTypeUtils.writeInline(response, filename, content);
        } else {
            FileTypeUtils.writeAttachment(response, filename, content);
        }
    }

    /**
     * 是否为上传者本人
     *
     * **严格匹配**：用户类型与用户编号都必须一致。
     *
     * 注意：历史数据中 {@code owner_user_type} 为 NULL（旧版本未记录用户类型）。此时**不放行**，
     * 否则「会员 id=1」与「后台用户 id=1」会互相命中而形成越权；历史私有文件改由
     * 「后台 infra:file:query + 同租户」通道访问。
     */
    private boolean isOwner(FileDO file, LoginUser loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            return false;
        }
        return Objects.equals(file.getOwnerUserId(), loginUser.getId())
                && Objects.equals(file.getOwnerUserType(), loginUser.getUserType());
    }

    /**
     * 是否为「同一归属」——用于登记（{@code /file/create}）的幂等判定
     */
    private boolean isSameOwner(FileDO file, LoginUser loginUser) {
        return isOwner(file, loginUser);
    }

    private boolean containsParticipant(String participants, Integer userType, Long userId) {
        if (StrUtil.isEmpty(participants) || userType == null || userId == null) {
            return false;
        }
        for (String item : StrUtil.split(participants, ',')) {
            String[] parts = StrUtil.splitToArray(StrUtil.trim(item), ':');
            if (parts.length != 2) {
                continue;
            }
            if (StrUtil.isNumeric(parts[0]) && StrUtil.isNumeric(parts[1])
                    && Integer.valueOf(parts[0]).equals(userType)
                    && Long.valueOf(parts[1]).equals(userId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int bindFiles(FileBindReqDTO reqDTO) {
        // 1. 归一化 URL（移除签名等查询参数），按记录匹配
        List<String> urls = reqDTO.getUrls().stream()
                .filter(StrUtil::isNotEmpty)
                .map(HttpUtils::removeUrlQuery)
                .distinct()
                .toList();
        if (CollUtil.isEmpty(urls)) {
            return 0;
        }
        List<FileDO> files = fileMapper.selectListByUrls(urls);
        if (CollUtil.isEmpty(files)) {
            log.warn("[bindFiles][业务({}/{}) 提交的 URL 均无文件登记记录，未绑定：{}]",
                    reqDTO.getBizType(), reqDTO.getBizId(), urls);
            return 0;
        }
        if (files.size() < urls.size()) {
            // URL 可能指向外部图片（非本文件服务签发）或登记失败，这里只告警不阻断：
            // 业务单据本身可以保存，但该图片不会获得私有可读权限。
            log.warn("[bindFiles][业务({}/{}) 提交 {} 个 URL，仅匹配到 {} 条文件登记记录]",
                    reqDTO.getBizType(), reqDTO.getBizId(), urls.size(), files.size());
        }

        // 2. 计算目标访问类型：客户端/业务方声明公开时，必须该业务类型允许公开，否则强制私有
        FileBizTypeEnum bizType = FileBizTypeEnum.valueOfName(reqDTO.getBizType());
        boolean wantPublic = Objects.equals(reqDTO.getAccessType(), FileAccessTypeEnum.PUBLIC.getAccessType());
        if (wantPublic && (bizType == null || !bizType.isPublicCapable())) {
            log.warn("[bindFiles][业务类型({}) 不允许公开，已强制按私有绑定]", reqDTO.getBizType());
            wantPublic = false;
        }

        // 3. 逐个绑定前做「引用公开资源 / 租户边界 / 已有绑定 / 原上传者」核验，**不覆盖**既有归属
        String participants = buildParticipants(reqDTO.getParticipants());
        int updated = 0;
        int referencedPublic = 0;
        for (FileDO file : files) {
            // 3.0 引用公开资源：文件已是服务端确认过的公开资源，直接跳过。
            //     典型场景是询价明细 / 售后凭证引用了商品图，或评论引用了商品图。
            //     理由：公开资源已经匿名可读，再次绑定既不能把它降级为私有（询价、售后场景），
            //     也不应该因为"已挂在别的单据上"而报冲突（评论场景），否则会拦住正常的复用。
            //     bizId 只是参考信息，不参与任何读取授权判定，因此无需改写。
            if (isPublicResource(file)) {
                referencedPublic++;
                // 关键修复：已确认公开的文件，若之前 OSS 提升因网络抖动失败（内容仍停留在 master、
                // config_id 仍是 master），不能"再次保存就跳过"。这里对"公开但未进入 OSS"的文件补做提升；
                // 已在 OSS（config_id 已是 OSS 配置）时 promoteToPublicOss 立即返回 null，幂等、无副作用。
                if (!isPromotedToOss(file)) {
                    String[] promoted = promoteToPublicOss(file);
                    if (promoted != null) {
                        fileMapper.updateById(new FileDO().setId(file.getId())
                                .setConfigId(Long.valueOf(promoted[0])).setUrl(promoted[1]));
                    }
                }
                continue;
            }
            // 3.1 租户边界：文件已有租户归属时，绑定方租户必须一致（避免跨租户认领）
            if (file.getTenantId() != null && reqDTO.getTenantId() != null
                    && !file.getTenantId().equals(reqDTO.getTenantId())) {
                log.warn("[bindFiles][跨租户绑定被拒：文件({}) 归属租户({})，绑定租户({})]",
                        file.getId(), file.getTenantId(), reqDTO.getTenantId());
                throw exception(FILE_BIND_TENANT_MISMATCH);
            }
            // 3.2 重绑定冲突：已绑定到其他业务单据时，只有**原上传者本人**可以把自己的文件改挂到新单据；
            //     否则一律拒绝，避免把他人单据的文件"接管"过来。
            boolean sameOwnerAsRequest = file.getOwnerUserId() != null && reqDTO.getOwnerUserId() != null
                    && file.getOwnerUserId().equals(reqDTO.getOwnerUserId())
                    && Objects.equals(file.getOwnerUserType(), reqDTO.getOwnerUserType());
            if (StrUtil.isNotEmpty(file.getBizId()) && StrUtil.isNotEmpty(reqDTO.getBizId())
                    && !Objects.equals(file.getBizId(), reqDTO.getBizId()) && !sameOwnerAsRequest) {
                log.warn("[bindFiles][绑定冲突被拒：文件({}) 已绑定 bizId({})，本次 bizId({})，且请求方非原上传者]",
                        file.getId(), file.getBizId(), reqDTO.getBizId());
                throw exception(FILE_BIND_BIZ_CONFLICT);
            }
            // 3.3 上传者核验：**必须在放行前拒绝**，且不接受"声明式认领"。
            //     绑定会授予读取权；公开绑定更会把文件变成匿名可读。因此请求方必须能证明
            //     自己就是该文件的上传者：
            //       a) 文件**已有完整归属** → (ownerUserType, ownerUserId) 必须完全一致；
            //       b) 文件**缺少完整归属**（历史/异常数据）→ **一律拒绝**。
            //          不允许凭请求方自己声明的归属补录：头像、商品图等入口都会声明
            //          "当前用户就是上传者"，若接受声明，"声明是我" 就等价于 "确实属于我"，
            //          同租户任何人只要知道地址就能把它认领成自己的（公开类型还会被抬成匿名可读）。
            //          这类文件必须经过**可信迁移**（按 creator + 唯一可判用户类型回填，见
            //          009-file-public-recompute.sql）或人工归属核验后再参与绑定。
            //     这是「他人把自己的私有图片地址放进头像/商品图/评价图，从而被抬成公开」
            //     这条提权路径的唯一拦截点（MEMBER_AVATAR / PRODUCT_IMAGE 等 bizType 的
            //     publicCapable 都为 true，只靠 isPublicCapable 拦不住）。
            boolean fileHasOwner = file.getOwnerUserId() != null && file.getOwnerUserType() != null;
            if (!fileHasOwner) {
                log.warn("[bindFiles][归属不可信被拒：文件({}) owner=({}:{})，业务({}/{})]",
                        file.getId(), file.getOwnerUserType(), file.getOwnerUserId(),
                        reqDTO.getBizType(), reqDTO.getBizId());
                throw exception(FILE_BIND_OWNER_UNKNOWN);
            }
            if (!sameOwnerAsRequest) {
                log.warn("[bindFiles][上传者不一致被拒：文件({}) 记录({}:{})，请求({}:{})，业务({}/{})]",
                        file.getId(), file.getOwnerUserType(), file.getOwnerUserId(),
                        reqDTO.getOwnerUserType(), reqDTO.getOwnerUserId(),
                        reqDTO.getBizType(), reqDTO.getBizId());
                throw exception(FILE_BIND_OWNER_MISMATCH);
            }
            // 3.4 公开绑定二次确认：只有**归属已核验一致**的请求才能把文件抬为公开资源。
            //     （3.3 已保证走到这里时归属必然一致，这里显式再表达一次业务约束，
            //       避免后续有人放宽 3.3 时无声地把公开口子重新打开。）
            if (wantPublic && !sameOwnerAsRequest) {
                log.warn("[bindFiles][公开绑定被拒：文件({}) 归属未通过核验]", file.getId());
                throw exception(FILE_BIND_OWNER_MISMATCH);
            }

            // 3.5 **服务端确认公开之后**才允许内容进入公开 OSS。
            //     上传期绝不做这件事（见 createFile 注释）：directory 是客户端传参，
            //     用它决定"是否存到公开 OSS"等于让客户端自选安全等级。
            //     也只有走到这里，文件的归属与公开口径都已被服务端核验通过。
            String[] promoted = wantPublic ? promoteToPublicOss(file) : null;

            FileDO update = new FileDO().setId(file.getId())
                    .setBizType(bizType != null ? bizType.name() : file.getBizType())
                    .setBizId(reqDTO.getBizId())
                    .setAccessType(wantPublic ? FileAccessTypeEnum.PUBLIC.getAccessType()
                            : FileAccessTypeEnum.PRIVATE.getAccessType())
                    .setBindStatus(wantPublic ? FileBindStatusEnum.PUBLIC.getStatus()
                            : FileBindStatusEnum.BOUND.getStatus())
                    .setBindParticipants(participants)
                    .setBindExpireTime(null); // 已绑定：不再有过期可读
            // 3.3 已保证上传者归属存在且一致，因此这里**不存在**"补录上传者"的分支：
            // 绑定只授予读取权，任何情况下都不改写 owner（归属变更只能走可信迁移）。
            if (file.getTenantId() == null && reqDTO.getTenantId() != null) {
                update.setTenantId(reqDTO.getTenantId());
            }
            if (promoted != null) {
                // 内容已复制到公开 OSS：记录改为指向该存储，url 用 OSS 直连地址
                update.setConfigId(Long.valueOf(promoted[0])).setUrl(promoted[1]);
            }
            updated += fileMapper.updateById(update);
        }
        if (referencedPublic > 0) {
            log.info("[bindFiles][业务({}/{}) 有 {} 个文件已是公开资源，按引用处理未降级为私有]",
                    reqDTO.getBizType(), reqDTO.getBizId(), referencedPublic);
        }
        return updated;
    }

    /**
     * 是否为已由服务端确认过的公开资源（匿名可读）。
     *
     * 用于区分「私有单据引用公开资源」与「私有文件重绑定」：前者必须保持公开，不能被降级。
     */
    private boolean isPublicResource(FileDO file) {
        return Objects.equals(file.getAccessType(), FileAccessTypeEnum.PUBLIC.getAccessType())
                || Objects.equals(file.getBindStatus(), FileBindStatusEnum.PUBLIC.getStatus());
    }

    private String buildParticipants(List<FileBindReqDTO.Participant> participants) {
        if (CollUtil.isEmpty(participants)) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (FileBindReqDTO.Participant participant : participants) {
            if (participant.getUserType() == null || participant.getUserId() == null) {
                continue;
            }
            items.add(participant.getUserType() + ":" + participant.getUserId());
        }
        return items.isEmpty() ? null : String.join(",", items);
    }

    @Override
    public int cleanExpiredOrphanFiles(int limit) {
        List<FileDO> files = fileMapper.selectExpiredUnboundList(LocalDateTime.now(), limit);
        int count = 0;
        for (FileDO file : files) {
            // 安全护栏：已被业务数据引用的文件（即使业务侧回绑失败）绝不清理，避免"已保存的业务图片"被误删
            if (isReferencedByBusiness(file.getUrl())) {
                log.warn("[cleanExpiredOrphanFiles][跳过：文件({}) 仍被业务数据引用，不清理]", file.getId());
                continue;
            }
            try {
                FilePathUtils.validatePath(file.getPath());
                FileClient client = fileConfigService.getFileClient(file.getConfigId());
                if (client != null) {
                    client.delete(file.getPath());
                }
                fileMapper.deleteById(file.getId());
                count++;
            } catch (Exception ex) {
                log.warn("[cleanExpiredOrphanFiles][文件({}) 清理失败]", file.getId(), ex);
            }
        }
        if (count > 0) {
            log.info("[cleanExpiredOrphanFiles][清理过期待绑定孤儿文件 {} 个]", count);
        }
        return count;
    }

    // ==================== OSS 提升补传（可追踪、可安全重试） ====================

    @Override
    public List<FileDO> listPublicUnpromoted(int limit) {
        return fileMapper.selectPublicUnpromoted(publicOssConfigId, Math.max(1, limit));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OssPromoteRetryResult retryPublicOssPromotion(int limit) {
        List<FileDO> files = fileMapper.selectPublicUnpromoted(publicOssConfigId, Math.max(1, limit));
        int total = files.size();
        int success = 0, failed = 0, skipped = 0;
        for (FileDO file : files) {
            if (isPromotedToOss(file)) {
                skipped++; // 已在 OSS，幂等跳过
                continue;
            }
            String[] promoted = promoteToPublicOss(file);
            if (promoted != null) {
                fileMapper.updateById(new FileDO().setId(file.getId())
                        .setConfigId(Long.valueOf(promoted[0])).setUrl(promoted[1]));
                success++;
            } else {
                failed++; // OSS 仍不可达：promoteToPublicOss 已记录失败次数与末次错误，保留 master
            }
        }
        if (success > 0 || failed > 0) {
            log.info("[retryPublicOssPromotion][补传完成 total={} success={} failed={} skipped={}]",
                    total, success, failed, skipped);
        }
        return OssPromoteRetryResult.of(total, success, failed, skipped);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OssPromoteRetryResult retryOssPromotion(Long fileId) {
        FileDO file = fileMapper.selectById(fileId);
        if (file == null) {
            return OssPromoteRetryResult.notFound();
        }
        // 安全护栏：仅允许补传"已确认公开"的文件；私有文件一律拒绝，绝不迁移到公共桶
        if (!isPublicResource(file)) {
            return OssPromoteRetryResult.notPublic();
        }
        if (isPromotedToOss(file)) {
            return OssPromoteRetryResult.alreadyPromoted();
        }
        String[] promoted = promoteToPublicOss(file);
        if (promoted != null) {
            fileMapper.updateById(new FileDO().setId(file.getId())
                    .setConfigId(Long.valueOf(promoted[0])).setUrl(promoted[1]));
            return OssPromoteRetryResult.singleSuccess();
        }
        FileDO after = fileMapper.selectById(fileId);
        return OssPromoteRetryResult.singleFailed(
                after != null ? after.getPromoteLastError() : "提升失败，原因未知");
    }

    /**
     * 文件是否仍被业务数据引用（孤儿清理护栏）
     *
     * 保守策略：查询异常时按"疑似被引用"处理，宁可漏删也不错删。
     */
    private boolean isReferencedByBusiness(String url) {
        if (StrUtil.isEmpty(url)) {
            return false;
        }
        try {
            Long count = fileMapper.countBusinessReferences(url);
            return count != null && count > 0;
        } catch (Exception ex) {
            log.warn("[isReferencedByBusiness][引用检查异常，按被引用处理 url({})]", url, ex);
            return true;
        }
    }

}
