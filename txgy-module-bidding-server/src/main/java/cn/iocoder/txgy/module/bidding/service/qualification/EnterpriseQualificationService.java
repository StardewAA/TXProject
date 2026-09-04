package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;

import java.time.LocalDate;

/**
 * 企业资质主档/申报流服务（M5 核心：资质台账 + 延续申报状态机）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五（表 15-1 主档/表 15-7 申报流）；
 * 《招投标模块开发规范》§4.5（乐观锁 CAS、状态流转走接口）；与通用台账边界：影像/验真归 certificate_info，
 * 本域经 binded_cert_id 关联复用（§十五 边界）。
 */
public interface EnterpriseQualificationService {

    /**
     * 建档：UK（ent+serial+category+grade+cert_no）判重；binded_cert_id 存在性校验（复用台账验真）；
     * 默认 VALID/MANUAL。返回主键。
     */
    Long createQualification(EnterpriseQualificationSaveReqVO reqVO);

    /**
     * 状态流转（系统/人工显式调整）：QualificationStatusEnum 迁移表校验，乐观锁 CAS。
     */
    void changeStatus(Long qualificationId, String targetStatus, Integer version, String reason,
                      Long operatorId);

    /**
     * 延续申报（RENEW）：新建申报流 SUBMITTED（applyType=RENEW），同事务将主档
     * VALID/EXPIRING_SOON/EXPIRED → PENDING_RENEW（待续期，15-7）；返回申报主键。
     */
    Long submitRenewalApply(Long qualificationId, Integer version, Long ownerBy, LocalDate dueDate, String remark);

    /**
     * 延续审批通过：申报 SUBMITTED → APPROVED→ISSUED（领证）；同事务回写主档 VALID 并更新
     * valid_to（newValidTo 必填）/cert_no（可空）——办结回写口径（15-7）。返回申报主键。
     */
    Long approveRenewalApply(Long applyId, Integer version, String resultNo, String newCertNo,
                             LocalDate newValidTo, Long reviewerId);

    /**
     * 审批退回：申报 SUBMITTED → REJECTED；同事务主档 PENDING_RENEW 还原 VALID（原有效性延续，
     * 临近到期由预警扫描重新置 EXPIRING_SOON，见 15-1 联动）。返回申报主键。
     */
    Long rejectRenewalApply(Long applyId, Integer version, Long reviewerId, String remark);
}
