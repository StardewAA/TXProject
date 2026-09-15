package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileRegisterReqVO;

/**
 * 证照文件版本服务（登记/病毒扫描结果回写）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三＋§18.2；《招投标模块开发规范》§5 三-5
 * （文件经 FileApi 上传，业务表只记 file_id/sha256/size、上传须过病毒扫描与类型白名单）。
 */
public interface CertFileVersionService {

    /**
     * 登记证照文件版本：证书必须存在；同证同哈希拒绝（CERT_FILE_HASH_DUPLICATE）；
     * 版本号=同类别当前最大版本+1，新行置为 is_current 并关闭旧当前行（只追加，v3.3 §三）；
     * 病毒扫描状态默认 PENDING。返回文件版本主键。
     */
    Long registerFile(CertFileRegisterReqVO reqVO);

    /**
     * 病毒扫描结果回写：CLEAN 可预览/下载；INFECTED 自动置 is_current=false 停用（v3.3 §三口径）。
     */
    void markScanResult(Long fileVersionId, String virusScanStatus);

    /**
     * 证照全部文件版本（只追加历史；页面回查用，按类别+版本降序）。
     */
    java.util.List<cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO> listFiles(Long certId);
}
