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

}
