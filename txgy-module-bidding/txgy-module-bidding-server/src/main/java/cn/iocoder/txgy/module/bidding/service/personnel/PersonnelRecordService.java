package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelLaborContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelSocialSecuritySaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelTrainingSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelLaborContractDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelSocialSecurityDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelTrainingRecordDO;

import java.util.List;

/**
 * 员工附属履历服务（培训记录 / 社保缴纳 / 劳动合同 三类证据登记与查询）。
 *
 * <p>规范出处：用户业务口径"记录员工基本信息、资质证书、培训记录等"+"核验员工社保缴纳单位、劳动合同主体
 * 是否与申报企业完全一致"；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（社保/劳动合同=挂靠核查证据）；
 * 《劳动合同法》第十条（建立劳动关系应订立书面劳动合同）；《社会保险法》第五十八条（用人单位应依法参保）。
 * <p>为什么这样写（三类合一个服务）：它们都是"员工档案的从属证据"，形态一致（登记 → 按人查询 → 校验主体一致），
 * 合并可避免三个近乎空壳的服务类，也让"一致性核验"的取数口径集中在一处。
 * <p>业务目标：社保与合同可追溯、可按月核对，杜绝"人证分离""挂证"。
 */
public interface PersonnelRecordService {

    /** 登记培训记录（继续教育学时可累计，支撑资质人员指标）。 */
    Long createTraining(PersonnelTrainingSaveReqVO reqVO);

    /** 某员工培训履历（按结束日期倒序）。 */
    List<PersonnelTrainingRecordDO> listTraining(Long personId);

    /** 登记社保缴纳记录（同人同月仅一条，重复登记报错）。 */
    Long createSocialSecurity(PersonnelSocialSecuritySaveReqVO reqVO);

    /** 某员工社保缴纳履历（按缴纳月份倒序）。 */
    List<PersonnelSocialSecurityDO> listSocialSecurity(Long personId);

    /** 登记劳动合同（同人同合同编号唯一）。 */
    Long createLaborContract(PersonnelLaborContractSaveReqVO reqVO);

    /** 某员工劳动合同列表。 */
    List<PersonnelLaborContractDO> listLaborContract(Long personId);
}
