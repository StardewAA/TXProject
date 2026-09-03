package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitOfferMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitOfferApprovalStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.*;

@Service
public class HrmRecruitOfferServiceImpl implements HrmRecruitOfferService {
    @Resource private HrmRecruitOfferMapper offerMapper;
    @Resource private HrmRecruitApplicationService applicationService;
    @Resource private HrmEmployeeService employeeService;
    @Resource private HrmRecruitCandidateService candidateService;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(HrmRecruitOfferSaveReqVO reqVO) {
        HrmRecruitApplicationDO application = applicationService.getApplication(reqVO.getApplicationId());
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        if (!"PASSED".equals(application.getStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        if (offerMapper.selectByApplicationId(reqVO.getApplicationId()) != null) throw exception(RECRUIT_OFFER_DUPLICATE);
        HrmRecruitOfferDO offer = BeanUtils.toBean(reqVO, HrmRecruitOfferDO.class)
                .setOfferNo("OFF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus()).setApprovalVersion(0);
        offerMapper.insert(offer); return offer.getId();
    }
    @Override public HrmRecruitOfferDO get(Long id) { return offerMapper.selectById(id); }
    @Override @Transactional(rollbackFor = Exception.class) public void submit(Long id) {
        updateStatus(id, HrmRecruitOfferApprovalStatusEnum.DRAFT, HrmRecruitOfferApprovalStatusEnum.APPROVING);
        applicationService.markOfferApproving(required(id).getApplicationId());
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void approvalCallback(Long id, String processInstanceId, Integer version, boolean approved) {
        HrmRecruitOfferDO offer = required(id);
        if ((HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus().equals(offer.getApprovalStatus())
                || HrmRecruitOfferApprovalStatusEnum.REJECTED.getStatus().equals(offer.getApprovalStatus()))
                && java.util.Objects.equals(offer.getProcessInstanceId(), processInstanceId)
                && java.util.Objects.equals(offer.getApprovalVersion(), version)) return;
        if (!HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())
                || !java.util.Objects.equals(offer.getApprovalVersion(), version)) throw exception(RECRUIT_OFFER_STATE_INVALID);
        HrmRecruitOfferDO update = new HrmRecruitOfferDO().setId(id).setProcessInstanceId(processInstanceId)
                .setApprovalStatus(approved ? HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus() : HrmRecruitOfferApprovalStatusEnum.REJECTED.getStatus());
        if (offerMapper.updateApprovalWithVersion(id, version, update) == 0) throw exception(RECRUIT_OFFER_STATE_INVALID);
        if (!approved) applicationService.markOfferRejected(offer.getApplicationId());
    }
    @Override @Transactional(rollbackFor = Exception.class) public void withdraw(Long id) {
        HrmRecruitOfferDO offer = required(id);
        if (!HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus().equals(offer.getApprovalStatus())
                && !HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id).setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.WITHDRAWN.getStatus()));
    }
    @Override @Transactional(rollbackFor = Exception.class) public void send(Long id) {
        HrmRecruitOfferDO offer = required(id);
        if (!HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id).setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.SENT.getStatus()).setSentTime(LocalDateTime.now()));
        applicationService.markOfferSent(offer.getApplicationId());
    }
    @Override @Transactional(rollbackFor = Exception.class) public void confirm(Long id) {
        HrmRecruitOfferDO offer = required(id);
        if (!HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id).setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.CONFIRMED.getStatus()).setConfirmTime(LocalDateTime.now()));
        applicationService.markPendingOnboard(offer.getApplicationId());
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Long onboard(HrmRecruitOfferOnboardReqVO reqVO) {
        HrmRecruitOfferDO offer = required(reqVO.getOfferId());
        if (offer.getEmployeeId() != null) return offer.getEmployeeId();
        if (!HrmRecruitOfferApprovalStatusEnum.CONFIRMED.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        HrmRecruitApplicationDO application = applicationService.getApplication(offer.getApplicationId());
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        if (application.getEmployeeId() != null) return application.getEmployeeId();
        HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidate(application.getCandidateId());
        HrmEmployeeSaveReqVO employee = new HrmEmployeeSaveReqVO().setName(candidate.getName()).setMobile(candidate.getMobile())
                .setSex(candidate.getSex()).setAge(candidate.getAge()).setEmail(candidate.getEmail()).setCandidateId(candidate.getId())
                .setType(reqVO.getType()).setEntryStatus(reqVO.getEntryStatus()).setEntryTime(reqVO.getEntryTime()).setStatus(reqVO.getStatus());
        Long employeeId = employeeService.createEmployee(employee);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId()).setEmployeeId(employeeId));
        applicationService.markOnboarded(application.getId(), employeeId);
        return employeeId;
    }
    private void updateStatus(Long id, HrmRecruitOfferApprovalStatusEnum from, HrmRecruitOfferApprovalStatusEnum to) {
        HrmRecruitOfferDO offer = required(id);
        if (!from.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id).setApprovalStatus(to.getStatus())
                .setApprovalVersion((offer.getApprovalVersion() == null ? 0 : offer.getApprovalVersion()) + 1));
    }
    private HrmRecruitOfferDO required(Long id) { HrmRecruitOfferDO offer = offerMapper.selectById(id); if (offer == null) throw exception(RECRUIT_OFFER_NOT_EXISTS); return offer; }
}
