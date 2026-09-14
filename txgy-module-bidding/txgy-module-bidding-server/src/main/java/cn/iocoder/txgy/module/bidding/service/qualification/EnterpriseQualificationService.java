package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRespVO;

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

    /**
     * 资质台账分页（页签 1）。
     *
     * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G1；《招投标模块开发规范》§4.1 分页口径。
     * <p>为什么这样写：筛选项全部下推 SQL；{@code expiringInDays} 传空即不过滤（默认过滤会让台账"看起来缺数据"）；
     * 出参一次性带出 {@code daysToExpire}（剩余天数统一由后端算）与 {@code hasPendingApply}（按钮态），
     * 免去前端二次查询与口径分歧。
     * <p>业务目标：台账列表一次拿全渲染与按钮可用性所需字段。
     */
    PageResult<QualificationRespVO> getQualificationPage(QualificationPageReqVO reqVO);

    /**
     * 资质详情（页签 1 行展开/详情页）：主档 + 绑定证照摘要 + 最近一次达标核查。
     *
     * <p>规范出处：契约 §4 G2；《电子证件全流程管理_数据库设计.md》v3.3 §十五（主档与 certificate_info 的
     * binded_cert_id 边界）。
     * <p>为什么这样写：详情是投标资格审核的证据视图，"核查结论 + 依据标准版本"必须同屏给出，否则无法追溯依据。
     * <p>业务目标：一屏拿全主档、影像来源与最近核查，减少往返并避免快照不一致。
     *
     * @param id 资质主档 id，不存在抛 {@code QUALIFICATION_NOT_EXISTS}
     */
    QualificationDetailRespVO getQualification(Long id);

    /**
     * 延续申报单分页（页签 2）：按企业主体 + 资质/类型/状态筛选，提交日期倒序。
     *
     * <p>规范出处：契约 §4 G3；DDL {@code qualification_apply_record}（表 15-7）。
     * <p>为什么这样写：审批按状态分泳道各查一次，"全部"视图不传 status；出参带 version 供审批动作直接回传。
     * <p>业务目标：申报进度可见、审批动作可直接发起。
     */
    PageResult<QualificationApplyRespVO> getApplyPage(QualificationApplyPageReqVO reqVO);
}
