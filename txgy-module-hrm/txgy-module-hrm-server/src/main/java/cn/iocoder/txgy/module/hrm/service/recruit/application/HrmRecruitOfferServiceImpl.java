package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeSaveReqVO;
import cn.iocoder.txgy.module.hrm.enums.employee.info.HrmEmployeeEntryStatusEnum;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitOfferMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitOfferApprovalStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum.FINAL_INTERVIEW_PASSED;
import static cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum.PASSED;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.*;

@Service
public class HrmRecruitOfferServiceImpl implements HrmRecruitOfferService {
    @Resource private HrmRecruitOfferMapper offerMapper;
    @Resource private HrmRecruitApplicationService applicationService;
    @Resource private HrmEmployeeService employeeService;
    @Resource private HrmRecruitCandidateService candidateService;
    @Resource private HrmRecruitPostService postService;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(HrmRecruitOfferSaveReqVO reqVO) {
        HrmRecruitApplicationDO application = applicationService.getApplication(reqVO.getApplicationId());
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        if (!PASSED.getStatus().equals(application.getStatus())
                && !FINAL_INTERVIEW_PASSED.getStatus().equals(application.getStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        if (!Objects.equals(application.getPostId(), reqVO.getPostId())) {
            throw exception(RECRUIT_OFFER_POST_MISMATCH);
        }
        validateOfferData(reqVO);
        if (offerMapper.selectByApplicationId(reqVO.getApplicationId()) != null) throw exception(RECRUIT_OFFER_DUPLICATE);
        if (reqVO.getEntryPostId() != null) {
            postService.validateRecruitPostExists(reqVO.getEntryPostId());
        }
        HrmRecruitOfferDO offer = BeanUtils.toBean(reqVO, HrmRecruitOfferDO.class)
                .setOfferNo("OFF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus()).setApprovalVersion(0);
        offerMapper.insert(offer);
        applicationService.markPendingOffer(application.getId());
        return offer.getId();
    }
    @Override public HrmRecruitOfferDO get(Long id) { return offerMapper.selectById(id); }
    @Override @Transactional(rollbackFor = Exception.class) public void submit(Long id) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus().equals(offer.getApprovalStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        updateStatus(offer, HrmRecruitOfferApprovalStatusEnum.APPROVING);
        applicationService.markOfferApproving(offer.getApplicationId());
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void approvalCallback(Long id, String processInstanceId, Integer version, boolean approved) {
        approvalCallback(id, processInstanceId, version, approved, null);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void approvalCallback(Long id, String processInstanceId, Integer version,
                                 boolean approved, String reason) {
        if (StrUtil.isBlank(processInstanceId) || version == null) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if ((HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus().equals(offer.getApprovalStatus())
                || HrmRecruitOfferApprovalStatusEnum.REJECTED.getStatus().equals(offer.getApprovalStatus()))
                && java.util.Objects.equals(offer.getProcessInstanceId(), processInstanceId)
                && java.util.Objects.equals(offer.getApprovalVersion(), version)) return;
        if (!HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())
                || !java.util.Objects.equals(offer.getApprovalVersion(), version)) throw exception(RECRUIT_OFFER_STATE_INVALID);
        HrmRecruitOfferDO update = new HrmRecruitOfferDO().setId(id).setProcessInstanceId(processInstanceId)
                .setApprovalStatus(approved ? HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus()
                        : HrmRecruitOfferApprovalStatusEnum.REJECTED.getStatus())
                .setRemark(approved ? offer.getRemark() : StrUtil.blankToDefault(reason, "Offer审批未通过"));
        if (offerMapper.updateApprovalWithVersion(id, version, update) == 0) throw exception(RECRUIT_OFFER_STATE_INVALID);
        if (!approved) applicationService.markOfferRejected(offer.getApplicationId(), reason);
    }
    @Override @Transactional(rollbackFor = Exception.class) public void withdraw(Long id) {
        withdraw(id, "招聘方撤回 Offer");
    }

    @Override @Transactional(rollbackFor = Exception.class) public void withdraw(Long id, String reason) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus().equals(offer.getApprovalStatus())
                && !HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())
                && !HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus().equals(offer.getApprovalStatus())
                && !HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        String withdrawReason = StrUtil.blankToDefault(reason, "招聘方撤回 Offer");
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id)
                .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.WITHDRAWN.getStatus())
                .setRemark(withdrawReason));
        applicationService.markOfferWithdrawn(offer.getApplicationId(), withdrawReason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expire(Long id, String reason) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        String expireReason = StrUtil.blankToDefault(reason, "Offer 已失效");
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id)
                .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.EXPIRED.getStatus())
                .setRemark(expireReason));
        applicationService.markOfferExpired(offer.getApplicationId(), expireReason);
    }
    @Override @Transactional(rollbackFor = Exception.class) public void send(Long id) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus().equals(offer.getApprovalStatus())
                || offer.getValidUntil() == null || offer.getValidUntil().isBefore(LocalDate.now())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id)
                .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.SENT.getStatus())
                .setSentTime(LocalDateTime.now()));
        applicationService.markOfferSent(offer.getApplicationId());
    }
    @Override @Transactional(rollbackFor = Exception.class) public void confirm(Long id) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        if (offer.getValidUntil() == null || offer.getValidUntil().isBefore(LocalDate.now())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        offerMapper.updateById(new HrmRecruitOfferDO().setId(id).setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.CONFIRMED.getStatus()).setConfirmTime(LocalDateTime.now()));
        applicationService.markPendingOnboard(offer.getApplicationId());
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptByCandidate(Long id, Long userId) {
        expireOverdueOffers(userId);
        HrmRecruitApplicationDO application = getCandidateOfferApplication(id, userId);
        TenantUtils.execute(application.getTenantId(), () -> {
            HrmRecruitOfferDO offer = requiredForUpdate(id);
            HrmRecruitApplicationDO currentApplication = applicationService.getApplication(offer.getApplicationId());
            if (currentApplication == null || !userId.equals(currentApplication.getApplicantUserId())
                    || !HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) {
                throw exception(RECRUIT_OFFER_STATE_INVALID);
            }
            confirm(id);
        });
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectByCandidate(Long id, Long userId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECRUIT_APPLICATION_REASON_REQUIRED);
        }
        expireOverdueOffers(userId);
        HrmRecruitApplicationDO application = getCandidateOfferApplication(id, userId);
        TenantUtils.execute(application.getTenantId(), () -> {
            HrmRecruitOfferDO offer = requiredForUpdate(id);
            HrmRecruitApplicationDO currentApplication = applicationService.getApplication(offer.getApplicationId());
            if (currentApplication == null || !userId.equals(currentApplication.getApplicantUserId())
                    || !HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) {
                throw exception(RECRUIT_OFFER_STATE_INVALID);
            }
            offerMapper.updateById(new HrmRecruitOfferDO().setId(id)
                    .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.CANDIDATE_REJECTED.getStatus())
                    .setRemark(reason));
            applicationService.markOfferCandidateRejected(offer.getApplicationId(), reason);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireOverdueOffers(Long applicantUserId) {
        List<HrmRecruitOfferDO> overdue = TenantUtils.executeIgnore(
                () -> offerMapper.selectSentBefore(LocalDate.now()));
        for (HrmRecruitOfferDO offer : overdue) {
            HrmRecruitApplicationDO application = TenantUtils.executeIgnore(
                    () -> applicationService.getApplication(offer.getApplicationId()));
            if (application == null
                    || (applicantUserId != null && !applicantUserId.equals(application.getApplicantUserId()))
                    || !HrmRecruitApplicationStatusEnum.OFFER_SENT.getStatus().equals(application.getStatus())
                    || application.getTenantId() == null) {
                continue;
            }
            TenantUtils.execute(application.getTenantId(), () -> {
                applicationService.markOfferExpired(offer.getApplicationId(), "Offer 已超过有效期");
                offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId())
                        .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.EXPIRED.getStatus())
                        .setRemark("Offer 已超过有效期"));
            });
        }
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public Long onboard(HrmRecruitOfferOnboardReqVO reqVO) {
        HrmRecruitOfferDO offer = requiredForUpdate(reqVO.getOfferId());
        if (offer.getEmployeeId() != null) return offer.getEmployeeId();
        if (!HrmRecruitOfferApprovalStatusEnum.CONFIRMED.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        HrmRecruitApplicationDO application = applicationService.getApplication(offer.getApplicationId());
        if (application == null) throw exception(RECRUIT_APPLICATION_NOT_EXISTS);
        if (application.getEmployeeId() != null) return application.getEmployeeId();
        HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidate(application.getCandidateId());
        HrmEmployeeSaveReqVO employee = new HrmEmployeeSaveReqVO().setName(candidate.getName()).setMobile(candidate.getMobile())
                .setCandidateId(candidate.getId()).setJobNumber("REC-" + offer.getId()).setType(reqVO.getType())
                .setEntryStatus(reqVO.getEntryStatus()).setEntryTime(reqVO.getEntryTime()).setStatus(reqVO.getStatus())
                .setProbation(0);
        Long employeeId = employeeService.createEmployee(employee);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId()).setEmployeeId(employeeId));
        if (HrmEmployeeEntryStatusEnum.ACTIVE.getStatus().equals(reqVO.getEntryStatus())) {
            applicationService.markOnboarded(application.getId(), employeeId);
        } else {
            applicationService.linkEmployeeForOnboard(application.getId(), employeeId);
        }
        return employeeId;
    }
    private void updateStatus(HrmRecruitOfferDO offer, HrmRecruitOfferApprovalStatusEnum to) {
        offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId()).setApprovalStatus(to.getStatus())
                .setApprovalVersion((offer.getApprovalVersion() == null ? 0 : offer.getApprovalVersion()) + 1));
    }

    private void validateOfferData(HrmRecruitOfferSaveReqVO reqVO) {
        LocalDate today = LocalDate.now();
        if (reqVO.getSalaryAmount() == null || reqVO.getSalaryAmount().signum() <= 0
                || reqVO.getSalaryAmount().scale() > 2
                || !Set.of(1, 2).contains(reqVO.getSalaryUnit())
                || reqVO.getPlannedOnboardDate() == null || reqVO.getValidUntil() == null
                || reqVO.getPlannedOnboardDate().isBefore(today)
                || reqVO.getValidUntil().isBefore(today)) {
            throw exception(RECRUIT_OFFER_DATA_INVALID);
        }
    }
    private HrmRecruitApplicationDO getCandidateOfferApplication(Long id, Long userId) {
        HrmRecruitOfferDO offer = TenantUtils.executeIgnore(() -> offerMapper.selectById(id));
        if (offer == null) throw exception(RECRUIT_OFFER_NOT_EXISTS);
        HrmRecruitApplicationDO application = TenantUtils.executeIgnore(
                () -> applicationService.getApplication(offer.getApplicationId()));
        if (application == null || !userId.equals(application.getApplicantUserId())
                || application.getTenantId() == null) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        return application;
    }
    private HrmRecruitOfferDO requiredForUpdate(Long id) {
        HrmRecruitOfferDO offer = offerMapper.selectByIdForUpdate(id);
        if (offer == null) throw exception(RECRUIT_OFFER_NOT_EXISTS);
        return offer;
    }
}
