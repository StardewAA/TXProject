package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeContractService;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractRenderService;
import cn.iocoder.txgy.module.hrm.service.employee.config.HrmEmployeeFieldConfigService;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum.FINAL_INTERVIEW_PASSED;
import static cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum.PASSED;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class HrmRecruitOfferServiceImpl implements HrmRecruitOfferService {
    @Resource private HrmRecruitOfferMapper offerMapper;
    @Resource private HrmRecruitApplicationService applicationService;
    @Resource private HrmEmployeeService employeeService;
    @Resource private HrmRecruitCandidateService candidateService;
    @Resource private HrmRecruitPostService postService;
    @Resource private cn.iocoder.txgy.module.hrm.service.recruit.demand.HrmRecruitDemandService recruitDemandService;
    @Resource private cn.iocoder.txgy.module.hrm.service.employee.blacklist.HrmEmployeeBlacklistService employeeBlacklistService;
    @Resource private HrmEmployeeContractService employeeContractService;
    @Resource private HrmEmployeeFieldConfigService employeeFieldConfigService;
    @Resource private HrmContractRenderService contractRenderService;
    @Resource private FileApi fileApi;
    @Resource private NotifyMessageSendApi notifyMessageSendApi;
    @Resource private cn.iocoder.txgy.module.hrm.service.employment.HrmEmploymentTypeService employmentTypeService;
    @Resource private cn.iocoder.txgy.module.hrm.service.recruit.offer.HrmOfferPdfService offerPdfService;
    @Resource private cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService companyProfileService;
    @Resource private cn.iocoder.txgy.module.system.api.mail.MailSendApi mailSendApi;
    @Resource private cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeOnboardDocService onboardDocService;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Boolean approved, String reason) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        // 未接入 BPM 流程实例时的人工审批：使用带前缀的流程号占位，复用原回调逻辑（状态机与幂等不变）
        String processInstanceId = "MANUAL-" + cn.iocoder.txgy.framework.security.core.util
                .SecurityFrameworkUtils.getLoginUserId();
        approvalCallback(id, processInstanceId, offer.getApprovalVersion(),
                Boolean.TRUE.equals(approved), reason);
    }

    @Override
    public void resendContractNotice(Long offerId) {
        HrmRecruitOfferDO offer = offerMapper.selectById(offerId);
        if (offer == null) {
            throw exception(RECRUIT_OFFER_NOT_EXISTS);
        }
        if (offer.getEmployeeId() == null) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        prepareContractForSignature(offerId, offer.getEmployeeId());
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
        // 发放 Offer 时自动生成劳务合同草稿（员工入职建档后绑定员工编号）
        employeeContractService.createDraftForOffer(offer);
        // 3. 给候选人发送录用通知邮件（正文 + Offer 通知 PDF 附件）
        //    失败只记日志、不阻断 Offer 发放：邮件发不出不该让招聘流程卡住，HR 可在候选人详情重发
        sendOfferNoticeMail(offer);
    }

    @Override
    public void resendOfferNotice(Long offerId) {
        HrmRecruitOfferDO offer = requiredForUpdate(offerId);
        // 只有已经发出去的 Offer 才有「重发录用通知」的语义
        if (!HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())
                && !HrmRecruitOfferApprovalStatusEnum.CONFIRMED.getStatus().equals(offer.getApprovalStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        sendOfferNoticeMail(offer);
    }

    /**
     * 发放 Offer 后给候选人发送录用通知邮件（附件为系统生成的 Offer 通知 PDF）
     *
     * <p>候选人邮箱为空时跳过并记日志；邮件链路异常同样不抛出，避免影响 Offer 状态流转。</p>
     */
    private void sendOfferNoticeMail(HrmRecruitOfferDO offer) {
        try {
            HrmRecruitApplicationDO application = applicationService.getApplication(offer.getApplicationId());
            if (application == null) {
                return;
            }
            HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidate(application.getCandidateId());
            if (candidate == null || StrUtil.isBlank(candidate.getEmail())) {
                log.warn("[sendOfferNoticeMail][Offer({}) 候选人({}) 邮箱为空，跳过 Offer 通知邮件]",
                        offer.getId(), candidate == null ? null : candidate.getId());
                return;
            }
            // 1. 生成 Offer 通知 PDF 并上传到文件服务（跨服务邮件只能带附件地址）
            Long postId = offer.getEntryPostId() != null ? offer.getEntryPostId() : offer.getPostId();
            HrmRecruitPostDO post = postId != null ? postService.getRecruitPost(postId) : null;
            cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO company =
                    companyProfileService.getDefaultProfile();
            byte[] pdf = offerPdfService.renderOfferPdf(offer, candidate, post, company);
            String fileName = offerPdfService.buildFileName(candidate.getName(), offer.getOfferNo());
            String fileUrl = fileApi.createFile(pdf, fileName, "hrm/offer", "application/pdf");
            // 2. 组装邮件模板参数（与邮件模板 hrm_offer_notice 的 params 一一对应）
            Map<String, Object> params = new HashMap<>();
            params.put("candidateName", StrUtil.blankToDefault(candidate.getName(), "候选人"));
            params.put("companyName", company != null ? company.getName() : "用工单位");
            params.put("postName", post != null ? StrUtil.blankToDefault(post.getPostName(), "-") : "-");
            params.put("plannedOnboardDate", offer.getPlannedOnboardDate() != null
                    ? offer.getPlannedOnboardDate().toString() : "以双方约定为准");
            params.put("validUntil", offer.getValidUntil() != null ? offer.getValidUntil().toString() : "-");
            params.put("salaryAmount", offer.getSalaryAmount() != null
                    ? offer.getSalaryAmount().stripTrailingZeros().toPlainString() : "面议");
            // 3. 发送（只发给候选人本人，不关联任何后台用户，避免把 HR 自己的邮箱也带上）
            Long mailLogId = mailSendApi.sendSingleMailToAddresses(
                    new cn.iocoder.txgy.module.system.api.mail.dto.MailSendToAddressesReqDTO()
                            .setToMails(java.util.Collections.singletonList(candidate.getEmail()))
                            .setTemplateCode(MessageTemplateConstants.OFFER_NOTICE)
                            .setTemplateParams(params)
                            .setAttachmentUrls(java.util.Collections.singletonList(fileUrl))).getCheckedData();
            log.info("[sendOfferNoticeMail][Offer({}) 已发送录用通知邮件，mailLogId({})，附件({})]",
                    offer.getId(), mailLogId, fileUrl);
        } catch (Throwable ex) {
            log.warn("[sendOfferNoticeMail][Offer({}) 发送录用通知邮件失败]", offer.getId(), ex);
        }
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
        // 1.1 转员工会用到候选人手机号（同时作为员工档案与自动开户的联系方式），格式不合法时给出明确提示
        if (StrUtil.isBlank(candidate.getMobile()) || !candidate.getMobile().matches("^1[3-9]\\d{9}$")) {
            throw exception(RECRUIT_OFFER_CANDIDATE_MOBILE_INVALID);
        }
        // 1.1.1 黑名单拦截：严重违纪 / 重大安全违规等人员不允许再录用
        employeeBlacklistService.validateNotBlacklisted(candidate.getName(), candidate.getMobile(), null);
        // 1.2 用工形式必填且必须可用：它决定签哪份合同、按什么方式计薪（未绑定模板会在此拦下）
        cn.iocoder.txgy.module.hrm.dal.dataobject.employment.HrmEmploymentTypeDO employmentType =
                employmentTypeService.validateUsableForOnboard(reqVO.getEmploymentTypeId());
        // 2.1 入职职位优先取 Offer 指定的入职职位，其次取应聘职位；用于带出部门、岗位与直属上级
        Long postId = offer.getEntryPostId() != null ? offer.getEntryPostId() : application.getPostId();
        HrmRecruitPostDO post = postId != null ? postService.getRecruitPost(postId) : null;
        // 2.2 工号由工号规则引擎自动生成（不再使用 REC-<offerId> 占位）
        HrmEmployeeSaveReqVO employee = new HrmEmployeeSaveReqVO().setName(candidate.getName()).setMobile(candidate.getMobile())
                .setSex(candidate.getSex()).setEmail(candidate.getEmail())
                .setHighestEducation(candidate.getEducation())
                .setCandidateId(candidate.getId()).setChannelId(candidate.getChannelId())
                .setType(reqVO.getType())
                .setEmploymentTypeId(employmentType.getId())
                .setEntryStatus(reqVO.getEntryStatus()).setEntryTime(reqVO.getEntryTime()).setStatus(reqVO.getStatus())
                .setProbation(0);
        if (post != null) {
            employee.setDeptId(post.getDeptId()).setPostName(post.getPostName())
                    .setLeaderEmployeeId(post.getOwnerEmployeeId());
            // 需求单已到齐 / 已取消时不允许继续录用
            recruitDemandService.validateRecruitDemandCanRecruit(post.getDemandId());
        }
        // 2.2.1 剔除当前租户"未显示"的字段，避免自动建档被字段显隐校验拦截
        employeeFieldConfigService.clearInvisibleCreateFields(employee, employee.getEntryStatus());
        Long employeeId = employeeService.createEmployee(employee);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId()).setEmployeeId(employeeId));
        // 用工需求单回写：到岗人数 +1，到齐自动置为「已完成」（失败不阻断入职）
        if (post != null && post.getDemandId() != null) {
            try {
                recruitDemandService.increaseOnboardCount(post.getDemandId());
            } catch (Throwable ex) {
                log.warn("[onboard][需求单({})回写到岗人数失败]", post.getDemandId(), ex);
            }
        }
        // 2.2.2 自动生成员工专属档案文档（个人基本信息档案 / 入职档案登记表 / 岗前培训协议）
        //       失败只记日志：档案文档缺失不该阻断入职建档，HR 可在员工档案里手动重新生成
        try {
            onboardDocService.generateOnboardDocs(employeeId);
        } catch (Throwable ex) {
            log.warn("[onboard][员工({}) 生成入职档案文档失败]", employeeId, ex);
        }
        // 2.3 把发 Offer 时生成的合同草稿绑定到该员工
        employeeContractService.bindEmployeeByOfferId(offer.getId(), employeeId, reqVO.getEntryTime());
        // 2.3.1 按用工形式切换合同模板：发 Offer 时用的是租户默认模板，用工形式选定后要换成它绑定的模板
        employeeContractService.applyTemplateByEmploymentType(offer.getId(), employmentType.getContractTemplateId());
        // 2.4 生成劳务合同文件，并给员工发送「待签署」站内信
        prepareContractForSignature(offer.getId(), employeeId);
        if (HrmEmployeeEntryStatusEnum.ACTIVE.getStatus().equals(reqVO.getEntryStatus())) {
            applicationService.markOnboarded(application.getId(), employeeId);
        } else {
            applicationService.linkEmployeeForOnboard(application.getId(), employeeId);
        }
        return employeeId;
    }

    /**
     * 转员工后：渲染并归档劳务合同文件，同时给员工发送待签署通知
     *
     * <p>任一步失败只记日志，不阻断入职建档（HR 仍可在员工合同页下载/重试）。</p>
     */
    private void prepareContractForSignature(Long offerId, Long employeeId) {
        try {
            // 1. 找到该 Offer 生成的合同
            HrmEmployeeContractDO contract = employeeContractService.getContractBySourceOfferId(offerId);
            if (contract == null) {
                return;
            }
            // 2. 渲染合同文件并上传到文件服务，回写合同附件
            byte[] content = contractRenderService.renderDocx(contract.getId());
            String fileName = contractRenderService.buildFileName(contract.getId());
            String fileUrl = fileApi.createFile(content, fileName, "hrm/contract",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            employeeContractService.markGeneratedFile(contract.getId(), fileUrl, "合同文件已生成，待签署");
            // 3. 给员工（其绑定的后台账号）发送待签署站内信
            HrmEmployeeDO employee = employeeService.validateEmployeeExists(employeeId);
            if (employee.getUserId() != null) {
                Map<String, Object> templateParams = new HashMap<>();
                templateParams.put("contractNo",
                        StrUtil.blankToDefault(contract.getNo(), String.valueOf(contract.getId())));
                notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                        .setUserId(employee.getUserId())
                        .setTemplateCode(MessageTemplateConstants.CONTRACT_PENDING_SIGN)
                        .setTemplateParams(templateParams));
            }
        } catch (Throwable ex) {
            log.warn("[prepareContractForSignature][offerId({}) employeeId({}) 生成合同文件或发送签署通知失败]",
                    offerId, employeeId, ex);
        }
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
