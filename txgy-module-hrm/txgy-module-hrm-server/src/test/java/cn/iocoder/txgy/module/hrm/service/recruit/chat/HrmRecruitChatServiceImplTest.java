package cn.iocoder.txgy.module.hrm.service.recruit.chat;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.candidate.HrmRecruitCandidateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatMessageDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat.HrmRecruitChatConversationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatConversationMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.chat.HrmRecruitChatMessageMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.chat.HrmRecruitChatStatusEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static cn.iocoder.txgy.framework.common.enums.CommonStatusEnum.DISABLE;
import static cn.iocoder.txgy.framework.common.enums.CommonStatusEnum.ENABLE;
import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_APPLY_NOT_QUALIFIED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_CANDIDATE_FIRST;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_NOT_PARTICIPANT;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.RECRUIT_CHAT_ENDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@Import(HrmRecruitChatServiceImpl.class)
public class HrmRecruitChatServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmRecruitChatServiceImpl chatService;
    @Resource
    private HrmRecruitChatConversationMapper conversationMapper;
    @Resource
    private HrmRecruitChatMessageMapper messageMapper;

    @MockitoBean
    private HrmRecruitPostService postService;
    @MockitoBean
    private HrmEmployeeService employeeService;
    @MockitoBean
    private HrmPersonalResumeService personalResumeService;
    @MockitoBean
    private HrmRecruitCandidateService candidateService;
    @MockitoBean
    private HrmRecruitResumeService recruitResumeService;
    @MockitoBean
    private AdminUserApi adminUserApi;

    @Test
    public void testOpenConversationFallsBackToEnabledDepartmentUser() {
        Long candidateUserId = randomLongId();
        Long departmentId = randomLongId();
        Long departmentHrUserId = randomLongId();
        HrmRecruitPostDO post = new HrmRecruitPostDO();
        post.setId(randomLongId());
        post.setTenantId(randomLongId());
        post.setDeptId(departmentId);
        post.setCreator("seed");
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        AdminUserRespDTO candidate = new AdminUserRespDTO();
        candidate.setId(candidateUserId);
        candidate.setStatus(ENABLE.getStatus());
        AdminUserRespDTO disabledUser = new AdminUserRespDTO();
        disabledUser.setId(randomLongId());
        disabledUser.setStatus(DISABLE.getStatus());
        AdminUserRespDTO departmentHr = new AdminUserRespDTO();
        departmentHr.setId(departmentHrUserId);
        departmentHr.setStatus(ENABLE.getStatus());
        when(adminUserApi.getUserListByDeptIds(List.of(departmentId)))
                .thenReturn(CommonResult.success(List.of(candidate, disabledUser, departmentHr)));

        var conversation = chatService.openConversation(candidateUserId, post.getId());

        assertEquals(departmentHrUserId, conversation.getHrUserId());
        assertEquals(candidateUserId, conversation.getCandidateUserId());
    }

    @Test
    public void testOpenConversationPopulatesWorkTimeFromPersonalProfile() {
        Long candidateUserId = randomLongId();
        Long hrUserId = randomLongId();
        HrmRecruitPostDO post = new HrmRecruitPostDO();
        post.setId(randomLongId());
        post.setTenantId(randomLongId());
        post.setCreator(String.valueOf(hrUserId));
        when(postService.getPortalRecruitPostPublished(post.getId())).thenReturn(post);

        AdminUserRespDTO hr = new AdminUserRespDTO();
        hr.setId(hrUserId);
        hr.setNickname("招聘负责人");
        when(adminUserApi.getUserIgnoreTenant(hrUserId)).thenReturn(CommonResult.success(hr));

        HrmPersonalResumeDO profile = new HrmPersonalResumeDO();
        profile.setTitle("求职资料（系统）");
        profile.setContent("{\"jobStart\":\"2020-01\",\"jobEnd\":\"2022-01\"}");
        when(personalResumeService.getResumeList(candidateUserId)).thenReturn(List.of(profile));

        var conversation = chatService.openConversation(candidateUserId, post.getId());

        assertEquals(2, conversation.getCandidateWorkTime());
    }

    @Test
    public void testConvertConversationToCandidate_createsCandidateAndResumeIdempotently() {
        Long candidateUserId = randomLongId();
        Long hrUserId = randomLongId();
        Long postId = randomLongId();
        Long tenantId = randomLongId();
        HrmRecruitChatConversationDO conversation = new HrmRecruitChatConversationDO()
                .setPostId(postId)
                .setCandidateUserId(candidateUserId)
                .setHrUserId(hrUserId)
                .setStatus(HrmRecruitChatStatusEnum.QUALIFIED.getStatus());
        conversation.setTenantId(tenantId);
        TenantUtils.execute(tenantId, () -> conversationMapper.insert(conversation));
        HrmRecruitChatMessageDO resumeMessage = new HrmRecruitChatMessageDO()
                .setConversationId(conversation.getId())
                .setSenderUserId(candidateUserId)
                .setContentType(2)
                .setContent("项目经理简历|project-manager.pdf|https://files.example.com/project-manager.pdf");
        resumeMessage.setTenantId(tenantId);
        TenantUtils.execute(tenantId, () -> messageMapper.insert(resumeMessage));

        AdminUserRespDTO candidateUser = new AdminUserRespDTO()
                .setId(candidateUserId).setNickname("求职者张三").setMobile("13800138000")
                .setEmail("zhangsan@example.com").setSex(1);
        HrmPersonalResumeDO profile = new HrmPersonalResumeDO()
                .setTitle("求职资料（系统）")
                .setContent("{\"name\":\"张三\",\"gender\":1,\"birthMonth\":\"1995-01\","
                        + "\"workTime\":5,\"education\":\"本科\",\"school\":\"测试大学\","
                        + "\"company\":\"测试科技\"}");
        when(adminUserApi.getUserIgnoreTenant(candidateUserId)).thenReturn(CommonResult.success(candidateUser));
        when(personalResumeService.getResumeList(candidateUserId)).thenReturn(List.of(profile));
        when(candidateService.getRecruitCandidateByMobile(candidateUser.getMobile())).thenReturn(null);
        when(candidateService.createRecruitCandidate(org.mockito.ArgumentMatchers.any())).thenReturn(7001L);
        when(recruitResumeService.createRecruitResume(org.mockito.ArgumentMatchers.any())).thenReturn(8001L);

        Long candidateId = chatService.convertConversationToCandidate(hrUserId, conversation.getId());

        assertEquals(7001L, candidateId);
        assertEquals(7001L, conversationMapper.selectById(conversation.getId()).getCandidateId());
        ArgumentCaptor<HrmRecruitCandidateSaveReqVO> candidateCaptor =
                ArgumentCaptor.forClass(HrmRecruitCandidateSaveReqVO.class);
        verify(candidateService).createRecruitCandidate(candidateCaptor.capture());
        assertEquals("张三", candidateCaptor.getValue().getName());
        assertEquals(postId, candidateCaptor.getValue().getPostId());
        assertEquals(5, candidateCaptor.getValue().getEducation());
        ArgumentCaptor<HrmRecruitResumeSaveReqVO> resumeCaptor =
                ArgumentCaptor.forClass(HrmRecruitResumeSaveReqVO.class);
        verify(recruitResumeService).createRecruitResume(resumeCaptor.capture());
        assertEquals(7001L, resumeCaptor.getValue().getCandidateId());
        assertEquals("project-manager.pdf", resumeCaptor.getValue().getFileName());

        assertEquals(7001L, chatService.convertConversationToCandidate(hrUserId, conversation.getId()));
        verify(candidateService).createRecruitCandidate(org.mockito.ArgumentMatchers.any());
        verify(recruitResumeService).createRecruitResume(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void testSendMessage_firstHrReplyGrantsApplyQualification() {
        Long candidateUserId = randomLongId();
        Long hrUserId = randomLongId();
        HrmRecruitChatConversationDO conversation = new HrmRecruitChatConversationDO();
        conversation.setPostId(randomLongId());
        conversation.setCandidateUserId(candidateUserId);
        conversation.setHrUserId(hrUserId);
        conversation.setStatus(HrmRecruitChatStatusEnum.NOT_STARTED.getStatus());
        conversation.setCandidateUnread(0);
        conversation.setHrUnread(0);
        conversationMapper.insert(conversation);

        assertServiceException(() -> chatService.sendMessage(hrUserId, new HrmPortalRecruitChatSendReqVO()
                .setConversationId(conversation.getId()).setContentType(1).setContent("您好，欢迎投递")),
                RECRUIT_CHAT_CANDIDATE_FIRST);
        assertServiceException(() -> chatService.sendMessage(candidateUserId, new HrmPortalRecruitChatSendReqVO()
                .setConversationId(conversation.getId()).setContentType(2).setContent("简历|resume.pdf|resume.pdf")),
                RECRUIT_CHAT_APPLY_NOT_QUALIFIED);

        chatService.sendMessage(candidateUserId, new HrmPortalRecruitChatSendReqVO()
                .setConversationId(conversation.getId()).setContentType(1).setContent("您好，我对这个岗位很感兴趣"));
        HrmRecruitChatConversationDO waiting = conversationMapper.selectById(conversation.getId());
        assertEquals(HrmRecruitChatStatusEnum.WAITING.getStatus(), waiting.getStatus());

        chatService.sendMessage(hrUserId, new HrmPortalRecruitChatSendReqVO()
                .setConversationId(conversation.getId()).setContentType(1).setContent("您好，欢迎投递"));

        HrmRecruitChatConversationDO updated = conversationMapper.selectById(conversation.getId());
        assertEquals(HrmRecruitChatStatusEnum.QUALIFIED.getStatus(), updated.getStatus());
        assertEquals("READY_TO_SUBMIT", updated.getApplicationStatus());
        assertNotNull(updated.getHrReplyTime());
        assertEquals(1, updated.getCandidateUnread());
        assertEquals(true, chatService.hasApplyQualification(candidateUserId, conversation.getPostId()));
    }

    @Test
    public void testSendMessage_waitingMoreThanTenDaysFailsConversation() {
        Long candidateUserId = randomLongId();
        Long hrUserId = randomLongId();
        HrmRecruitChatConversationDO conversation = new HrmRecruitChatConversationDO()
                .setPostId(randomLongId())
                .setCandidateUserId(candidateUserId)
                .setHrUserId(hrUserId)
                .setStatus(HrmRecruitChatStatusEnum.WAITING.getStatus())
                .setLastTime(java.time.LocalDateTime.now().minusDays(11))
                .setCandidateUnread(0)
                .setHrUnread(0);
        conversationMapper.insert(conversation);

        assertServiceException(() -> chatService.sendMessage(candidateUserId,
                new HrmPortalRecruitChatSendReqVO().setConversationId(conversation.getId())
                        .setContentType(1).setContent("继续沟通")), RECRUIT_CHAT_ENDED);
        assertEquals(HrmRecruitChatStatusEnum.FAILED.getStatus(),
                conversationMapper.selectById(conversation.getId()).getStatus());
    }

    @Test
    public void testEndConversationMarksConversationAsFailed() {
        Long candidateUserId = randomLongId();
        HrmRecruitChatConversationDO conversation = new HrmRecruitChatConversationDO()
                .setPostId(randomLongId())
                .setCandidateUserId(candidateUserId)
                .setHrUserId(randomLongId())
                .setStatus(HrmRecruitChatStatusEnum.WAITING.getStatus())
                .setCandidateUnread(0)
                .setHrUnread(0);
        conversationMapper.insert(conversation);

        chatService.endConversation(candidateUserId, conversation.getId(), "双方决定结束联系");

        HrmRecruitChatConversationDO ended = conversationMapper.selectById(conversation.getId());
        assertEquals(HrmRecruitChatStatusEnum.FAILED.getStatus(), ended.getStatus());
        assertEquals("双方决定结束联系", ended.getEndReason());
        assertNotNull(ended.getEndTime());
    }

    @Test
    public void testDeleteConversationOnlyAllowsParticipants() {
        Long hrUserId = randomLongId();
        Long candidateUserId = randomLongId();
        HrmRecruitChatConversationDO conversation = new HrmRecruitChatConversationDO()
                .setPostId(randomLongId())
                .setCandidateUserId(candidateUserId)
                .setHrUserId(hrUserId)
                .setStatus(HrmRecruitChatStatusEnum.WAITING.getStatus())
                .setCandidateUnread(0)
                .setHrUnread(0);
        conversationMapper.insert(conversation);

        chatService.deleteConversation(hrUserId, conversation.getId());

        assertNull(conversationMapper.selectById(conversation.getId()));
    }

    @Test
    public void testDeleteConversationListValidatesAllBeforeDeleting() {
        Long hrUserId = randomLongId();
        HrmRecruitChatConversationDO owned = new HrmRecruitChatConversationDO()
                .setPostId(randomLongId())
                .setCandidateUserId(randomLongId())
                .setHrUserId(hrUserId)
                .setStatus(HrmRecruitChatStatusEnum.WAITING.getStatus())
                .setCandidateUnread(0)
                .setHrUnread(0);
        HrmRecruitChatConversationDO notOwned = new HrmRecruitChatConversationDO()
                .setPostId(randomLongId())
                .setCandidateUserId(randomLongId())
                .setHrUserId(randomLongId())
                .setStatus(HrmRecruitChatStatusEnum.WAITING.getStatus())
                .setCandidateUnread(0)
                .setHrUnread(0);
        conversationMapper.insert(owned);
        conversationMapper.insert(notOwned);

        assertServiceException(() -> chatService.deleteConversationList(hrUserId,
                List.of(owned.getId(), notOwned.getId())), RECRUIT_CHAT_NOT_PARTICIPANT);
        assertNotNull(conversationMapper.selectById(owned.getId()));
        assertNotNull(conversationMapper.selectById(notOwned.getId()));
    }
}
