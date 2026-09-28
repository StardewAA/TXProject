package cn.iocoder.txgy.module.infra.service.file;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.infra.api.file.dto.FileBindReqDTO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.txgy.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.txgy.module.infra.framework.file.core.enums.FileReadAccessEnum;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 文件 Service 接口
 *
 * @author OPENLAB BS
 */
public interface FileService {

    /**
     * 获得文件分页
     *
     * @param pageReqVO 分页查询
     * @return 文件分页
     */
    PageResult<FileDO> getFilePage(FilePageReqVO pageReqVO);

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content   文件内容
     * @param name      文件名称，允许空
     * @param directory 目录，允许空
     * @param type      文件的 MIME 类型，允许空
     * @return 文件路径
     */
    String createFile(@NotEmpty(message = "文件内容不能为空") byte[] content,
                      String name, String directory, String type);

    /**
     * 生成文件预签名地址信息，用于上传
     *
     * @param name      文件名
     * @param directory 目录
     * @return 预签名地址信息
     */
    FilePresignedUrlRespVO presignPutUrl(@NotEmpty(message = "文件名不能为空") String name,
                                         String directory);
    /**
     * 生成文件预签名地址信息，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    String presignGetUrl(String url, Integer expirationSeconds);

    /**
     * 创建文件
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createFile(FileCreateReqVO createReqVO);
    FileDO getFile(Long id);

    FileDO getFileByUrl(String url);

    /**
     * 按访问地址获得文件内容
     *
     * @param url 完整的文件访问地址
     * @return 文件内容
     */
    byte[] getFileContentByUrl(String url) throws Exception;

    /**
     * 删除文件
     *
     * @param id 编号
     */
    void deleteFile(Long id) throws Exception;

    /**
     * 批量删除文件
     *
     * @param ids 编号列表
     */
    void deleteFileList(List<Long> ids) throws Exception;

    /**
     * 获得文件内容
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 文件内容
     */
    byte[] getFileContent(Long configId, String path) throws Exception;

    /**
     * 获得文件
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 文件
     */
    FileDO getFileByConfigIdAndPath(Long configId, String path);

    /**
     * 校验当前登录身份是否可读取指定文件
     *
     * 规则（安全默认）：
     * 1. 公开资源（bindStatus = PUBLIC / accessType = PUBLIC）：匿名可读；
     * 2. 私有资源：必须登录，且满足「上传者本人 / 未绑定短窗口内上传者 / 已绑定单据参与方 / 具备文件管理权限的后台用户」之一；
     * 3. 无法确认归属（无文件记录）时，拒绝匿名访问。
     *
     * @param configId 配置编号
     * @param path     文件路径
     * @return 鉴权结果
     */
    FileReadAccessEnum checkReadAccess(Long configId, String path);

    /**
     * 处理文件读取请求：完成访问控制校验，并写出文件内容或 401/403 错误响应
     *
     * 说明：后台（/admin-api）与用户端（/app-api）共用同一套读取逻辑，
     * 避免两处实现产生鉴权差异。所有读取都必须经过本方法。
     *
     * @param request  请求（用于取路径）
     * @param response 响应（写出内容或错误）
     * @param configId 配置编号
     * @param inline   true 表示在线预览（inline），false 表示下载（attachment）
     */
    void handleReadRequest(jakarta.servlet.http.HttpServletRequest request,
                           jakarta.servlet.http.HttpServletResponse response,
                           Long configId, boolean inline) throws Exception;

    /**
     * 业务落库后，由服务端核验并绑定文件归属
     *
     * @param reqDTO 绑定请求
     * @return 实际更新的文件条数
     */
    int bindFiles(FileBindReqDTO reqDTO);

    /**
     * 清理已过期且未绑定业务单据的孤儿文件（记录 + 存储对象）
     *
     * @param limit 单次最大清理条数
     * @return 实际清理条数
     */
    int cleanExpiredOrphanFiles(int limit);

    /**
     * 批量补传「已确认公开但未进入公开 OSS」的文件（可安全重试）
     *
     * <p>只处理 PUBLIC 文件（access_type=1 & bind_status=2 且 config_id 非 OSS 配置），
     * 绝不会把私有文件迁移到公共桶；已在 OSS 的文件会被幂等跳过。网络抖动导致的失败
     * 仅记录失败次数与末次错误并保留 master，不抛异常、不阻断。</p>
     *
     * @param limit 单次最大补传条数
     * @return 本次处置统计（总数/成功/失败/跳过）
     */
    OssPromoteRetryResult retryPublicOssPromotion(int limit);

    /**
     * 单文件补传：仅当该文件是「已确认公开但未进入 OSS」时才执行提升
     *
     * @param fileId 文件编号
     * @return 本次处置结果（含拒绝原因，例如「非公开文件」）
     */
    OssPromoteRetryResult retryOssPromotion(Long fileId);

    /**
     * 列出「已确认公开但内容仍停留在 master、未进入公开 OSS」的文件（运维可追踪）
     *
     * @param limit 最大条数
     * @return stranded 文件清单
     */
    List<FileDO> listPublicUnpromoted(int limit);

}
