package cn.iocoder.txgy.module.bidding.service.qualification;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAssetRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationElementPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPerformanceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStaffRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationExportVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStatsRespVO;

import java.time.LocalDate;
import java.util.List;

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
     * 主档修改（详情页内联编辑；入参 id/version 必传）。
     *
     * <p>规范出处：契约《企业资质页-页面级契约-v1.0.md》§4 G2（详情/编辑同页）；《招投标模块开发规范》
     * §4.5（乐观锁 CAS）、§5 三-7（code 存储）。
     * <p>为什么这样写：① 乐观锁必须回传 version——详情页取到的版本与库内不一致即拒（{@code DATA_MODIFIED}），
     * 否则并发编辑会静默覆盖他人修改；② UK（ent+serial+category+grade+cert_no）判重需**排除自身**，
     * 否则"只改了备注、UK 四字段没动"的保存会把自己判成重复；③ **qualStatus 不进入参**：状态由状态机
     * （{@link #changeStatus}）与延续申报流管理，本接口只做"日期推导态"的重算（见下），
     * 否则客户端可绕过状态机直接改状态；④ valid_to 变更后必须重算 VALID/EXPIRING_SOON/EXPIRED，
     * 否则"过期资质延期后仍显示已过期"且**永不自愈**——到期扫描对已过期记录是
     * {@code days <= 0 → continue} 直接跳过，只在窗口内升为 EXPIRING_SOON，不会把 EXPIRED 改回 VALID。
     * <p>业务目标：详情页能就地改正录错的有效期/编号/机关，且状态与有效期始终自洽、并发编辑不互相覆盖。
     *
     * @param reqVO 主档字段；{@code id} 与 {@code version} 必传（此 VO 未加 {@code @NotNull}，由本方法兜底校验）
     * @return 主档 id
     */
    Long updateQualification(EnterpriseQualificationSaveReqVO reqVO);

    /**
     * 资质看板统计（五页改造契约 Q5）：按企业主体聚合 total/valid/expiring/expired/unlinkedStaff；
     * 口径与 page 同源（同 entId 同 deleted），保证看板数字与列表条数一致。
     */
    QualificationStatsRespVO getQualificationStats(Long entId);

    /**
     * 资质 Excel 导入（五页改造契约 Q6，2026-09-28）：逐行建档，行级回执——
     * 单行失败不影响其余；entId 按一租户一主体档案由后端推导。
     */
    QualificationImportRespVO importQualifications(List<QualificationImportExcelVO> rows, Long operatorId);

    /**
     * 资质导出（五页改造契约 Q6，2026-09-28）：范围=当前筛选，与 page 同过滤口径（不做分页截断）。
     */
    List<QualificationExportVO> exportQualifications(QualificationPageReqVO reqVO);

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

    /**
     * 资质标准分页（页签 3）：查达标核查所依据的版本化标准。
     *
     * <p>规范出处：契约 §4 G4；表 15-2；`资质管理/02-法规标准与核验渠道.md` line 37
     * （资质改革过渡期，「做资质类别/等级标准库时不得静态固化」）。
     * <p>为什么这样写：筛选项全可空（查依据场景）；{@code onlyEffective} 按生效区间判定，
     * 换版只改数据不改代码。
     * <p>业务目标：核查依据可查、可按版本追溯。
     */
    PageResult<QualificationStandardRespVO> getStandardPage(QualificationStandardPageReqVO reqVO);

    /**
     * 资质维度选项（序列 → 类别 → 等级 三级级联）：建档表单与筛选下拉的数据源。
     *
     * <p>规范出处：同上（不固化）；与 §3.5 资质标准登记配套。
     * <p>为什么这样写：维度取自**现行标准库**而非固定字典——改革压减/合并资质类别时只改数据；
     * 三级嵌套避免前端自行推断层级、也防止跨序列串类。
     * <p>业务目标：页面可选维度始终与库内现行标准一致。
     */
    QualificationStandardOptionsRespVO getStandardOptions();

    /**
     * 要素台账 · 人员配置分页（页签 3）。
     *
     * <p>规范出处：契约 §4 G5；表 15-3。
     * <p>为什么这样写：出参**不含** id_card_enc / id_card_blind_index —— 身份证密文与盲索引一律不出接口
     * （L4 加密的最小必要字段原则），前端需要证件时跳证照台账（bindedCertId）。
     * <p>业务目标：要素可按人核对，同时不给下游身份证件旁路。
     */
    PageResult<QualificationStaffRespVO> getStaffPage(QualificationElementPageReqVO reqVO);

    /** 要素台账 · 工程业绩分页（契约 §4 G5；表 15-4）。 */
    PageResult<QualificationPerformanceRespVO> getPerformancePage(QualificationElementPageReqVO reqVO);

    /** 要素台账 · 资产/设备分页（契约 §4 G5；表 15-5；资产按统计年度取值）。 */
    PageResult<QualificationAssetRespVO> getAssetPage(QualificationElementPageReqVO reqVO);

    /**
     * 达标核查历史分页（页签 3）。
     *
     * <p>规范出处：契约 §4 G6；表 15-6（只追加）。
     * <p>为什么这样写：出参带 stdVersion 与 elementSnapshot，使历史结论"可追溯依据、可复算"，
     * 不受此后要素台账修改影响。
     * <p>业务目标：核查结论可作为投标资格审核证据。
     */
    PageResult<QualificationReviewRespVO> getReviewPage(QualificationReviewPageReqVO reqVO);

    /** 安许证台账分页（页签 4；契约 §4 G7；含风险三件套与宽限登记）。 */
    PageResult<QualificationPermitRespVO> getPermitPage(QualificationPermitPageReqVO reqVO);

    /** 安许证详情（契约 §4 G7）。不存在抛 {@code SAFETY_PERMIT_NOT_EXISTS}（1090109024）。 */
    QualificationPermitRespVO getPermit(Long id);

    /**
     * 资质到期预警清单分页（页签 4）。
     *
     * <p>规范出处：契约 §4 G8；cert_alert_records（alertType=QUAL_EXPIRY 三源）。
     * <p>为什么这样写：预警表本身**没有企业字段**（只有 refType/refId 指向业务对象），
     * 因此按企业过滤时先取该企业的资质/安许 id，再按 (refType, refId) 成对匹配 —— 只按 refId 匹配会串对象
     * （不同表的 id 可能相同）。
     * <p>业务目标：预警可看、可跳转、可闭环。
     */
    PageResult<QualificationAlertRespVO> getAlertPage(QualificationAlertPageReqVO reqVO);
}
