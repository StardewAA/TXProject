package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;

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
    void withdraw(Long id);
    void withdraw(Long id, String reason);
    void expire(Long id, String reason);
    void send(Long id);
    void confirm(Long id);
    void acceptByCandidate(Long id, Long userId);
    void rejectByCandidate(Long id, Long userId, String reason);
    void expireOverdueOffers(Long applicantUserId);
    Long onboard(HrmRecruitOfferOnboardReqVO reqVO);
}
