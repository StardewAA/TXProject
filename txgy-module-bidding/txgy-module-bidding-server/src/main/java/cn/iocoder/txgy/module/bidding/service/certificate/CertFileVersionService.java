package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileCorrectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileRegisterReqVO;

/**
 * 证照文件版本服务（登记/病毒扫描结果回写/作废/更正元数据）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §三＋§18.2；《招投标模块开发规范》§5 三-5
 * （文件经 FileApi 上传，业务表只记 file_id/sha256/size、上传须过病毒扫描与类型白名单）。
 */
public interface CertFileVersionService {

    /**
     * 登记证照文件版本：证书必须存在；同证同哈希**幂等返回已有版本主键**（F1，不再抛
     * CERT_FILE_HASH_DUPLICATE）；版本号=同类别当前最大版本+1，新行置为 is_current 并关闭旧当前行
     * （只追加，v3.3 §三）；病毒扫描状态默认 PENDING。返回文件版本主键。
     */
    Long registerFile(CertFileRegisterReqVO reqVO);

    /**
     * 病毒扫描结果回写：CLEAN 可预览/下载；INFECTED 自动置 is_current=false 停用（v3.3 §三口径）。
     */
    void markScanResult(Long fileVersionId, String virusStatus);

    /**
     * 证照全部文件版本（只追加历史；页面回查用，按类别+版本降序）。
     */
    java.util.List<cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO> listFiles(Long certId);

    /**
     * 作废文件版本（F2）：作废=软删留痕——行保留并标 voided/voidReason/voidedAt；
     * 若该行是当前版本，同时清除 is_current（该类别失去当前版本，用户重新登记新版本）。
     * 已作废行重复作废抛 CERT_FILE_ALREADY_VOIDED；不存在抛 CERT_FILE_NOT_EXISTS。
     */
    void voidVersion(Long fileVersionId, String reason);

    /**
     * 更正文件版本元数据（F2）：只允许更正展示/留痕类字段（remark + 加注件 copy_* 四要素），
     * 不传的字段保持不变；至少一个可更正字段，否则抛 CERT_FILE_CORRECT_NO_FIELD。
     * 不存在抛 CERT_FILE_NOT_EXISTS。
     */
    void correctMetadata(CertFileCorrectReqVO reqVO);
}
