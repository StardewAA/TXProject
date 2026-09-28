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
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.option.HrmSalaryOptionValueVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
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

    /**
     * Offer 审批的 BPM 流程定义标识
     *
     * <p>在「工作流程 → 流程模型」里按这个 key 发布流程后，提交 Offer 会自动发起该审批流程；
     * 未发布时降级为人工审批，保证功能可用。</p>
     */
    public static final String OFFER_APPROVAL_PROCESS_KEY = "hrm_offer_approval";

    /** 人工审批的流程号占位前缀（未接入 BPM 流程时使用） */
    private static final String MANUAL_PROCESS_INSTANCE_PREFIX = "MANUAL-";

    /** 参数键：是否允许提交人本人审批自己提交的 Offer（默认允许，HR 发完可直接批） */
    private static final String ALLOW_SELF_APPROVE_KEY = "hrm.offer.approval.allow-self-approve";
    /**
     * 招聘平台（门户）基地址参数键（问题 9）
     *
     * <p>录用通知邮件里的「接受 / 拒绝」链接会带上该基地址，默认 {@code http://localhost:5173}，
     * 生产环境请在「系统管理 → 参数设置」里改成实际门户域名。</p>
     */
    private static final String PORTAL_BASE_URL_KEY = "hrm.portal.base-url";
    private static final String DEFAULT_PORTAL_BASE_URL = "http://localhost:5173";

    /**
     * 是否允许「提交人本人审批自己提交的 Offer」
     *
     * <p>口径由参数控制（系统管理 → 参数设置 → {@value #ALLOW_SELF_APPROVE_KEY}）：
     * 空值 / true 表示允许（默认，HR 自己发自己批）；false / 0 表示必须由其他有审批权限的人审批。</p>
     */
    private boolean isSelfApprovalAllowed() {
        try {
            String value = configApi.getConfigValueByKey(ALLOW_SELF_APPROVE_KEY).getCheckedData();
            if (StrUtil.isBlank(value)) {
                return true;
            }
            String trimmed = value.trim();
            return !"false".equalsIgnoreCase(trimmed) && !"0".equals(trimmed);
        } catch (Throwable ex) {
            // 读参数失败不阻断业务，按默认口径（允许）处理
            log.warn("[isSelfApprovalAllowed][读取参数({})失败，按允许自审自批处理]", ALLOW_SELF_APPROVE_KEY, ex);
            return true;
        }
    }

    /**
     * 招聘平台（门户）基地址（问题 9）
     *
     * <p>用于拼装录用通知邮件里的「接受 / 拒绝」链接，取值顺序：</p>
     * <ol>
     *   <li>系统参数 {@code hrm.portal.base-url}（系统管理 → 参数设置，改完立即生效，无需重新部署）；</li>
     *   <li>默认 {@code http://localhost:5173}（本地开发环境）。</li>
     * </ol>
     */
    private String resolvePortalBaseUrl() {
        try {
            String value = configApi.getConfigValueByKey(PORTAL_BASE_URL_KEY).getCheckedData();
            if (StrUtil.isNotBlank(value)) {
                return StrUtil.removeSuffix(value.trim(), "/");
            }
        } catch (Throwable ex) {
            log.warn("[resolvePortalBaseUrl][读取参数({})失败，使用默认门户地址]", PORTAL_BASE_URL_KEY, ex);
        }
        return DEFAULT_PORTAL_BASE_URL;
    }

    /**
     * 拼装门户「接受 / 拒绝 Offer」落地链接：登录后由前端读取 offerId/offerAction 并二次确认后落库。
     */
    private String buildOfferActionUrl(String portalBaseUrl, Long offerId, String action) {
        return portalBaseUrl + "/#/portal/recruit?section=messages&offerId=" + offerId
                + "&offerAction=" + action;
    }

    @Resource private HrmRecruitOfferMapper offerMapper;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private cn.iocoder.txgy.module.infra.api.config.ConfigApi configApi;
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
    @Resource private cn.iocoder.txgy.module.hrm.service.contract.HrmContractSignRecordService contractSignRecordService;
    /** 问题 10：简历结构化内容补全员工教育/工作经历 + 附件文本抽取 */
    @Resource private cn.iocoder.txgy.module.hrm.service.employee.archive.HrmEmployeeArchiveFillService employeeArchiveFillService;
    /** 问题 10：入职建档后按 Offer 薪资自动定薪 */
    @Resource private cn.iocoder.txgy.module.hrm.service.salary.employee.HrmSalaryEmployeeInfoService salaryEmployeeInfoService;
    @Resource private cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService recruitResumeService;

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
        // 黑名单前置拦截：候选人已在黑名单时不必等到「转员工」才拦，
        // 避免 Offer 审批、发送、合同生成全部走完后才被拒（H-02）
        HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidate(application.getCandidateId());
        if (candidate != null) {
            employeeBlacklistService.validateNotBlacklisted(candidate.getName(), candidate.getMobile(), null);
        }
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
        // 若已发布 BPM 流程定义（key=hrm_offer_approval），则发起审批流程；
        // 未发布时保持「人工审批」语义（由有 hrm:recruit:offer:approve 权限的人直接审批），不影响可用性
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        try {
            String processInstanceId = processInstanceApi.createProcessInstance(loginUserId,
                    new BpmProcessInstanceCreateReqDTO()
                            .setProcessDefinitionKey(OFFER_APPROVAL_PROCESS_KEY)
                            .setBusinessKey(String.valueOf(offer.getId()))
                            .setVariables(buildOfferApprovalVariables(offer))).getCheckedData();
            offerMapper.updateById(new HrmRecruitOfferDO().setId(id).setProcessInstanceId(processInstanceId));
        } catch (Throwable ex) {
            log.warn("[submit][Offer({})未发起 BPM 审批流程（流程定义可能未发布），降级为人工审批]",
                    id, ex);
        }
    }

    /**
     * 构造 Offer 审批流程变量：供流程节点上的审批人分配规则使用
     * （例如「用人部门负责人」按部门负责人解析、按角色解析评审人等）
     */
    private Map<String, Object> buildOfferApprovalVariables(HrmRecruitOfferDO offer) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("offerId", offer.getId());
        variables.put("applicationId", offer.getApplicationId());
        variables.put("postId", offer.getPostId());
        variables.put("salaryAmount", offer.getSalaryAmount());
        variables.put("plannedOnboardDate",
                offer.getPlannedOnboardDate() == null ? null : offer.getPlannedOnboardDate().toString());
        HrmRecruitPostDO post = offer.getPostId() == null ? null : postService.getRecruitPost(offer.getPostId());
        if (post != null) {
            // 用人部门：流程节点可据此找部门负责人审批
            variables.put("deptId", post.getDeptId());
            variables.put("postName", post.getPostName());
            variables.put("postOwnerEmployeeId", post.getOwnerEmployeeId());
        }
        return variables;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Boolean approved, String reason) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())) {
            throw exception(RECRUIT_OFFER_STATE_INVALID);
        }
        // 自审拦截：不能审批自己提交的 Offer（提交人记录在 creator 字段）
        // 口径可配：参数 hrm.offer.approval.allow-self-approve = true 时允许 HR 自己发自己批（默认 true）
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        if (!isSelfApprovalAllowed() && loginUserId != null
                && Objects.equals(String.valueOf(loginUserId), offer.getCreator())) {
            throw exception(RECRUIT_OFFER_SELF_APPROVAL_FORBIDDEN);
        }
        // 未接入 BPM 流程实例时的人工审批：使用带前缀的流程号占位，复用原回调逻辑（状态机与幂等不变）
        String processInstanceId = MANUAL_PROCESS_INSTANCE_PREFIX + loginUserId;
        approvalCallback(id, processInstanceId, offer.getApprovalVersion(),
                Boolean.TRUE.equals(approved), reason, loginUserId);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessApprovalResult(String processInstanceId, Integer processStatus, String reason) {
        handleProcessApprovalResult(processInstanceId, processStatus, reason, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessApprovalResult(String processInstanceId, Integer processStatus, String reason,
                                            Long approverUserId) {
        if (StrUtil.isBlank(processInstanceId) || processStatus == null) {
            return;
        }
        HrmRecruitOfferDO offer = offerMapper.selectByProcessInstanceId(processInstanceId);
        if (offer == null) {
            log.warn("[handleProcessApprovalResult][流程实例({})未找到对应 Offer，忽略]", processInstanceId);
            return;
        }
        // 1. 流程通过 / 不通过：推进 Offer 审批状态（复用人工审批同一套状态机，天然幂等）
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            approvalCallback(offer.getId(), processInstanceId, offer.getApprovalVersion(), true, reason, approverUserId);
            return;
        }
        if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            approvalCallback(offer.getId(), processInstanceId, offer.getApprovalVersion(), false, reason, approverUserId);
            return;
        }
        // 2. 发起人撤回流程：Offer 退回草稿，避免一直卡在「审批中」
        if (BpmProcessInstanceStatusEnum.CANCEL.getStatus().equals(processStatus)
                && HrmRecruitOfferApprovalStatusEnum.APPROVING.getStatus().equals(offer.getApprovalStatus())) {
            offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId()).setProcessInstanceId(null)
                    .setApprovalStatus(HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus())
                    .setApproveRemark(StrUtil.blankToDefault(reason, "审批流程已撤回，Offer 退回草稿")));
            applicationService.markPendingOffer(offer.getApplicationId());
            log.info("[handleProcessApprovalResult][Offer({})审批流程被撤回，已退回草稿]", offer.getId());
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void approvalCallback(Long id, String processInstanceId, Integer version,
                                 boolean approved, String reason) {
        approvalCallback(id, processInstanceId, version, approved, reason,
                SecurityFrameworkUtils.getLoginUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approvalCallback(Long id, String processInstanceId, Integer version,
                                 boolean approved, String reason, Long approverUserId) {
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
                // 审批留痕：通过/驳回都记录审批人、审批时间与审批意见（不再只写驳回原因）
                .setApproverUserId(approverUserId).setApproveTime(LocalDateTime.now())
                .setApproveRemark(StrUtil.blankToDefault(reason, approved ? "审批通过" : "Offer审批未通过"))
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
        // 若该 Offer 挂在运行中的工作流上，同步取消流程实例，避免审批人还在批一个已撤回的单子
        if (StrUtil.isNotBlank(offer.getProcessInstanceId())
                && !offer.getProcessInstanceId().startsWith(MANUAL_PROCESS_INSTANCE_PREFIX)) {
            try {
                // 撤回发起人即提交流程的人，用「发起人取消」接口即可；失败只记日志，不阻断撤回
                processInstanceApi.cancelProcessInstanceByStartUser(SecurityFrameworkUtils.getLoginUserId(),
                        offer.getProcessInstanceId(), withdrawReason).checkError();
            } catch (Throwable ex) {
                log.warn("[withdraw][Offer({})取消流程实例({})失败]", id, offer.getProcessInstanceId(), ex);
            }
        }
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
        // 发放口径（业务确认 2026-09-23）：
        // 1) 已审批通过 → 正常发放；
        // 2) 仍是草稿 → 允许「免审批直接发放」（未配置审批流或 HR 判断无需审批的场景）；
        // 3) 已发放 / 审批中 等其它状态 → 拒绝；
        // 4) 有效期：为空视为不限；只有明确过期才拦截。
        boolean approved = HrmRecruitOfferApprovalStatusEnum.APPROVED.getStatus().equals(offer.getApprovalStatus());
        boolean draft = HrmRecruitOfferApprovalStatusEnum.DRAFT.getStatus().equals(offer.getApprovalStatus());
        if ((!approved && !draft)
                || (offer.getValidUntil() != null && offer.getValidUntil().isBefore(LocalDate.now()))) {
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
                markNotifyResult(offer.getId(), 2, "应聘记录不存在，无法发送录用通知");
                return;
            }
            HrmRecruitCandidateDO candidate = candidateService.getRecruitCandidate(application.getCandidateId());
            if (candidate == null || StrUtil.isBlank(candidate.getEmail())) {
                log.warn("[sendOfferNoticeMail][Offer({}) 候选人({}) 邮箱为空，跳过 Offer 通知邮件]",
                        offer.getId(), candidate == null ? null : candidate.getId());
                markNotifyResult(offer.getId(), 2, "候选人邮箱为空，无法发送录用通知");
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
            // 问题 9：录用通知邮件里给出「接受 / 拒绝」链接，候选人点进招聘平台即完成状态更新
            String portalBaseUrl = resolvePortalBaseUrl();
            params.put("acceptUrl", buildOfferActionUrl(portalBaseUrl, offer.getId(), "accept"));
            params.put("rejectUrl", buildOfferActionUrl(portalBaseUrl, offer.getId(), "reject"));
            // 3. 发送（只发给候选人本人，不关联任何后台用户，避免把 HR 自己的邮箱也带上）
            Long mailLogId = mailSendApi.sendSingleMailToAddresses(
                    new cn.iocoder.txgy.module.system.api.mail.dto.MailSendToAddressesReqDTO()
                            .setToMails(java.util.Collections.singletonList(candidate.getEmail()))
                            .setTemplateCode(MessageTemplateConstants.OFFER_NOTICE)
                            .setTemplateParams(params)
                            .setAttachmentUrls(java.util.Collections.singletonList(fileUrl))).getCheckedData();
            log.info("[sendOfferNoticeMail][Offer({}) 已发送录用通知邮件，mailLogId({})，附件({})]",
                    offer.getId(), mailLogId, fileUrl);
            markNotifyResult(offer.getId(), 1, null);
        } catch (Throwable ex) {
            log.warn("[sendOfferNoticeMail][Offer({}) 发送录用通知邮件失败]", offer.getId(), ex);
            markNotifyResult(offer.getId(), 2, StrUtil.maxLength(
                    StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName()), 480));
        }
    }

    /**
     * 落库录用通知发送结果，供 HR 在 Offer 上直接查看（失败原因 + 重发入口）
     */
    private void markNotifyResult(Long offerId, Integer status, String failReason) {
        try {
            offerMapper.updateById(new HrmRecruitOfferDO().setId(offerId)
                    .setNotifyStatus(status).setNotifyTime(LocalDateTime.now())
                    .setNotifyFailReason(failReason));
        } catch (Throwable ex) {
            log.warn("[markNotifyResult][Offer({}) 发送结果落库失败]", offerId, ex);
        }
    }
    @Override @Transactional(rollbackFor = Exception.class) public void confirm(Long id) {
        HrmRecruitOfferDO offer = requiredForUpdate(id);
        if (!HrmRecruitOfferApprovalStatusEnum.SENT.getStatus().equals(offer.getApprovalStatus())) throw exception(RECRUIT_OFFER_STATE_INVALID);
        // Q-002 / I-002：有效期口径与 send 对齐——为空表示「长期有效」，只有明确过期才拦截，
        // 避免「长期有效的 Offer 发得出去、候选人却确认不了」。
        if (offer.getValidUntil() != null && offer.getValidUntil().isBefore(LocalDate.now())) {
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
        // 候选人与应聘记录不一致（历史脏数据/被删除）时给出明确业务错误，避免下面取手机号裸 NPE 500（P1）
        if (candidate == null) {
            throw exception(RECRUIT_CANDIDATE_NOT_EXISTS);
        }
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
                // 问题 10：候选人学历与员工学历是两套字典（候选人 5=本科、员工 5=技校），必须显式映射，
                // 否则「本科」会被写成「技校」
                .setHighestEducation(cn.iocoder.txgy.module.hrm.util.HrmOnboardFillUtils
                        .mapCandidateEducationToEmployeeEducation(candidate.getEducation()))
                .setCandidateId(candidate.getId()).setChannelId(candidate.getChannelId())
                .setType(reqVO.getType())
                .setEmploymentTypeId(employmentType.getId())
                .setEntryStatus(reqVO.getEntryStatus()).setEntryTime(reqVO.getEntryTime()).setStatus(reqVO.getStatus())
                .setProbation(0);
        if (post != null) {
            employee.setDeptId(post.getDeptId()).setPostName(post.getPostName())
                    .setLeaderEmployeeId(post.getOwnerEmployeeId());
            // 问题 10：招聘职位上填的「工作城市 / 工作地点」应随入职自动带入员工档案
            if (post.getAreaId() != null) {
                employee.setWorkCity(cn.iocoder.txgy.framework.ip.core.utils.AreaUtils.format(post.getAreaId()));
            }
            if (StrUtil.isNotBlank(post.getWorkAddress())) {
                employee.setWorkAddress(post.getWorkAddress());
            }
            // 需求单已到齐 / 已取消时不允许继续录用
            recruitDemandService.validateRecruitDemandCanRecruit(post.getDemandId());
            // Q-001 / I-001：在建档之前先原子「占位」编制（条件自增），并发入职时只有一个事务能抢到名额，
            // 另一个直接抛「编制已满」。占位与建档同事务，建档失败会一起回滚。
            recruitDemandService.reserveOnboardCount(post.getDemandId());
        }
        // 2.2.0 问题 10：用简历（结构化内容或附件 PDF 抽取文本）按规则补全档案字段（**不依赖 AI**）
        //       只填空字段、不覆盖候选人/HR 已有的值；识别不到就保持为空。
        String resumeContent = null;
        String resumeText = null;
        HrmRecruitResumeDO resumeForFill = null;
        if (application.getResumeId() != null) {
            resumeForFill = recruitResumeService.getAuthorizedRecruitResume(application.getResumeId());
        }
        if (resumeForFill != null) {
            resumeContent = resumeForFill.getContent();
            resumeText = employeeArchiveFillService.resolveResumeText(resumeContent, resumeForFill.getFileUrl());
            applyResumeExtractToEmployee(employee, employeeArchiveFillService.extractProfile(resumeText));
        }
        // M-05：职位缺失或职位未配置部门时仍允许建档（口径：先建，再由 HR 在员工档案里分配部门），
        // 但必须留下明确提示，避免"静默建出无部门员工"
        if (post == null) {
            log.warn("[onboard][Offer({})的入职职位({})不存在（可能已被删除），员工将不带部门/岗位建档，请 HR 在员工档案中补充]",
                    offer.getId(), postId);
            offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId())
                    .setRemark(StrUtil.blankToDefault(offer.getRemark(), "")
                            + "【待补】入职职位不存在，转员工后请在员工档案分配部门与岗位"));
        } else if (post.getDeptId() == null) {
            log.warn("[onboard][Offer({})的入职职位({})未配置部门，员工将不带部门建档，请 HR 在员工档案中补充]",
                    offer.getId(), postId);
            offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId())
                    .setRemark(StrUtil.blankToDefault(offer.getRemark(), "")
                            + "【待补】职位未配置部门，转员工后请在员工档案分配部门"));
        }
        // 2.2.1 剔除当前租户"未显示"的字段，避免自动建档被字段显隐校验拦截
        employeeFieldConfigService.clearInvisibleCreateFields(employee, employee.getEntryStatus());
        Long employeeId = employeeService.createEmployee(employee);
        offerMapper.updateById(new HrmRecruitOfferDO().setId(offer.getId()).setEmployeeId(employeeId));
        // 用工需求单回写：到岗人数已在建档前原子占位（见上面的 reserveOnboardCount），
        // 这里只根据最新人数刷新需求单状态（到齐自动置为「已完成」），避免重复自增把编制算多。
        if (post != null && post.getDemandId() != null) {
            try {
                recruitDemandService.refreshOnboardStatus(post.getDemandId());
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
        // 2.2.3 问题 10：用简历补全员工教育/工作经历 + 证件
        //       在线简历是结构化 JSON，直接映射；附件简历（PDF）先抽文本回写简历，便于后续分析与人工核对。
        //       这一步失败只记日志，不影响入职建档。
        try {
            fillEmployeeArchiveFromResume(employeeId, resumeForFill, resumeContent, resumeText);
        } catch (Throwable ex) {
            log.warn("[onboard][员工({}) 按简历补全档案失败]", employeeId, ex);
        }
        // 2.2.4 问题 10：按 Offer 薪资自动建立薪资档案（首次定薪），避免「入职了却没有工资」
        try {
            createSalaryArchiveFromOffer(employeeId, offer, reqVO.getEntryTime());
        } catch (Throwable ex) {
            log.warn("[onboard][员工({}) 按 Offer 自动定薪失败]", employeeId, ex);
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
        // 2.5 建档完成后自动提醒员工到员工端完善档案（身份证/住址/教育经历等 HR 拿不到的信息）。
        //     员工在员工端保存后，写的就是这条员工记录，后台立即能看到；同时会给直属上级发提醒。
        try {
            employeeService.sendEmployeeProfileFillMessage(java.util.List.of(employeeId));
        } catch (Throwable ex) {
            log.warn("[onboard][员工({}) 发送「完善档案」提醒失败]", employeeId, ex);
        }
        return employeeId;
    }

    /**
     * 问题 10：按简历补全员工教育/工作经历
     *
     * <p>在线简历（结构化 JSON）直接写入；附件简历（PDF）先从附件抽取文本回写到简历，
     * 让 HR 在候选人简历页能看到可复制的内容，后续 AI 分析也能用上。</p>
     */
    private void fillEmployeeArchiveFromResume(Long employeeId, HrmRecruitResumeDO resume,
                                               String resumeContent, String resumeText) {
        if (resume == null) {
            log.info("[fillEmployeeArchiveFromResume][员工({}) 无关联简历，跳过简历补全]", employeeId);
            return;
        }
        if (StrUtil.isBlank(resumeContent) && StrUtil.isNotBlank(resumeText)) {
            // 附件简历：把抽取到的文本回写，便于 HR 查看与后续 AI 分析
            employeeArchiveFillService.saveExtractedText(resume.getId(), resumeText);
        }
        // 1. 结构化内容 → 教育/工作经历
        int filled = employeeArchiveFillService.fillFromResumeContent(employeeId, resumeContent);
        // 2. 规则提取到的证书 → 员工证件列表（不依赖 AI）
        int certificates = employeeArchiveFillService.fillCertificatesFromResume(
                employeeId, employeeArchiveFillService.extractProfile(resumeText));
        log.info("[fillEmployeeArchiveFromResume][员工({}) 按简历({})补全经历 {} 条、证件 {} 条]",
                employeeId, resume.getId(), filled, certificates);
    }

    /**
     * 问题 10：把简历规则提取结果写入员工建档 VO（只填空字段）
     *
     * <p>候选人与招聘流程已有的值优先（例如手机号/邮箱/学历来自投递表单），
     * 简历只在对应字段为空时补齐，避免"简历写错覆盖真实数据"。</p>
     */
    private void applyResumeExtractToEmployee(HrmEmployeeSaveReqVO employee,
                                              cn.iocoder.txgy.module.hrm.util.HrmResumeRuleExtractor.Extracted extracted) {
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        if (StrUtil.isBlank(employee.getIdNumber()) && StrUtil.isNotBlank(extracted.getIdNumber())) {
            employee.setIdNumber(extracted.getIdNumber());
            if (employee.getIdType() == null) {
                employee.setIdType(extracted.getIdType() == null ? 1 : extracted.getIdType());
            }
        }
        if (employee.getBirthday() == null && extracted.getBirthday() != null) {
            employee.setBirthday(extracted.getBirthday());
        }
        if (employee.getSex() == null && extracted.getSex() != null) {
            employee.setSex(extracted.getSex());
        }
        if (employee.getAge() == null && extracted.getAge() != null) {
            employee.setAge(extracted.getAge());
        }
        if (StrUtil.isBlank(employee.getNation()) && StrUtil.isNotBlank(extracted.getNation())) {
            employee.setNation(extracted.getNation());
        }
        if (StrUtil.isBlank(employee.getNativePlace()) && StrUtil.isNotBlank(extracted.getNativePlace())) {
            employee.setNativePlace(extracted.getNativePlace());
        }
        if (StrUtil.isBlank(employee.getAddress()) && StrUtil.isNotBlank(extracted.getAddress())) {
            employee.setAddress(extracted.getAddress());
        }
        if (StrUtil.isBlank(employee.getEmail()) && StrUtil.isNotBlank(extracted.getEmail())) {
            employee.setEmail(extracted.getEmail());
        }
        if (employee.getHighestEducation() == null && extracted.getHighestEducation() != null) {
            employee.setHighestEducation(extracted.getHighestEducation());
        }
    }

    /**
     * 问题 10：按 Offer 薪资自动建立薪资档案（首次定薪）
     *
     * <p>避免「候选人已同意入职、员工已建档，但薪资档案为空，核算时按 0 计算」。
     * 基本工资取 Offer 的薪资数额；试用期与正式薪资都按该数额预置，HR 可在薪资档案里再调。</p>
     */
    private void createSalaryArchiveFromOffer(Long employeeId, HrmRecruitOfferDO offer, LocalDateTime entryTime) {
        if (offer.getSalaryAmount() == null || offer.getSalaryAmount().signum() <= 0) {
            log.info("[createSalaryArchiveFromOffer][员工({}) 的 Offer 未填薪资数额，跳过自动定薪]", employeeId);
            return;
        }
        BigDecimal amount = offer.getSalaryAmount();
        Integer basicSalaryCode = cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryOptionCodeEnum
                .BASIC_SALARY.getCode();
        String basicSalaryName = cn.iocoder.txgy.module.hrm.enums.salary.config.HrmSalaryOptionCodeEnum
                .BASIC_SALARY.getName();
        HrmSalaryOptionValueVO salaryOption = new HrmSalaryOptionValueVO();
        salaryOption.setCode(basicSalaryCode);
        salaryOption.setName(basicSalaryName);
        salaryOption.setValue(amount);
        HrmSalaryOptionValueVO probationOption = new HrmSalaryOptionValueVO();
        probationOption.setCode(basicSalaryCode);
        probationOption.setName(basicSalaryName);
        probationOption.setValue(amount);
        cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.employeeinfo.HrmSalaryEmployeeInfoUpdateReqVO reqVO =
                new cn.iocoder.txgy.module.hrm.controller.admin.salary.vo.employeeinfo.HrmSalaryEmployeeInfoUpdateReqVO();
        reqVO.setEmployeeId(employeeId);
        reqVO.setRecordType(cn.iocoder.txgy.module.hrm.enums.salary.employee.HrmSalaryChangeRecordTypeEnum
                .SALARY_SET.getType());
        reqVO.setChangeReason(cn.iocoder.txgy.module.hrm.enums.salary.employee.HrmSalaryChangeReasonEnum
                .ENTRY_SALARY.getReason());
        reqVO.setEffectTime(entryTime == null ? LocalDateTime.now() : entryTime);
        reqVO.setRemark("录用 Offer 自动定薪（Offer " + StrUtil.blankToDefault(offer.getOfferNo(), "-") + "）");
        reqVO.setSalaryOptions(java.util.List.of(salaryOption));
        reqVO.setProbationSalaryOptions(java.util.List.of(probationOption));
        Long recordId = salaryEmployeeInfoService.updateSalaryEmployeeInfo(reqVO);
        log.info("[createSalaryArchiveFromOffer][员工({}) 已按 Offer 薪资 {} 自动定薪，记录编号 {}]",
                employeeId, amount, recordId);
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
            // 2. 先登记「待签」签署记录：合同一旦发出就要能在「合同签署记录」里看到、催办与登记签署结果，
            //    否则签署台账缺失，合同会一直停在「未执行」，也就进不了「合同到期预警」
            contractSignRecordService.createPendingSignRecordIfAbsent(contract.getId());
            // 3. 渲染合同文件并上传到文件服务，回写合同附件
            byte[] content = contractRenderService.renderDocx(contract.getId());
            String fileName = contractRenderService.buildFileName(contract.getId());
            String fileUrl = fileApi.createFile(content, fileName, "hrm/contract",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            employeeContractService.markGeneratedFile(contract.getId(), fileUrl, "合同文件已生成，待签署");
            // 4. 给员工（其绑定的后台账号）发送待签署站内信
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
        // Q-002 / I-002：有效期允许为空，语义为「长期有效」；填了才校验不能早于今天。
        if (reqVO.getSalaryAmount() == null || reqVO.getSalaryAmount().signum() <= 0
                || reqVO.getSalaryAmount().scale() > 2
                || !Set.of(1, 2).contains(reqVO.getSalaryUnit())
                || reqVO.getPlannedOnboardDate() == null
                || reqVO.getPlannedOnboardDate().isBefore(today)
                || (reqVO.getValidUntil() != null && reqVO.getValidUntil().isBefore(today))) {
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

    @Override
    public List<HrmRecruitOfferDO> getOfferListByCandidateId(Long candidateId) {
        if (candidateId == null) {
            return List.of();
        }
        // Offer 挂在应聘记录上：先取该候选人的全部应聘记录，再取这些应聘记录对应的 Offer（阶段四 4.4）
        List<HrmRecruitApplicationDO> applications = applicationService
                .getApplicationListByCandidateId(candidateId);
        if (applications == null || applications.isEmpty()) {
            return List.of();
        }
        return offerMapper.selectListByApplicationIds(
                applications.stream().map(HrmRecruitApplicationDO::getId).toList());
    }
}
