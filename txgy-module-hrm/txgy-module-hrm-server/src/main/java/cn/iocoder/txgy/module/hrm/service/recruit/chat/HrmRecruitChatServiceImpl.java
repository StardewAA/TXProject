package cn.iocoder.txgy.module.hrm.service.recruit.chat;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmConversationFilterResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmJobMatchResultValidator;
import cn.iocoder.txgy.module.hrm.framework.agent.core.HrmRecruitAgentPromptTemplates;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatConversationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatMessageRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmRecruitChatAiFilterRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmRecruitChatCandidateResumeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate.HrmRecruitCandidateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.enums.recruit.chat.HrmRecruitChatStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.chat.HrmRecruitChatApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.dal.dataobject.agent.HrmRecruitAgentAnalysisDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatConversationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatMessageDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitInterviewDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatConversationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatMessageMapper;
import cn.iocoder.txgy.module.hrm.enums.agent.HrmRecruitAgentSceneEnum;
import cn.iocoder.txgy.module.hrm.service.agent.HrmConversationFilterMatcher;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentAdminService;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentMatchScoreBO;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentRunBO;
import cn.iocoder.txgy.module.hrm.service.agent.HrmRecruitAgentSceneRunner;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitApplicationService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_APPLICATION_RESUME_UNAVAILABLE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_MODEL_OUTPUT_INVALID;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.AGENT_POST_NOT_AVAILABLE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_APPLICATION_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_APPLY_NOT_QUALIFIED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_CANDIDATE_PROFILE_INCOMPLETE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_CANDIDATE_RESUME_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_CANDIDATE_FIRST;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_CONVERSATION_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_ENDED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_END_REASON_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_NOT_PARTICIPANT;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_OWNER_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_RESUME_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_SELF_NOT_ALLOWED;

@Service
@Validated
@Slf4j
public class HrmRecruitChatServiceImpl implements HrmRecruitChatService {

    private static final int WAITING_REPLY_TIMEOUT_DAYS = 10;
    private static final String TIMEOUT_END_REASON = "招聘方超过10天未回复";
    private static final DateTimeFormatter INTERVIEW_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 在线简历在个人简历表中的类型与标题，与门户端 RECRUIT_PROFILE_TITLE 对齐（兼容早期标题） */
    private static final int ONLINE_RESUME_TYPE = 2;
    private static final List<String> ONLINE_RESUME_TITLES = List.of("求职资料（系统）", "在线简历");
    /** Agent 任务里业务对象的类型标识，与 HrmRecruitAgentAdminServiceImpl 保持一致 */
    private static final String AGENT_BIZ_TYPE_APPLICATION = "APPLICATION";
    /** 沟通筛选任务没有单一业务对象，只用 bizType 标记它属于哪类场景 */
    private static final String AGENT_BIZ_TYPE_CONVERSATION_FILTER = "CONVERSATION_FILTER";
    /** 学历编号 → 名称兜底：优先用简历里的文字学历，缺失时才按编号推 */
    private static final Map<Integer, String> EDUCATION_NAMES = Map.of(
            1, "小学", 2, "初中", 3, "高中", 4, "大专", 5, "本科", 6, "硕士", 7, "博士");
    /** 这几类条件必须读简历才能判断；没有它们时跳过逐人的简历查询 */
    private static final List<String> RESUME_DEPENDENT_FILTERS =
            List.of("domicileKeywords", "expectedCityKeywords", "resumeKeywords");

    @Resource private HrmRecruitChatConversationMapper conversationMapper;
    @Resource private HrmRecruitChatMessageMapper messageMapper;
    @Resource private HrmRecruitCandidateMapper candidateMapper;
    @Resource private HrmRecruitCandidateService candidateService;
    @Resource private HrmRecruitPostService postService;
    @Resource private HrmRecruitAgentSceneRunner agentSceneRunner;
    @Resource private HrmEmployeeService employeeService;
    @Resource private HrmPersonalResumeService personalResumeService;
    @Resource private HrmRecruitResumeService recruitResumeService;
    @Resource private AdminUserApi adminUserApi;
    @Resource @Lazy private HrmRecruitApplicationService applicationService;
    @Resource @Lazy private HrmRecruitChatService chatServiceProxy;
    // Agent 管理端服务 → 应聘服务 → 本服务，构成环，故用 @Lazy 打破
    @Resource @Lazy private HrmRecruitAgentAdminService agentAdminService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrmPortalRecruitChatConversationRespVO openConversation(Long userId, Long postId) {
        chatServiceProxy.expireWaitingConversations(LocalDateTime.now());
        HrmRecruitPostDO post = postService.getPortalRecruitPostPublished(postId);
        AdminUserRespDTO hr = resolveHrUser(post, userId);
        if (hr == null || hr.getId() == null) {
            throw exception(RECRUIT_CHAT_OWNER_NOT_EXISTS);
        }
        if (hr.getId().equals(userId)) {
            throw exception(RECRUIT_CHAT_SELF_NOT_ALLOWED);
        }
        HrmRecruitChatConversationDO conversation = TenantUtils.executeIgnore(
                () -> conversationMapper.selectByPostAndUsers(post.getId(), userId, hr.getId()));
        if (conversation == null) {
            HrmRecruitChatConversationDO created = new HrmRecruitChatConversationDO();
            created.setPostId(post.getId());
            created.setHrUserId(hr.getId());
            created.setCandidateUserId(userId);
            created.setCandidateUnread(0);
            created.setHrUnread(0);
            created.setStatus(HrmRecruitChatStatusEnum.NOT_STARTED.getStatus());
            TenantUtils.execute(post.getTenantId(), () -> conversationMapper.insert(created));
            conversation = created;
        } else if (conversation.getLastTime() == null
                && conversation.getHrReplyTime() == null
                && HrmRecruitChatStatusEnum.WAITING.getStatus().equals(conversation.getStatus())) {
            // 兼容早期版本：仅打开过会话但尚未发送消息的记录应回到待发起。
            HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO();
            update.setId(conversation.getId());
            update.setStatus(HrmRecruitChatStatusEnum.NOT_STARTED.getStatus());
            TenantUtils.execute(conversation.getTenantId(), () -> conversationMapper.updateById(update));
            conversation.setStatus(update.getStatus());
        }
        return buildConversation(conversation, userId, post, Map.of(hr.getId(), hr));
    }

