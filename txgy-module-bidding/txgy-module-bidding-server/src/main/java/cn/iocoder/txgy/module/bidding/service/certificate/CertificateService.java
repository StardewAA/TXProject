package cn.iocoder.txgy.module.bidding.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertHashChainVerifyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportFailureVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatsReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatsRespVO;
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
     * 证照 Excel 导入（五页改造契约 Q1，2026-09-28）：逐行建档，行级回执——
     * 单行失败不影响其余行；cerNo 重复/必填缺失按行记 holders 失败明细。
     */
    CertificateImportRespVO importCertificates(java.util.List<CertificateImportExcelVO> rows, Long operatorId);

    /**
     * 证照详情（管理端）：查询不存在抛 CERTIFICATE_NOT_EXISTS。
     */
    CertificateRespVO getCertificate(Long id);

    /**
     * 证照台账分页（管理端）：certNo/certType/certStatus/verificationStatus 精确、certName/holderName 模糊，
     * 按建档倒序；租户隔离由平台拦截器实施（开发规范 §5 五-1）。
     */
    PageResult<CertificateRespVO> getCertificatePage(CertificatePageReqVO reqVO);

    /**
     * 看板统计（Q3，2026-09-28）：按台账状态/验真状态/证照大类全量计数（含暂扣/吊销/注销），
     * 支持 certStatuses/verificationStatuses 多选；过滤口径与 getCertificatePage 完全一致，
     * 使看板与列表筛选联动（替代前端 5 次 pageSize=1 并发计数）。
     */
    CertificateStatsRespVO getCertificateStats(CertificateStatsReqVO reqVO);

    /**
     * 变更留痕分页（契约：变更留痕读出口，2026-09-21 补）。
     *
     * <p>为什么需要：cert_change_logs 与写入逻辑一直存在（建档/更新/状态流转均留痕，含 v3.3 §13 哈希链），
     * 但**此前没有任何查询接口**，前端无法回答"这条证照改过什么"——而"查看历史"是明确的产品要求。
     *
     * @param reqVO certId 必传（留痕是某条证照的历史）；可按 changeType/fieldName/batchId 过滤
     */
    PageResult<CertChangeLogRespVO> getChangeLogPage(CertChangeLogPageReqVO reqVO);

    /**
     * **回验证照变更留痕哈希链**：逐行重算 record_hash 并核对 prev_hash，断链即返回首处不一致的行。
     *
     * <p>为什么需要：链只在写入时计算，没有回验时"防篡改"只是口号（改了中间行不会被发现）。
     * 本方法把"能验"变成"会验"（等保 2.0 三级 / GB/T 38540 事件存证口径）。
     *
     * @param certId 证照 id
     * @return 回验结果（ok / checked / brokenAtId / reason / expected / actual）
     */
    CertHashChainVerifyRespVO verifyChangeLogChain(Long certId);
}
