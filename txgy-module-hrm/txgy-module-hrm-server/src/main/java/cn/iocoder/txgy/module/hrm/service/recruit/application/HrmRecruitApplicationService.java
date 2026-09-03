package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;

/** HRM 招聘应聘流程服务。 */
public interface HrmRecruitApplicationService {

    Long createApplication(HrmRecruitApplicationCreateReqVO reqVO);

    PageResult<HrmRecruitApplicationDO> getApplicationPage(HrmRecruitApplicationPageReqVO reqVO);

    HrmRecruitApplicationDO getApplication(Long id);

    void startScreening(HrmRecruitApplicationStatusReqVO reqVO);

    void enterInterview(HrmRecruitApplicationStatusReqVO reqVO);

    /** 面试通过后推进应聘状态。 */
    void markInterviewPassed(Long id);

    /** 面试未通过后推进应聘状态。 */
    void eliminateFromInterview(Long id, String reason);

    void markOfferSent(Long id);

    void markOfferApproving(Long id);
    void markOfferRejected(Long id);

    void markPendingOnboard(Long id);
    void markOnboarded(Long id, Long employeeId);

    void eliminate(HrmRecruitApplicationStatusReqVO reqVO);

    void withdraw(HrmRecruitApplicationStatusReqVO reqVO);
}
