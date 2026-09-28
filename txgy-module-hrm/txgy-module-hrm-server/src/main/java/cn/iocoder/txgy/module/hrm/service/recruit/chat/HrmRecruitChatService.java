package cn.iocoder.txgy.module.hrm.service.recruit.chat;

import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatConversationRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatMessageRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitChatSendReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmRecruitChatAiFilterRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmRecruitChatCandidateResumeRespVO;
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

    /**
     * 查询沟通会话中求职者的在线简历。
     *
     * <p>优先返回投递时固化的快照（与 AI 分析、候选人详情同源，不受求职者后续编辑影响），
     * 尚未投递或快照只有附件时，回退到求职者当前的在线简历。</p>
     *
     * @param hrUserId       当前登录的招聘方用户编号
     * @param conversationId 沟通会话编号
     */
    HrmRecruitChatCandidateResumeRespVO getCandidateOnlineResume(Long hrUserId, Long conversationId);

    /**
     * 管理端会话列表。
     *
     * <p>在通用会话列表之上回填 AI 匹配分，供招聘方在列表层直接筛选。
     * 只读已经落库的分析结果，不会触发模型调用；门户端求职者不走这个方法。</p>
     *
     * @param hrUserId 当前登录的招聘方用户编号
     */
    List<HrmPortalRecruitChatConversationRespVO> getAdminConversationList(Long hrUserId);

    /**
     * 对沟通会话中的求职者跑一次「应聘匹配」AI 分析（A08）。
     *
     * <p>简历来源与 {@link #getCandidateOnlineResume} 同口径：投递快照优先，
     * 快照只有附件时回退到求职者当前在线简历——否则附件投递的会话永远无法分析。</p>
     *
     * @param hrUserId       当前登录的招聘方用户编号
     * @param conversationId 沟通会话编号
     * @param idempotencyKey 幂等键，重复提交不会重复消耗模型额度
     * @return Agent 任务编号
     */
    Long analyzeConversation(Long hrUserId, Long conversationId, String idempotencyKey);

    /**
     * 按招聘方的一句自然语言要求筛选沟通中的求职者。
     *
     * <p>模型只负责把这句话翻译成结构化条件，**不接收任何候选人数据**；
     * 是否命中的判断在服务端用本地数据完成，户籍、性别、年龄等属性不出内网。</p>
     *
     * @param hrUserId       当前登录的招聘方用户编号
     * @param query          招聘方输入的自然语言筛选要求
     * @param idempotencyKey 幂等键
     * @return 命中的会话编号，以及模型无法翻译的条件
     */
    HrmRecruitChatAiFilterRespVO aiFilterConversations(Long hrUserId, String query, String idempotencyKey);

    /** 将面试安排通知写入对应招聘会话，供求职者端实时接收。 */
    void sendInterviewArrangedNotification(Long applicationId, HrmRecruitInterviewDO interview);

    /** 将超过默认等待时限且尚未获得招聘方回复的会话置为沟通失败。 */
    int expireWaitingConversations(java.time.LocalDateTime now);
}
