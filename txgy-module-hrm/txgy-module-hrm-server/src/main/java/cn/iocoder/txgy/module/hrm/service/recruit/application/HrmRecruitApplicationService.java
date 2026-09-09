package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplicationPageReqVO;

/** HRM 招聘应聘流程服务。 */
public interface HrmRecruitApplicationService {

    Long createApplication(HrmRecruitApplicationCreateReqVO reqVO);

    PageResult<HrmRecruitApplicationDO> getApplicationPage(HrmRecruitApplicationPageReqVO reqVO);

    HrmRecruitApplicationDO getApplication(Long id);

    /**
     * 查询候选人当前岗位最新一条应聘记录，用于录用转员工时同步状态。
     */
    HrmRecruitApplicationDO getLatestRecruitApplicationByCandidateAndPost(Long candidateId, Long postId);

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

    Long createPortalApplication(Long applicantUserId, HrmPortalRecruitApplyReqVO reqVO);

    PageResult<HrmRecruitApplicationDO> getPortalApplicationPage(Long applicantUserId,
                                                                   HrmPortalRecruitApplicationPageReqVO reqVO);

    HrmRecruitApplicationDO getPortalApplication(Long applicantUserId, Long id);

    void withdrawPortalApplication(Long applicantUserId, Long id, String reason);
}
