package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelOverviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfilePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelProfileStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;

/**
 * 员工档案服务（人员主数据：建档 / 检索 / 状态流转 / 资质全景）。
 *
 * <p>规范出处：用户业务口径"建立员工资质管理模块：记录员工基本信息、资质证书、培训记录等，实现证书信息
 * 的集中管理和动态更新"；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（人员要素口径：
 * 在职状态/社保证明/学历职称/工作年限）、表 15-6（人员风险判定需在/离职与社保口径）；
 * 《招投标模块开发规范》§4.3（Service 编排 + 校验）、§4.5（状态流转走接口 + 乐观锁）。
 * <p>为什么这样写：员工是证书/社保/合同/培训三类证据的挂载点——先把主数据做扎实，后续到期预警与一致性
 * 核验才有稳定的主体可依；全景视图一次聚合返回，避免各页面各自拼装导致口径不一致。
 * <p>业务目标：把"员工有没有证、证还有效吗、社保在不在本公司"从散落的 Excel 变成一处可查、可预警的主数据。
 */
public interface PersonnelProfileService {

    /** 建档：企业内员工编号唯一 + 身份证盲索引唯一（一人一档）。返回主键。 */
    Long createProfile(PersonnelProfileSaveReqVO reqVO);

    /** 更新档案：乐观锁 CAS，禁止通过本接口改身份证有效期以外的身份信息明文。 */
    void updateProfile(PersonnelProfileSaveReqVO reqVO);

    /** 在职状态流转（入职/离职/停职/复职），非法迁移抛 PERSONNEL_STATUS_TRANSITION_INVALID。 */
    void changeStatus(PersonnelProfileStatusChangeReqVO reqVO);

    /** 档案详情（含身份证掩码、证书计数）。 */
    PersonnelProfileDO getProfile(Long id);

    /** 分页检索（部门/状态/证书类型等条件）。 */
    PageResult<PersonnelProfileDO> pageProfile(PersonnelProfilePageReqVO reqVO);

    /** 员工资质全景：档案 + 证书 + 社保 + 合同 + 培训学时 + 风险提示。 */
    PersonnelOverviewRespVO getOverview(Long personId);
}
