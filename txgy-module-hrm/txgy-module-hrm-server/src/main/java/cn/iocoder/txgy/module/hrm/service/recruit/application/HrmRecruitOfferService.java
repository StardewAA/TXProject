package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;

import java.util.List;

public interface HrmRecruitOfferService {
    Long create(HrmRecruitOfferSaveReqVO reqVO);
    HrmRecruitOfferDO get(Long id);
    void submit(Long id);

    /**
     * Offer 人工审批（通过 / 驳回）
     *
     * <p>当前环境未接入 BPM 流程定义，未产生审批任务；由拥有 hrm:recruit:offer:approve 权限的人直接审批。
     * 后续接入 BPM 后仍可继续使用原有的 approvalCallback 回调。</p>
     *
     * @param id Offer 编号
     * @param approved 是否通过
     * @param reason 驳回原因（驳回时建议填写）
     */
    void approve(Long id, Boolean approved, String reason);

    /**
     * 重新生成劳务合同文件并再次发送「待签署」通知
     *
     * @param offerId Offer 编号（需已完成转员工）
     */
    void resendContractNotice(Long offerId);

    /**
     * 重新发送 Offer 录用通知邮件（正文 + 录用通知书 PDF 附件）
     *
     * <p>候选人未收到邮件、或邮箱填错后修正时使用；仅对「已发送 / 已确认」的 Offer 生效。</p>
     *
     * @param offerId Offer 编号
     */
    void resendOfferNotice(Long offerId);
    void approvalCallback(Long id, String processInstanceId, Integer approvalVersion, boolean approved);
    void approvalCallback(Long id, String processInstanceId, Integer approvalVersion,
                          boolean approved, String reason);

    /**
     * Offer 审批结果回调（带审批人，用于落库留痕）
     *
     * @param id Offer 编号
     * @param processInstanceId 流程实例编号（人工审批时为 MANUAL- 前缀占位）
     * @param approvalVersion 审批版本号（乐观锁）
     * @param approved 是否通过
     * @param reason 审批意见
     * @param approverUserId 审批人用户编号，可为空（工作流回调时由流程记录审批人）
     */
    void approvalCallback(Long id, String processInstanceId, Integer approvalVersion,
                          boolean approved, String reason, Long approverUserId);

    /**
     * 处理流程引擎回调的审批结果（由流程监听器调用）
     *
     * <p>流程通过/不通过时推进 Offer 状态；流程被发起人撤回时把 Offer 退回草稿，避免卡在审批中。</p>
     *
     * @param processInstanceId 流程实例编号
     * @param processStatus     流程状态（见 BpmProcessInstanceStatusEnum）
     * @param reason            审批意见 / 撤回原因
     */
    void handleProcessApprovalResult(String processInstanceId, Integer processStatus, String reason);

    /**
     * 处理流程引擎回调的审批结果（带最终审批人，用于 Offer 留痕）
     *
     * @param processInstanceId 流程实例编号
     * @param processStatus     流程状态
     * @param reason            审批意见 / 撤回原因
     * @param approverUserId    最终审批人用户编号，可为空
     */
    void handleProcessApprovalResult(String processInstanceId, Integer processStatus, String reason,
                                     Long approverUserId);
    void withdraw(Long id);
    void withdraw(Long id, String reason);
    void expire(Long id, String reason);
    void send(Long id);
    void confirm(Long id);
    void acceptByCandidate(Long id, Long userId);
    void rejectByCandidate(Long id, Long userId, String reason);
    void expireOverdueOffers(Long applicantUserId);
    Long onboard(HrmRecruitOfferOnboardReqVO reqVO);

    /**
     * 按候选人查询 Offer 列表（候选人详情「Offer」Tab 用）
     *
     * <p>Offer 挂在应聘记录上：先取该候选人的全部应聘记录，再取这些应聘记录的 Offer。</p>
     *
     * @param candidateId 候选人编号
     * @return Offer 列表
     */
    List<HrmRecruitOfferDO> getOfferListByCandidateId(Long candidateId);
}
