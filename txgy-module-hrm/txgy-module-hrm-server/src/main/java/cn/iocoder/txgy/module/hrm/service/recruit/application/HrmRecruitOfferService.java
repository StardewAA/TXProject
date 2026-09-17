package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;

public interface HrmRecruitOfferService {
    Long create(HrmRecruitOfferSaveReqVO reqVO);
    HrmRecruitOfferDO get(Long id);
    void submit(Long id);
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
