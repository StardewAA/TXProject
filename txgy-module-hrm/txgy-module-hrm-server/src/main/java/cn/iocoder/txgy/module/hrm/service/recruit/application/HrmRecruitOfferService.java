package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;

public interface HrmRecruitOfferService {
    Long create(HrmRecruitOfferSaveReqVO reqVO);
    HrmRecruitOfferDO get(Long id);
    void submit(Long id);
    void approvalCallback(Long id, String processInstanceId, Integer approvalVersion, boolean approved);
    void withdraw(Long id);
    void send(Long id);
    void confirm(Long id);
    Long onboard(HrmRecruitOfferOnboardReqVO reqVO);
}
