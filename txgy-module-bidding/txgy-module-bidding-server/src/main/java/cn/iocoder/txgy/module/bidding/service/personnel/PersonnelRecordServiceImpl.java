package cn.iocoder.txgy.module.bidding.service.personnel;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelLaborContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelSocialSecuritySaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelTrainingSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelLaborContractDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelProfileDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelSocialSecurityDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.personnel.PersonnelTrainingRecordDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelLaborContractMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelProfileMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelSocialSecurityMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.personnel.PersonnelTrainingRecordMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 员工附属履历服务实现（培训 / 社保 / 劳动合同）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（社保/劳动合同=挂靠核查证据、edu_hours 学时要素）；
 * 《劳动合同法》第十条、《社会保险法》第五十八条（书面合同与依法参保为法定义务）；
 * 《招投标模块开发规范》§4.3（登记前校验主数据存在）、§4.5（幂等：同人同月社保、同人同合同号唯一）。
 * <p>设计遵循/针对：① 三类记录都先校验员工档案存在，避免挂出"无主证据"；
 * ② 社保按"人 + 缴纳月份 + 缴纳单位"、合同按"人 + 合同编号 + 用人单位"做业务键判重（DDL UK 兜底），
 * 保证月度核对时不会出现同月两条互相矛盾的缴纳记录；③ 一律不改写历史记录（更正走新增，保留证据链）。
 * <p>业务目标：社保与劳动合同可按月/按份核对，为"人证合一、防挂证"提供不可篡改的证据。
 */
@Slf4j
@Service
public class PersonnelRecordServiceImpl implements PersonnelRecordService {

    @Resource
    private PersonnelProfileMapper personnelProfileMapper;
    @Resource
    private PersonnelTrainingRecordMapper personnelTrainingRecordMapper;
    @Resource
    private PersonnelSocialSecurityMapper personnelSocialSecurityMapper;
    @Resource
    private PersonnelLaborContractMapper personnelLaborContractMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTraining(PersonnelTrainingSaveReqVO reqVO) {
        validatePerson(reqVO.getPersonId());
        PersonnelTrainingRecordDO record = new PersonnelTrainingRecordDO();
        record.setEntId(reqVO.getEntId());
        record.setPersonId(reqVO.getPersonId());
        record.setTrainingName(reqVO.getTrainingName());
        record.setTrainingType(reqVO.getTrainingType());
        record.setOrganizer(reqVO.getOrganizer());
        record.setStartDate(reqVO.getStartDate());
        record.setEndDate(reqVO.getEndDate());
        record.setEduHours(reqVO.getEduHours());
        record.setResult(reqVO.getResult());
        record.setScore(reqVO.getScore());
        record.setCertNo(reqVO.getCertNo());
        record.setCertValidTo(reqVO.getCertValidTo());
        record.setFileId(reqVO.getFileId());
        record.setRemark(reqVO.getRemark());
        personnelTrainingRecordMapper.insert(record);
        log.info("[personnel-training][create] personId={} name={} id={}",
                record.getPersonId(), record.getTrainingName(), record.getId());
        return record.getId();
    }

    @Override
    public List<PersonnelTrainingRecordDO> listTraining(Long personId) {
        return personnelTrainingRecordMapper.selectByPerson(personId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSocialSecurity(PersonnelSocialSecuritySaveReqVO reqVO) {
        validatePerson(reqVO.getPersonId());
        // 同人同月同单位只允许一条：月度核对若出现两条互相矛盾的记录，挂证核查结论将不可复算
        boolean duplicated = personnelSocialSecurityMapper.selectByPerson(reqVO.getPersonId()).stream()
                .anyMatch(r -> reqVO.getPayMonth().equals(r.getPayMonth())
                        && Objects.equals(r.getEntId(), reqVO.getEntId()));
        if (duplicated) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_RECORD_DUPLICATE);
        }
        PersonnelSocialSecurityDO record = new PersonnelSocialSecurityDO();
        record.setEntId(reqVO.getEntId());
        record.setPersonId(reqVO.getPersonId());
        record.setPayerName(reqVO.getPayerName());
        record.setPayerCreditCode(reqVO.getPayerCreditCode());
        record.setPayMonth(reqVO.getPayMonth());
        record.setPayStatus(reqVO.getPayStatus());
        record.setPayBase(reqVO.getPayBase());
        record.setPayAmount(reqVO.getPayAmount());
        record.setSocialNo(reqVO.getSocialNo());
        record.setSourceChannel(reqVO.getSourceChannel());
        record.setEvidenceFileId(reqVO.getEvidenceFileId());
        record.setRemark(reqVO.getRemark());
        personnelSocialSecurityMapper.insert(record);
        return record.getId();
    }

    @Override
    public List<PersonnelSocialSecurityDO> listSocialSecurity(Long personId) {
        return personnelSocialSecurityMapper.selectByPerson(personId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLaborContract(PersonnelLaborContractSaveReqVO reqVO) {
        validatePerson(reqVO.getPersonId());
        boolean duplicated = personnelLaborContractMapper.selectByPerson(reqVO.getPersonId()).stream()
                .anyMatch(r -> reqVO.getContractNo().equals(r.getContractNo())
                        && Objects.equals(r.getEntId(), reqVO.getEntId()));
        if (duplicated) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_RECORD_DUPLICATE);
        }
        PersonnelLaborContractDO record = new PersonnelLaborContractDO();
        record.setEntId(reqVO.getEntId());
        record.setPersonId(reqVO.getPersonId());
        record.setContractNo(reqVO.getContractNo());
        record.setContractType(reqVO.getContractType());
        record.setSignDate(reqVO.getSignDate());
        record.setStartDate(reqVO.getStartDate());
        record.setEndDate(reqVO.getEndDate());
        record.setContractStatus(reqVO.getContractStatus() == null ? "ACTIVE" : reqVO.getContractStatus());
        record.setPositionName(reqVO.getPositionName());
        record.setFileId(reqVO.getFileId());
        record.setRemark(reqVO.getRemark());
        personnelLaborContractMapper.insert(record);
        return record.getId();
    }

    @Override
    public List<PersonnelLaborContractDO> listLaborContract(Long personId) {
        return personnelLaborContractMapper.selectByPerson(personId);
    }

    // ========== 内部 ==========

    private PersonnelProfileDO validatePerson(Long personId) {
        PersonnelProfileDO profile = personId == null ? null : personnelProfileMapper.selectById(personId);
        if (profile == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PERSONNEL_NOT_EXISTS);
        }
        return profile;
    }
}
