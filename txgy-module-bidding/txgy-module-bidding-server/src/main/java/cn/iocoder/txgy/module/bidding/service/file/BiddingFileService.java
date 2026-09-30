package cn.iocoder.txgy.module.bidding.service.file;

// 规范依据：《招投标模块开发规范》§5 三-5 文件存储口径（FileApi + file_id/sha256/size）+ 红线 8。

import cn.iocoder.txgy.module.bidding.controller.admin.file.vo.BiddingFileRespVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * bidding 文件注册服务：把「上传」与「拿到全局唯一 file_id」收敛为一次调用。
 *
 * <p>业务目标：业务表以 file_id 关联文件，因此本服务对调用方的承诺是
 * <b>要么返回带 fileId 的结果，要么抛业务异常</b>，不存在「成功但没有 file_id」的中间态。
 */
public interface BiddingFileService {

    /**
     * 上传文件并返回 file_id。
     *
     * @param file      上传文件（空文件抛 FILE_UPLOAD_EMPTY）
     * @param directory 归档目录（空则用默认目录）
     * @return 含 fileId/url/name/type/size/sha256；文件服务未回传 file_id 时抛 FILE_REGISTER_FAILED
     */
    BiddingFileRespVO upload(MultipartFile file, String directory);

    /**
     * 按访问地址补登记 file_id（历史"只有 URL 没有 file_id"的数据修复通道）。
     *
     * @param url 平台文件服务返回的访问地址
     * @return 含 fileId/url/name/type/size（sha256 无法从元数据得到，返回 null）
     */
    BiddingFileRespVO registerByUrl(String url);

    /** 注册服务端生成的文件内容（如 CA 签章结果文件）。 */
    BiddingFileRespVO registerBytes(byte[] content, String name, String directory, String type);

    /**
     * 读取文件内容（按 fileId）——文件读出口的唯一实现通道。
     *
     * <p>业务目标：把「按 fileId 取文件」收在招投标侧：业务账号不再需要 infra:file:query，
     * 前端也拿不到裸 url（URL 即凭据，属红线 8 管控对象）。
     *
     * @param fileId 文件编号（infra_file.id）
     * @return 文件内容
     * @throws cn.iocoder.txgy.framework.common.exception.ServiceException 未登记/坐标非法/病毒未过/读取失败
     */
    byte[] download(Long fileId);

    /**
     * 按已存在的 fileId 补登记存储坐标（供其他域登记文件时调用，使其文件可被 /bidding/file/download 读取）。
     *
     * <p>使用场景：证照文件登记（cert_file_versions）等只拿到 fileId、未走本模块上传通道时，
     * 由调用方传入 fileId 与访问地址，本服务解析并登记存储坐标；已登记则不重复写（幂等）。
     *
     * @param fileId      文件编号
     * @param url         平台文件访问地址（含存储坐标；为 null 时仅登记元数据，下载时报坐标缺失）
     * @param name        文件名
     * @param size        文件大小
     * @param virusStatus 病毒扫描状态（null=按 UNKNOWN 登记）
     */
    void registerStorageCoordByFileId(Long fileId, String url, String name, Long size, String virusStatus);

    /**
     * 查询文件登记信息（供下载接口回填响应头文件名、以及前端按 id 取元数据）。
     *
     * @param fileId 文件编号
     * @return 登记信息；未登记抛 FILE_NOT_REGISTERED
     */
    cn.iocoder.txgy.module.bidding.dal.dataobject.file.BiddingFileDO getByFileId(Long fileId);

}
