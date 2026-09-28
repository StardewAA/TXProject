package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertPageByPersonReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertPersonRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertAlertNotifyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelCertificateDO;

import java.util.List;
import java.util.Map;

/**
 * 员工证书服务（证书集中管理 + 分类检索 + 状态流转）。
 *
 * <p>规范出处：用户业务口径"实现员工证书信息的集中管理、分类检索和动态更新，记录证书的有效期、编号等关键信息
 * 并支持附件上传"；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（注册/执业编号与注册有效期）、
 * 表 1/2（证照事实与文件版本，本域经 binded_cert_id/file_id 复用）；
 * 《注册建造师管理规定》（建设部令第153号）第十条（注册证书有效期与延续）；
 * 《招投标模块开发规范》§4.5（状态流转 + 乐观锁）、§5 三-5（文件不落本域，只存 file_id）。
 * <p>为什么这样写：证书是"能不能投标"的硬证据——保存时必须有有效期（否则到期预警形同虚设），
 * 状态变更必须独立成接口（否则状态被混在字段更新里，事后无法审计"谁把证书改成失效了"）。
 * <p>业务目标：把证书变成"可查、可筛、可预警、可核验"的结构化台账，替代 Excel 登记 + 到期靠人记。
 */
public interface PersonnelCertificateService {

    /** 登记证书（同人同类型同编号唯一；有有效期的证书类型必须填有效期，否则预警失效）。返回主键。 */
    Long createCertificate(PersonnelCertificateSaveReqVO reqVO);

    /**
     * 员工证书 Excel 导入（契约 e2e P2）。
     *
     * <p>逐行登记、**行级回执**、单个失败不影响其余（与证照/资质导入同口径）；
     * 每行复用 {@link #createCertificate} 的校验，故"同人同类型同编号唯一""有效期必填"等口径一致。
     *
     * @param rows  解析后的 Excel 行（行号从 2 起，表头为第 1 行）
     * @param entId 归属企业（由 Controller 按本租户主体档案推导；null 时 Service 内再兜底）
     * @return 行级回执
     */
    cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportRespVO
            importCertificates(List<cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportExcelVO> rows,
                               Long entId);

    /** 更新证书（乐观锁 CAS）。 */
    void updateCertificate(PersonnelCertificateSaveReqVO reqVO);

    /** 证书注销/吊销（走显式状态迁移，禁止直接改库）。 */
    void changeStatus(Long certId, String targetStatus, Integer version, String reason);

    /** 删除证书（软删，保留历史）。 */
    void deleteCertificate(Long certId);

    /** 证书详情。 */
    PersonnelCertificateDO getCertificate(Long id);

    /** 分页检索（人/类型/状态/到期窗口）。 */
    PageResult<PersonnelCertRespVO> pageCertificate(PersonnelCertificatePageReqVO reqVO);

    /**
     * 按人维度分页（五页改造契约 Q9）：先筛证书取持证人集合、再按人分页；
     * 出参含该人同筛选下的证书列表、证书数与最近到期日。
     */
    PageResult<PersonnelCertPersonRespVO> pageByPerson(PersonnelCertPageByPersonReqVO reqVO);

    /**
     * 员工证书看板统计（五页改造契约 Q10）：按企业（可选部门）聚合
     * holderCount/certTotal/expiring/expired/unverified。
     */
    PersonnelCertStatsRespVO getCertStats(Long entId, String deptName);

    /** 某员工全部在册证书。 */
    List<PersonnelCertRespVO> listByPerson(Long personId);

    /** 企业维度的证书到期清单（today ~ today+days 内到期）。 */
    List<PersonnelCertRespVO> listExpiring(Long entId, int days);

    /** 按证书细分统计可用持证人数（安许证 A/B/C 三类人员达标统计用）。 */
    Map<String, Integer> countUsableBySubType(Long entId, String certType);

    /**
     * 批量续期（五页改造契约 Q11，2026-09-28）：逐条执行——版本 CAS 校验、新有效期校验、
     * 状态重算；返回**回执对象**（total/successCount/failCount/failures + 逐条 items），单个失败不影响其余。
     * 入参支持 items[]（逐条）或 certIds[]+newValidTo（统一批量）两种模式（2026-09-28 对账补齐）。
     */
    PersonnelCertBatchRenewRespVO batchRenew(PersonnelCertBatchRenewReqVO reqVO, Long operatorId);

    /**
     * 批量到期提醒（五页改造契约 Q12，2026-09-28）：personIds[] 或 certIds[] 二选一，
     * 逐证写通知发件箱（CHECK_REMIND；同日同证不重复，幂等）。
     */
    int notifyExpiring(PersonnelCertAlertNotifyReqVO reqVO, Long operatorId);

    /**
     * 证书级验真（五页改造契约 Q13，2026-09-28）：复用证照域验真渠道——
     * 须已绑定证照台账（bindedCertId），否则报 CERT_NOT_BOUND；验真结论回写 platformVerified/verifiedAt。
     */
    void verifyPersonnelCert(Long certId, String verifyChannel, Long operatorId);
}