    @Override
    public List<HrmPortalRecruitChatConversationRespVO> getConversationList(Long userId) {
        chatServiceProxy.expireWaitingConversations(LocalDateTime.now());
        List<HrmRecruitChatConversationDO> list = TenantUtils.executeIgnore(
                () -> conversationMapper.selectListByUserId(userId));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        Map<Long, HrmRecruitPostDO> postMap = convertMap(TenantUtils.executeIgnore(
                () -> postService.getRecruitPostList(convertList(list, HrmRecruitChatConversationDO::getPostId))),
                HrmRecruitPostDO::getId);
        Set<Long> userIds = new HashSet<>();
        list.forEach(item -> {
            userIds.add(item.getHrUserId());
            userIds.add(item.getCandidateUserId());
        });
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMapIgnoreTenant(userIds);
        return convertList(list, item -> buildConversation(item, userId, postMap.get(item.getPostId()), userMap));
    }

    @Override
    public List<HrmPortalRecruitChatMessageRespVO> getMessageList(Long userId, Long conversationId, Long minId, Integer limit) {
        chatServiceProxy.expireWaitingConversations(LocalDateTime.now());
        HrmRecruitChatConversationDO conversation = getRequiredConversation(userId, conversationId);
        List<HrmRecruitChatMessageDO> list = TenantUtils.executeIgnore(
                () -> messageMapper.selectListByConversation(conversation.getId(), minId, limit));
        return convertList(list, item -> buildMessage(item, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrmPortalRecruitChatMessageRespVO sendMessage(Long userId, HrmPortalRecruitChatSendReqVO reqVO) {
        chatServiceProxy.expireWaitingConversations(LocalDateTime.now());
        HrmRecruitChatConversationDO conversation = getRequiredConversation(userId, reqVO.getConversationId());
        // 锁住会话行，保证首次 HR 回复、资格时间和未读计数在并发消息下保持一致。
        Long conversationId = conversation.getId();
        conversation = TenantUtils.executeIgnore(
                () -> conversationMapper.selectByIdForUpdate(conversationId));
        if (HrmRecruitChatStatusEnum.FAILED.getStatus().equals(conversation.getStatus())) {
            throw exception(RECRUIT_CHAT_ENDED);
        }
        int contentType = Integer.valueOf(2).equals(reqVO.getContentType()) ? 2 : 1;
        boolean candidateSide = userId.equals(conversation.getCandidateUserId());
        boolean hrSide = userId.equals(conversation.getHrUserId());
        if (!candidateSide && !hrSide) {
            throw exception(RECRUIT_CHAT_NOT_PARTICIPANT);
        }
        if (contentType == 2 && (!candidateSide || conversation.getHrReplyTime() == null)) {
            throw exception(RECRUIT_CHAT_APPLY_NOT_QUALIFIED);
        }
        if (hrSide && (HrmRecruitChatStatusEnum.NOT_STARTED.getStatus().equals(conversation.getStatus())
                || (conversation.getLastTime() == null && conversation.getHrReplyTime() == null))) {
            throw exception(RECRUIT_CHAT_CANDIDATE_FIRST);
        }
        String content = StrUtil.trim(reqVO.getContent());
        HrmRecruitChatMessageDO message = new HrmRecruitChatMessageDO();
        message.setConversationId(conversation.getId());
        message.setSenderUserId(userId);
        message.setContentType(contentType);
        message.setContent(content);
        message.setSendTime(LocalDateTime.now());
        TenantUtils.execute(conversation.getTenantId(), () -> messageMapper.insert(message));

        String summary = contentType == 2 ? "[简历] " + StrUtil.blankToDefault(StrUtil.subBefore(content, "|", false), content) : content;
        if (summary != null && summary.length() > 200) {
            summary = summary.substring(0, 200);
        }
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO();
        update.setId(conversation.getId());
        update.setLastContent(summary);
        update.setLastSenderUserId(userId);
        update.setLastTime(message.getSendTime());
        if (candidateSide) {
            update.setHrUnread((conversation.getHrUnread() == null ? 0 : conversation.getHrUnread()) + 1);
            if (HrmRecruitChatStatusEnum.NOT_STARTED.getStatus().equals(conversation.getStatus())) {
                update.setStatus(HrmRecruitChatStatusEnum.WAITING.getStatus());
            }
        } else {
            update.setCandidateUnread((conversation.getCandidateUnread() == null ? 0 : conversation.getCandidateUnread()) + 1);
            if (conversation.getHrReplyTime() == null) {
                update.setHrReplyTime(message.getSendTime());
            }
            if (!HrmRecruitChatStatusEnum.QUALIFIED.getStatus().equals(conversation.getStatus())) {
                update.setStatus(HrmRecruitChatStatusEnum.QUALIFIED.getStatus());
            }
            if (conversation.getApplicationStatus() == null) {
                update.setApplicationStatus(HrmRecruitChatApplicationStatusEnum.READY_TO_SUBMIT.getStatus());
            }
        }
        TenantUtils.execute(conversation.getTenantId(), () -> conversationMapper.updateById(update));
        return buildMessage(message, userId);
    }

    @Override
    public void markRead(Long userId, Long conversationId) {
        HrmRecruitChatConversationDO conversation = getRequiredConversation(userId, conversationId);
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO();
        update.setId(conversation.getId());
        if (userId.equals(conversation.getCandidateUserId())) {
            update.setCandidateUnread(0);
        } else {
            update.setHrUnread(0);
        }
        TenantUtils.execute(conversation.getTenantId(), () -> conversationMapper.updateById(update));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endConversation(Long userId, Long conversationId, String reason) {
        if (StrUtil.isBlank(reason)) {
            throw exception(RECRUIT_CHAT_END_REASON_REQUIRED);
        }
        HrmRecruitChatConversationDO conversation = getRequiredConversation(userId, conversationId);
        if (HrmRecruitChatStatusEnum.FAILED.getStatus().equals(conversation.getStatus())) {
            return;
        }
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO();
        update.setId(conversation.getId());
        update.setStatus(HrmRecruitChatStatusEnum.FAILED.getStatus());
        update.setEndTime(LocalDateTime.now());
        update.setEndReason(StrUtil.trim(reason));
        TenantUtils.execute(conversation.getTenantId(), () -> conversationMapper.updateById(update));
    }

    @Override
    public boolean hasApplyQualification(Long userId, Long postId) {
        List<HrmRecruitChatConversationDO> list = TenantUtils.executeIgnore(
                () -> conversationMapper.selectListByUserId(userId));
        if (list == null || list.isEmpty()) {
            return false;
        }
        return list.stream().anyMatch(item -> postId.equals(item.getPostId())
                && userId.equals(item.getCandidateUserId())
                && !HrmRecruitChatStatusEnum.FAILED.getStatus().equals(item.getStatus())
                && item.getHrReplyTime() != null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markApplicationSubmitted(Long candidateUserId, Long postId, Long candidateId, Long applicationId) {
        HrmRecruitChatConversationDO conversation = TenantUtils.executeIgnore(
                () -> conversationMapper.selectActiveByPostAndCandidateUser(postId, candidateUserId));
        if (conversation == null) {
            return;
        }
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO()
                .setId(conversation.getId())
                .setCandidateId(candidateId)
                .setApplicationId(applicationId)
                .setApplicationStatus(HrmRecruitChatApplicationStatusEnum.SUBMITTED.getStatus());
        TenantUtils.execute(conversation.getTenantId(), () -> conversationMapper.updateById(update));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long convertConversationToCandidate(Long hrUserId, Long conversationId) {
        HrmRecruitChatConversationDO conversation = getRequiredHrConversation(hrUserId, conversationId);
        if (conversation.getCandidateId() != null && conversation.getApplicationId() != null) {
            return conversation.getCandidateId();
        }

        Long tenantId = conversation.getTenantId();
        HrmRecruitChatMessageDO resumeMessage = TenantUtils.execute(tenantId,
                () -> messageMapper.selectLatestResumeByConversationId(conversation.getId()));
        if (resumeMessage == null || StrUtil.isBlank(resumeMessage.getContent())) {
            throw exception(RECRUIT_CHAT_RESUME_NOT_EXISTS);
        }

        String content = resumeMessage.getContent();
        int firstSeparator = content.indexOf('|');
        int secondSeparator = content.indexOf('|', firstSeparator + 1);
        if (firstSeparator < 0 || secondSeparator < 0 || secondSeparator == content.length() - 1) {
            throw exception(RECRUIT_CHAT_RESUME_NOT_EXISTS);
        }
        String resumeFileName = StrUtil.blankToDefault(
                content.substring(firstSeparator + 1, secondSeparator), "附件简历");
        String resumeFileUrl = content.substring(secondSeparator + 1).trim();
        if (StrUtil.isBlank(resumeFileUrl)) {
            throw exception(RECRUIT_CHAT_RESUME_NOT_EXISTS);
        }

        AdminUserRespDTO candidateUser = null;
        JSONObject profile = null;
        String mobile = null;
        if (conversation.getCandidateId() == null) {
            candidateUser = TenantUtils.execute(tenantId,
                    () -> adminUserApi.getUserIgnoreTenant(conversation.getCandidateUserId()).getCheckedData());
            profile = getCandidateProfile(conversation.getCandidateUserId());
            mobile = firstNotBlank(profile == null ? null : profile.getStr("mobile"),
                    candidateUser == null ? null : candidateUser.getMobile());
            if (StrUtil.isBlank(mobile)) {
                throw exception(RECRUIT_CHAT_CANDIDATE_PROFILE_INCOMPLETE);
            }
        }

        String candidateMobile = mobile;
        HrmRecruitCandidateDO candidate = conversation.getCandidateId() == null
                ? TenantUtils.execute(tenantId, () -> candidateService.getRecruitCandidateByMobile(candidateMobile))
                : new HrmRecruitCandidateDO().setId(conversation.getCandidateId());
        if (candidate == null) {
            HrmRecruitCandidateSaveReqVO candidateReq = new HrmRecruitCandidateSaveReqVO()
                    .setName(firstNotBlank(profile == null ? null : profile.getStr("name"),
                            candidateUser == null ? null : candidateUser.getNickname(), "求职者"))
                    .setMobile(mobile)
                    .setSex(resolveCandidateSex(profile, candidateUser))
                    .setAge(profile == null ? null : calculateAge(profile.getStr("birthMonth")))
                    .setEmail(firstNotBlank(profile == null ? null : profile.getStr("email"),
                            candidateUser == null ? null : candidateUser.getEmail()))
                    .setPostId(conversation.getPostId())
                    .setWorkTime(resolveCandidateWorkTime(profile))
                    .setEducation(resolveCandidateEducation(profile))
                    .setGraduateSchool(profile == null ? null : profile.getStr("school"))
                    .setLatestWorkPlace(profile == null ? null : profile.getStr("company"));
            Long candidateId = TenantUtils.execute(tenantId,
                    () -> candidateService.createRecruitCandidate(candidateReq));
            candidate = new HrmRecruitCandidateDO().setId(candidateId);
        } else {
            // 问题 5：候选人已存在但邮箱为空时，用求职者资料里的邮箱补齐
            String profileEmail = firstNotBlank(profile == null ? null : profile.getStr("email"),
                    candidateUser == null ? null : candidateUser.getEmail());
            Long existingCandidateId = candidate.getId();
            HrmRecruitCandidateDO existing = TenantUtils.execute(tenantId,
                    () -> candidateService.getRecruitCandidate(existingCandidateId));
            if (existing != null && StrUtil.isBlank(existing.getEmail()) && StrUtil.isNotBlank(profileEmail)) {
                Long fillCandidateId = existing.getId();
                TenantUtils.execute(tenantId,
                        () -> candidateService.updateRecruitCandidateEmail(fillCandidateId, profileEmail));
            }
        }

        HrmRecruitCandidateDO finalCandidate = candidate;
        HrmRecruitApplicationDO currentApplication = TenantUtils.execute(tenantId,
                () -> applicationService.getLatestRecruitApplicationByCandidateAndPost(
                        finalCandidate.getId(), conversation.getPostId()));
        Long applicationId = currentApplication != null
                && HrmRecruitApplicationStatusEnum.PROCESSING_STATUSES.contains(currentApplication.getStatus())
                ? currentApplication.getId() : null;
        if (applicationId == null) {
            Long resumeId = TenantUtils.execute(tenantId, () -> recruitResumeService.createRecruitResume(
                    new HrmRecruitResumeSaveReqVO()
                            .setCandidateId(finalCandidate.getId())
                            .setFileName(resumeFileName)
                            .setFileUrl(resumeFileUrl)
                            .setRemark("招聘沟通发送的简历")));
            applicationId = TenantUtils.execute(tenantId, () -> applicationService.createApplicationFromRecruitChat(
                    conversation.getCandidateUserId(), finalCandidate.getId(), conversation.getPostId(), resumeId));
        }
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO()
                .setId(conversation.getId()).setCandidateId(finalCandidate.getId())
                .setApplicationId(applicationId)
                .setApplicationStatus(HrmRecruitChatApplicationStatusEnum.SUBMITTED.getStatus());
        TenantUtils.execute(tenantId, () -> conversationMapper.updateById(update));
        return finalCandidate.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendInterviewArrangedNotification(Long applicationId, HrmRecruitInterviewDO interview) {
        if (applicationId == null || interview == null) {
            return;
        }
        HrmRecruitChatConversationDO conversation = TenantUtils.executeIgnore(
                () -> conversationMapper.selectByApplicationId(applicationId));
        if (conversation == null || conversation.getHrUserId() == null) {
            return;
        }
        String interviewTime = interview.getInterviewTime() == null
                ? "待确认" : INTERVIEW_TIME_FORMATTER.format(interview.getInterviewTime());
        String address = StrUtil.blankToDefault(interview.getAddress(), "线上面试");
        String content = "面试安排通知：第 " + (interview.getStageNumber() == null ? "1" : interview.getStageNumber())
                + " 轮，时间：" + interviewTime + "，地点：" + address;
        if (StrUtil.isNotBlank(interview.getRemark())) {
            content += "，备注：" + interview.getRemark();
        }
        try {
            sendMessage(conversation.getHrUserId(), new HrmPortalRecruitChatSendReqVO()
                    .setConversationId(conversation.getId())
                    .setContentType(1)
                    .setContent(content));
        } catch (RuntimeException ex) {
            log.warn("[sendInterviewArrangedNotification][applicationId({}) conversationId({}) 通知发送失败]",
                    applicationId, conversation.getId(), ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long userId, Long conversationId) {
        HrmRecruitChatConversationDO conversation = getRequiredConversation(userId, conversationId);
        deleteConversationRecord(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversationList(Long userId, List<Long> conversationIds) {
        List<Long> distinctIds = conversationIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<HrmRecruitChatConversationDO> conversations = distinctIds.stream()
                .map(id -> getRequiredConversation(userId, id))
                .collect(Collectors.toList());
        conversations.forEach(this::deleteConversationRecord);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int expireWaitingConversations(LocalDateTime now) {
        LocalDateTime deadline = now.minusDays(WAITING_REPLY_TIMEOUT_DAYS);
        List<HrmRecruitChatConversationDO> waiting = TenantUtils.executeIgnore(
                () -> conversationMapper.selectWaitingBefore(deadline));
        int expired = 0;
        for (HrmRecruitChatConversationDO conversation : waiting) {
            expired += TenantUtils.execute(conversation.getTenantId(),
                    () -> conversationMapper.markWaitingAsFailed(conversation.getId(), now, TIMEOUT_END_REASON));
        }
        return expired;
    }

    private HrmRecruitChatConversationDO getRequiredConversation(Long userId, Long conversationId) {
        HrmRecruitChatConversationDO conversation = TenantUtils.executeIgnore(
                () -> conversationMapper.selectById(conversationId));
        if (conversation == null) {
            throw exception(RECRUIT_CHAT_CONVERSATION_NOT_EXISTS);
        }
        if (!userId.equals(conversation.getCandidateUserId()) && !userId.equals(conversation.getHrUserId())) {
            throw exception(RECRUIT_CHAT_NOT_PARTICIPANT);
        }
        return conversation;
    }

    private HrmRecruitChatConversationDO getRequiredHrConversation(Long hrUserId, Long conversationId) {
        HrmRecruitChatConversationDO conversation = TenantUtils.executeIgnore(
                () -> conversationMapper.selectByIdForUpdate(conversationId));
        if (conversation == null) {
            throw exception(RECRUIT_CHAT_CONVERSATION_NOT_EXISTS);
        }
        if (!hrUserId.equals(conversation.getHrUserId())) {
            throw exception(RECRUIT_CHAT_NOT_PARTICIPANT);
        }
        return conversation;
    }

    @Override
    public HrmRecruitChatCandidateResumeRespVO getCandidateOnlineResume(Long hrUserId, Long conversationId) {
        HrmRecruitChatConversationDO conversation = getRequiredConversation(hrUserId, conversationId);
        HrmRecruitChatCandidateResumeRespVO snapshot =
                loadApplicationResumeSnapshot(conversation.getApplicationId());
        if (snapshot != null) {
            return snapshot;
        }
        HrmRecruitChatCandidateResumeRespVO online =
                loadCurrentOnlineResume(conversation.getCandidateUserId());
        if (online != null) {
            return online;
        }
        throw exception(RECRUIT_CHAT_CANDIDATE_RESUME_NOT_EXISTS);
    }

    @Override
    public List<HrmPortalRecruitChatConversationRespVO> getAdminConversationList(Long hrUserId) {
        List<HrmPortalRecruitChatConversationRespVO> conversations = getConversationList(hrUserId);
        List<Long> applicationIds = conversations.stream()
                .map(HrmPortalRecruitChatConversationRespVO::getApplicationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (applicationIds.isEmpty()) {
            return conversations;
        }
        Map<Long, HrmRecruitAgentMatchScoreBO> scores = agentAdminService.getApplicationMatchScores(applicationIds);
        conversations.forEach(conversation -> {
            HrmRecruitAgentMatchScoreBO score = scores.get(conversation.getApplicationId());
            if (score != null) {
                conversation.setAiMatchScore(score.getOverallScore());
                conversation.setAiTaskId(score.getTaskId());
            }
        });
        return conversations;
    }

    @Override
    public Long analyzeConversation(Long hrUserId, Long conversationId, String idempotencyKey) {
        HrmRecruitChatConversationDO conversation = getRequiredConversation(hrUserId, conversationId);
        if (conversation.getApplicationId() == null) {
            throw exception(RECRUIT_CHAT_APPLICATION_REQUIRED);
        }
        HrmRecruitApplicationDO application = applicationService.getApplication(conversation.getApplicationId());
        if (application == null) {
            throw exception(RECRUIT_CHAT_APPLICATION_REQUIRED);
        }
        // 职位被删除时不产出结论，避免拿旧结论误导 HR（与详情页 A07/A08/A09 同口径）
        HrmRecruitPostDO post = postService.getRecruitPost(application.getPostId());
        if (post == null) {
            throw exception(AGENT_POST_NOT_AVAILABLE);
        }
        // 简历与「查看在线简历」同口径：投递快照优先，快照只有附件时回退到求职者当前在线简历。
        // 不这样做的话，附件投递的会话（当前是绝大多数）永远分析不了。
        HrmRecruitResumeDO snapshot = loadApplicationResume(conversation.getApplicationId());
        HrmPersonalResumeDO online = snapshot == null ? loadOnlineResume(conversation.getCandidateUserId()) : null;
        String resumeContent = snapshot != null ? snapshot.getContent()
                : online == null ? null : online.getContent();
        if (StrUtil.isBlank(resumeContent)) {
            throw exception(AGENT_APPLICATION_RESUME_UNAVAILABLE);
        }
        return agentSceneRunner.run(new HrmRecruitAgentRunBO()
                .setUserId(hrUserId)
                .setScene(HrmRecruitAgentSceneEnum.APPLICATION_MATCH)
                .setPromptVersion(HrmRecruitAgentPromptTemplates.APPLICATION_MATCH_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.APPLICATION_MATCH_SYSTEM)
                .setResumeContent(resumeContent)
                // 快照用快照编号、回退时用个人简历编号，都只用于结果溯源
                .setResumeVersionId(snapshot != null ? snapshot.getId() : online.getId())
                .setBizType(AGENT_BIZ_TYPE_APPLICATION)
                .setBizId(application.getId())
                .setPost(post)
                .setIdempotencyKey(idempotencyKey)
                // 管理端在个人端匹配结构上额外要求风险项，与详情页应聘匹配保持一致
                .setResultValidator((content, resume) -> HrmJobMatchResultValidator.validateWithRisks(content)));
    }

    @Override
    public HrmRecruitChatAiFilterRespVO aiFilterConversations(Long hrUserId, String query, String idempotencyKey) {
        // 1. 交给模型的只有招聘方那句话；payload 里没有任何候选人数据
        Long taskId = agentSceneRunner.run(new HrmRecruitAgentRunBO()
                .setUserId(hrUserId)
                .setScene(HrmRecruitAgentSceneEnum.CONVERSATION_FILTER)
                .setPromptVersion(HrmRecruitAgentPromptTemplates.CONVERSATION_FILTER_VERSION)
                .setSystemPrompt(HrmRecruitAgentPromptTemplates.CONVERSATION_FILTER_SYSTEM)
                .setAgentPayload(new JSONObject().set("query", query))
                .setBizType(AGENT_BIZ_TYPE_CONVERSATION_FILTER)
                .setIdempotencyKey(idempotencyKey)
                .setResultValidator((content, resume) -> HrmConversationFilterResultValidator.validate(content)));

        HrmRecruitAgentAnalysisDO analysis = agentSceneRunner.getAnalysis(taskId, false);
        if (analysis == null || StrUtil.isBlank(analysis.getResultJson())) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }
        JSONObject result = JSONUtil.parseObj(analysis.getResultJson());
        JSONObject filters = result.getJSONObject("filters");
        if (filters == null) {
            throw exception(AGENT_MODEL_OUTPUT_INVALID);
        }

        // 2. 命中的判定在本地做；条件里没有依赖简历的维度时不必逐人读简历
        List<HrmPortalRecruitChatConversationRespVO> conversations = getAdminConversationList(hrUserId);
        boolean needResume = RESUME_DEPENDENT_FILTERS.stream().anyMatch(field -> {
            JSONArray array = filters.getJSONArray(field);
            return array != null && !array.isEmpty();
        });
        List<Long> matched = conversations.stream()
                .filter(conversation -> HrmConversationFilterMatcher.matches(filters,
                        buildFilterCandidate(conversation, needResume)))
                .map(HrmPortalRecruitChatConversationRespVO::getId)
                .toList();

        HrmRecruitChatAiFilterRespVO respVO = new HrmRecruitChatAiFilterRespVO();
        respVO.setCriteria(result.getStr("criteria"));
        respVO.setMatchedConversationIds(matched);
        respVO.setTotalCandidates(conversations.size());
        respVO.setUnsupported(toStringList(filters.getJSONArray("unsupported")));
        respVO.setTaskId(taskId);
        respVO.setModel(analysis.getModel());
        respVO.setPromptVersion(analysis.getPromptVersion());
        return respVO;
    }

    /** 把会话上已有的字段摊平成筛选所需的候选人视图；需要时再补简历里的户籍与期望城市 */
    private HrmConversationFilterMatcher.Candidate buildFilterCandidate(
            HrmPortalRecruitChatConversationRespVO conversation, boolean withResume) {
        HrmConversationFilterMatcher.Candidate candidate = new HrmConversationFilterMatcher.Candidate()
                .setSchool(conversation.getCandidateGraduateSchool())
                .setEducationName(resolveEducationName(conversation))
                .setSex(conversation.getCandidateSex())
                .setAge(conversation.getCandidateAge())
                .setWorkTime(conversation.getCandidateWorkTime())
                .setPostName(conversation.getPostName());
        if (!withResume) {
            return candidate;
        }
        HrmPersonalResumeDO online = loadOnlineResume(conversation.getCandidateUserId());
        if (online == null || StrUtil.isBlank(online.getContent())) {
            return candidate;
        }
        candidate.setResumeText(online.getContent());
        try {
            JSONObject profile = JSONUtil.parseObj(online.getContent());
            candidate.setDomicile(profile.getStr("domicile"));
            JSONArray cities = profile.getJSONArray("expectedCities");
            if (cities != null && !cities.isEmpty()) {
                candidate.setExpectedCities(cities.stream().map(String::valueOf).toList());
            } else if (StrUtil.isNotBlank(profile.getStr("city"))) {
                // 早期简历把期望城市存成顿号分隔的字符串
                candidate.setExpectedCities(List.of(profile.getStr("city")));
            }
        } catch (RuntimeException ex) {
            // 简历不是合法 JSON 时按「没有这份数据」处理，不影响其它维度
            log.debug("[buildFilterCandidate][conversationId({}) profile parse failed]",
                    conversation.getId(), ex);
        }
        return candidate;
    }

    private String resolveEducationName(HrmPortalRecruitChatConversationRespVO conversation) {
        if (StrUtil.isNotBlank(conversation.getCandidateEducationName())) {
            return conversation.getCandidateEducationName();
        }
        Integer education = conversation.getCandidateEducation();
        return education == null ? null : EDUCATION_NAMES.get(education);
    }

    private List<String> toStringList(JSONArray array) {
        return array == null ? List.of() : array.stream().map(String::valueOf).toList();
    }

    /**
     * 取应聘记录的投递快照。
     *
     * <p>未投递、快照缺失、跨租户，或快照只有附件（没有结构化内容）时都返回 null，
     * 由调用方决定是回退在线简历还是直接报错。</p>
     */
    private HrmRecruitResumeDO loadApplicationResume(Long applicationId) {
        if (applicationId == null) {
            return null;
        }
        HrmRecruitApplicationDO application = applicationService.getApplication(applicationId);
        if (application == null || application.getResumeId() == null) {
            return null;
        }
        try {
            HrmRecruitResumeDO resume = recruitResumeService.getAuthorizedRecruitResume(application.getResumeId());
            return resume != null && StrUtil.isNotBlank(resume.getContent()) ? resume : null;
        } catch (RuntimeException ex) {
            // 快照缺失或租户校验不通过时退回在线简历，不阻塞 HR 查看
            log.debug("[loadApplicationResume][applicationId({}) resume unavailable]", applicationId, ex);
            return null;
        }
    }

    /** 已正式投递的会话优先用投递快照，与 AI 分析、候选人详情看到的是同一份简历。 */
    private HrmRecruitChatCandidateResumeRespVO loadApplicationResumeSnapshot(Long applicationId) {
        HrmRecruitResumeDO resume = loadApplicationResume(applicationId);
        if (resume == null) {
            return null;
        }
        HrmRecruitChatCandidateResumeRespVO vo = new HrmRecruitChatCandidateResumeRespVO();
        vo.setSource("SNAPSHOT");
        vo.setName(parseOnlineResumeName(resume.getContent()));
        vo.setTitle(StrUtil.blankToDefault(resume.getFileName(), "投递简历"));
        vo.setContent(resume.getContent());
        vo.setUpdateTime(resume.getCreateTime());
        return vo;
    }

    /** 取求职者当前的在线简历草稿；没有填写过时返回 null。 */
    private HrmPersonalResumeDO loadOnlineResume(Long candidateUserId) {
        if (candidateUserId == null) {
            return null;
        }
        // getResumeList 内部已 executeIgnore：求职者个人简历与招聘方不在同一租户
        return personalResumeService.getResumeList(candidateUserId).stream()
                .filter(item -> Integer.valueOf(ONLINE_RESUME_TYPE).equals(item.getResumeType()))
                .filter(item -> ONLINE_RESUME_TITLES.contains(item.getTitle()))
                .filter(item -> StrUtil.isNotBlank(item.getContent()))
                .max(Comparator.comparing(HrmPersonalResumeDO::getId))
                .orElse(null);
    }

    /** 尚未投递、或快照只有附件时的兜底：读求职者当前保存的在线简历。 */
    private HrmRecruitChatCandidateResumeRespVO loadCurrentOnlineResume(Long candidateUserId) {
        HrmPersonalResumeDO profile = loadOnlineResume(candidateUserId);
        if (profile == null) {
            return null;
        }
        HrmRecruitChatCandidateResumeRespVO vo = new HrmRecruitChatCandidateResumeRespVO();
        vo.setSource("ONLINE");
        vo.setName(parseOnlineResumeName(profile.getContent()));
        vo.setTitle(profile.getTitle());
        vo.setContent(profile.getContent());
        vo.setUpdateTime(profile.getUpdateTime());
        return vo;
    }

    private String parseOnlineResumeName(String content) {
        try {
            return JSONUtil.parseObj(content).getStr("name");
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private JSONObject getCandidateProfile(Long userId) {
        List<HrmPersonalResumeDO> profiles = personalResumeService.getResumeList(userId);
        HrmPersonalResumeDO profile = profiles.stream()
                .filter(item -> "求职资料（系统）".equals(item.getTitle()))
                .findFirst().orElse(null);
        if (profile == null || StrUtil.isBlank(profile.getContent())) {
            return null;
        }
        try {
            return JSONUtil.parseObj(profile.getContent());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer resolveCandidateSex(JSONObject profile, AdminUserRespDTO candidateUser) {
        Integer sex = profile == null ? null : profile.getInt("gender");
        if (sex == null && candidateUser != null) {
            sex = candidateUser.getSex();
        }
        return sex == null ? 1 : sex;
    }

    private Integer resolveCandidateWorkTime(JSONObject profile) {
        if (profile == null) {
            return null;
        }
        Integer workTime = profile.getInt("workTime");
        if (workTime != null) {
            return workTime;
        }
        return calculateWorkTime(profile.getStr("jobStart"), profile.getStr("jobEnd"));
    }

    private Integer resolveCandidateEducation(JSONObject profile) {
        if (profile == null || StrUtil.isBlank(profile.getStr("education"))) {
            return 5;
        }
        String education = profile.getStr("education").replaceAll("[\\s/]+", "");
        if (education.contains("博士")) return 7;
        if (education.contains("硕士")) return 6;
        if (education.contains("本科")) return 5;
        if (education.contains("大专")) return 4;
        if (education.contains("高中") || education.contains("中专") || education.contains("初中")) return 3;
        return 5;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void deleteConversationRecord(HrmRecruitChatConversationDO conversation) {
        TenantUtils.execute(conversation.getTenantId(), () -> conversationMapper.deleteById(conversation.getId()));
    }

    private AdminUserRespDTO resolveHrUser(HrmRecruitPostDO post, Long candidateUserId) {
        if (post == null || post.getTenantId() == null) {
            return null;
        }
        Long tenantId = post.getTenantId();
        if (post.getOwnerEmployeeId() != null) {
            try {
                HrmEmployeeDO owner = TenantUtils.execute(tenantId, () -> employeeService.getEmployee(post.getOwnerEmployeeId()));
                if (owner != null && owner.getUserId() != null) {
                    AdminUserRespDTO ownerUser = TenantUtils.execute(tenantId,
                            () -> adminUserApi.getUserIgnoreTenant(owner.getUserId()).getCheckedData());
                    if (ownerUser != null) {
                        return ownerUser;
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("[resolveHrUser][postId({}) ownerEmployeeId({}) failed]", post.getId(), post.getOwnerEmployeeId(), ex);
            }
        }
        Long creatorUserId = NumberUtil.isNumber(post.getCreator()) ? NumberUtil.parseLong(post.getCreator()) : null;
        if (creatorUserId != null) {
            try {
                AdminUserRespDTO creator = TenantUtils.execute(tenantId,
                        () -> adminUserApi.getUserIgnoreTenant(creatorUserId).getCheckedData());
                if (creator != null) {
                    return creator;
                }
            } catch (RuntimeException ex) {
                log.warn("[resolveHrUser][postId({}) creator({}) failed]", post.getId(), post.getCreator(), ex);
            }
        }
        if (post.getDeptId() == null) {
            return null;
        }
        try {
            List<AdminUserRespDTO> departmentUsers = DataPermissionUtils.executeIgnore(() ->
                    TenantUtils.execute(tenantId,
                            () -> adminUserApi.getUserListByDeptIds(List.of(post.getDeptId())).getCheckedData()));
            if (departmentUsers == null) {
                return null;
            }
            return departmentUsers.stream()
                    .filter(user -> user != null && user.getId() != null)
                    .filter(user -> CommonStatusEnum.isEnable(user.getStatus()))
                    .filter(user -> !user.getId().equals(candidateUserId))
                    .findFirst().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("[resolveHrUser][postId({}) deptId({}) department user lookup failed]",
                    post.getId(), post.getDeptId(), ex);
            return null;
        }
    }

    private HrmPortalRecruitChatConversationRespVO buildConversation(HrmRecruitChatConversationDO conversation,
                                                                     Long userId,
                                                                     HrmRecruitPostDO post,
                                                                     Map<Long, AdminUserRespDTO> userMap) {
        HrmPortalRecruitChatConversationRespVO vo = new HrmPortalRecruitChatConversationRespVO();
        vo.setId(conversation.getId());
        vo.setPostId(conversation.getPostId());
        vo.setHrUserId(conversation.getHrUserId());
        vo.setCandidateUserId(conversation.getCandidateUserId());
        vo.setCandidateId(conversation.getCandidateId());
        vo.setApplicationId(conversation.getApplicationId());
        vo.setLastContent(conversation.getLastContent());
        vo.setLastSenderUserId(conversation.getLastSenderUserId());
        vo.setLastTime(conversation.getLastTime());
        boolean hrSide = userId.equals(conversation.getHrUserId());
        vo.setHrSide(hrSide);
        Integer status = nvl(conversation.getStatus(), conversation.getLastTime() == null
                ? HrmRecruitChatStatusEnum.NOT_STARTED.getStatus()
                : HrmRecruitChatStatusEnum.WAITING.getStatus());
        vo.setStatus(status);
        vo.setApplyQualified(conversation.getHrReplyTime() != null);
        vo.setApplicationStatus(conversation.getApplicationStatus());
        vo.setHrReplyTime(conversation.getHrReplyTime());
        vo.setEndTime(conversation.getEndTime());
        vo.setEndReason(conversation.getEndReason());
        vo.setUnreadCount(hrSide ? nvl(conversation.getHrUnread()) : nvl(conversation.getCandidateUnread()));
        vo.setPeerUserId(hrSide ? conversation.getCandidateUserId() : conversation.getHrUserId());
        AdminUserRespDTO peer = userMap == null ? null : userMap.get(vo.getPeerUserId());
        if (peer != null) {
            vo.setPeerUserName(peer.getNickname());
            vo.setPeerUserAvatar(peer.getAvatar());
        }
        populateCandidateProfile(vo, conversation, userMap);
        if (post != null) {
            vo.setPostName(post.getPostName());
            vo.setAreaId(post.getAreaId());
            vo.setAreaName(AreaUtils.format(post.getAreaId()));
            vo.setMinSalary(post.getMinSalary());
            vo.setMaxSalary(post.getMaxSalary());
            vo.setSalaryUnit(post.getSalaryUnit());
        }
        return vo;
    }

    private void populateCandidateProfile(HrmPortalRecruitChatConversationRespVO vo,
                                          HrmRecruitChatConversationDO conversation,
                                          Map<Long, AdminUserRespDTO> userMap) {
        HrmRecruitCandidateDO candidate = conversation.getCandidateId() == null ? null
                : TenantUtils.executeIgnore(() -> candidateMapper.selectById(conversation.getCandidateId()));
        if (candidate != null) {
            vo.setCandidateName(candidate.getName());
            vo.setCandidateSex(candidate.getSex());
            vo.setCandidateAge(candidate.getAge());
            vo.setCandidateWorkTime(candidate.getWorkTime());
            vo.setCandidateEducation(candidate.getEducation());
            vo.setCandidateGraduateSchool(candidate.getGraduateSchool());
            if (candidate.getWorkTime() != null) {
                return;
            }
        }
        AdminUserRespDTO candidateUser = userMap == null ? null : userMap.get(conversation.getCandidateUserId());
        if (candidateUser != null) {
            vo.setCandidateSex(candidateUser.getSex());
        }
        List<HrmPersonalResumeDO> profiles = personalResumeService.getResumeList(conversation.getCandidateUserId());
        HrmPersonalResumeDO profile = profiles.stream()
                .filter(item -> "求职资料（系统）".equals(item.getTitle()))
                .findFirst().orElse(null);
        if (profile == null || StrUtil.isBlank(profile.getContent())) {
            return;
        }
        try {
            JSONObject data = JSONUtil.parseObj(profile.getContent());
            vo.setCandidateName(data.getStr("name"));
            vo.setCandidateSex(data.getInt("gender", vo.getCandidateSex()));
            vo.setCandidateAge(calculateAge(data.getStr("birthMonth")));
            Integer workTime = data.getInt("workTime");
            if (vo.getCandidateWorkTime() == null) {
                vo.setCandidateWorkTime(workTime == null
                        ? calculateWorkTime(data.getStr("jobStart"), data.getStr("jobEnd"))
                        : workTime);
            }
            vo.setCandidateEducationName(data.getStr("education"));
            vo.setCandidateGraduateSchool(data.getStr("school"));
        } catch (RuntimeException ex) {
            log.debug("[populateCandidateProfile][conversationId({}) profile parse failed]", conversation.getId(), ex);
        }
    }

    private Integer calculateAge(String birthMonth) {
        if (StrUtil.isBlank(birthMonth)) {
            return null;
        }
        try {
            return Period.between(YearMonth.parse(birthMonth).atDay(1), LocalDate.now()).getYears();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer calculateWorkTime(String workStart, String workEnd) {
        if (StrUtil.isBlank(workStart)) {
            return null;
        }
        try {
            YearMonth start = YearMonth.parse(workStart);
            YearMonth end = StrUtil.isBlank(workEnd) ? YearMonth.now() : YearMonth.parse(workEnd);
            if (end.isBefore(start)) {
                return null;
            }
            return Period.between(start.atDay(1), end.atDay(1)).getYears();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private HrmPortalRecruitChatMessageRespVO buildMessage(HrmRecruitChatMessageDO message, Long userId) {
        HrmPortalRecruitChatMessageRespVO vo = new HrmPortalRecruitChatMessageRespVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setSenderUserId(message.getSenderUserId());
        vo.setContentType(message.getContentType());
        vo.setContent(message.getContent());
        vo.setSendTime(message.getSendTime());
        vo.setMine(userId.equals(message.getSenderUserId()));
        return vo;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer nvl(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }
}
