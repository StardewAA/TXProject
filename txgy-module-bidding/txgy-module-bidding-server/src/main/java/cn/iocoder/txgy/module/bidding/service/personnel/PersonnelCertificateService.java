package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificatePageReqVO;
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

    /** 某员工全部在册证书。 */
    List<PersonnelCertRespVO> listByPerson(Long personId);

    /** 企业维度的证书到期清单（today ~ today+days 内到期）。 */
    List<PersonnelCertRespVO> listExpiring(Long entId, int days);

    /** 按证书细分统计可用持证人数（安许证 A/B/C 三类人员达标统计用）。 */
    Map<String, Integer> countUsableBySubType(Long entId, String certType);
}
