package cn.iocoder.txgy.module.hrm.service.recruit.chat;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatConversationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatMessageRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate.HrmRecruitCandidateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.enums.recruit.chat.HrmRecruitChatStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.chat.HrmRecruitChatApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatConversationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatMessageDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitCandidateMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatConversationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatMessageMapper;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_APPLY_NOT_QUALIFIED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_CANDIDATE_PROFILE_INCOMPLETE;
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

    @Resource private HrmRecruitChatConversationMapper conversationMapper;
    @Resource private HrmRecruitChatMessageMapper messageMapper;
    @Resource private HrmRecruitCandidateMapper candidateMapper;
    @Resource private HrmRecruitCandidateService candidateService;
    @Resource private HrmRecruitPostService postService;
    @Resource private HrmEmployeeService employeeService;
    @Resource private HrmPersonalResumeService personalResumeService;
    @Resource private HrmRecruitResumeService recruitResumeService;
    @Resource private AdminUserApi adminUserApi;
    @Resource @Lazy private HrmRecruitChatService chatServiceProxy;

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
        if (conversation.getCandidateId() != null) {
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

        AdminUserRespDTO candidateUser = TenantUtils.execute(tenantId,
                () -> adminUserApi.getUserIgnoreTenant(conversation.getCandidateUserId()).getCheckedData());
        JSONObject profile = getCandidateProfile(conversation.getCandidateUserId());
        String mobile = firstNotBlank(profile == null ? null : profile.getStr("mobile"),
                candidateUser == null ? null : candidateUser.getMobile());
        if (StrUtil.isBlank(mobile)) {
            throw exception(RECRUIT_CHAT_CANDIDATE_PROFILE_INCOMPLETE);
        }

        HrmRecruitCandidateDO candidate = TenantUtils.execute(tenantId,
                () -> candidateService.getRecruitCandidateByMobile(mobile));
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
        }

        HrmRecruitCandidateDO finalCandidate = candidate;
        TenantUtils.execute(tenantId, () -> recruitResumeService.createRecruitResume(
                new HrmRecruitResumeSaveReqVO()
                        .setCandidateId(finalCandidate.getId())
                        .setFileName(resumeFileName)
                        .setFileUrl(resumeFileUrl)
                        .setRemark("招聘沟通发送的简历")));
        HrmRecruitChatConversationDO update = new HrmRecruitChatConversationDO()
                .setId(conversation.getId()).setCandidateId(finalCandidate.getId());
        TenantUtils.execute(tenantId, () -> conversationMapper.updateById(update));
        return finalCandidate.getId();
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
        HrmRecruitChatConversationDO conversation = getRequiredConversation(hrUserId, conversationId);
        if (!hrUserId.equals(conversation.getHrUserId())) {
            throw exception(RECRUIT_CHAT_NOT_PARTICIPANT);
        }
        return conversation;
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
