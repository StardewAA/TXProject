package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;

/**
 * 证照台账服务（建档/更新/状态流转）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二/§十三＋§18 v3.4；
 * 《招投标模块开发规范》§4.5（乐观锁/幂等）与 §5（表与审计）。
 */
public interface CertificateService {

    /**
     * 建档（createCertificate）：证书编号租户内唯一（UK），重复建档抛 CERTIFICATE_NO_DUPLICATE；
     * 落库后写 CREATE 变更日志（哈希链，v3.3 §13）。返回证照主键。
     */
    Long createCertificate(CertificateSaveReqVO reqVO);

    /**
     * 更新证照字段（乐观锁 CAS，版本冲突抛 CERTIFICATE_DATA_MODIFIED）；
     * 仅记录实际发生变化的字段到 UPDATE 变更日志（v3.3 §13）。
     */
    void updateCertificate(CertificateSaveReqVO reqVO);

    /**
     * 状态流转：按 CertificateStatusEnum.isTransitionAllowed 校验（51号 ZZZT 语义）；
     * 吊销/注销记 REVOKE 类型日志，其余状态流转记 STATUS_CHANGE；乐观锁 CAS。
     */
    void changeStatus(Long certificateId, String targetStatus, Integer version, String reason,
                      CertificateChangeSourceEnum opSource, String opIp);

    /**
     * 证照详情（管理端）：查询不存在抛 CERTIFICATE_NOT_EXISTS。
     */
    CertificateRespVO getCertificate(Long id);

    /**
     * 证照台账分页（管理端）：certNo/certType/certStatus/verificationStatus 精确、certName/holderName 模糊，
     * 按建档倒序；租户隔离由平台拦截器实施（开发规范 §5 五-1）。
     */
    PageResult<CertificateRespVO> getCertificatePage(CertificatePageReqVO reqVO);
}
