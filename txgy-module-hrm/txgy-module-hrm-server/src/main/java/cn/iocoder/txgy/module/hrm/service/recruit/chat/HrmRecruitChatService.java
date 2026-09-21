package cn.iocoder.txgy.module.hrm.service.recruit.chat;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatConversationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatMessageRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitInterviewDO;

import java.util.List;

/** 招聘门户沟通。 */
public interface HrmRecruitChatService {

    HrmPortalRecruitChatConversationRespVO openConversation(Long userId, Long postId);

    List<HrmPortalRecruitChatConversationRespVO> getConversationList(Long userId);

    List<HrmPortalRecruitChatMessageRespVO> getMessageList(Long userId, Long conversationId, Long minId, Integer limit);

    HrmPortalRecruitChatMessageRespVO sendMessage(Long userId, HrmPortalRecruitChatSendReqVO reqVO);

    void markRead(Long userId, Long conversationId);

    void endConversation(Long userId, Long conversationId, String reason);

    void deleteConversation(Long userId, Long conversationId);

    void deleteConversationList(Long userId, List<Long> conversationIds);

    boolean hasApplyQualification(Long userId, Long postId);

    /** 将正式投递记录绑定到对应会话，供企业候选人列表即时查询。 */
    void markApplicationSubmitted(Long candidateUserId, Long postId, Long candidateId, Long applicationId);

    /** 将招聘沟通中的求职者手动转为候选人。 */
    Long convertConversationToCandidate(Long hrUserId, Long conversationId);

    /** 将面试安排通知写入对应招聘会话，供求职者端实时接收。 */
    void sendInterviewArrangedNotification(Long applicationId, HrmRecruitInterviewDO interview);

    /** 将超过默认等待时限且尚未获得招聘方回复的会话置为沟通失败。 */
    int expireWaitingConversations(java.time.LocalDateTime now);
}
