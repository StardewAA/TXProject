package cn.iocoder.txgy.module.hrm.service.recruit.application;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitApplicationStatusReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplyReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.HrmPortalRecruitApplicationPageReqVO;

import java.util.List;

/** HRM 招聘应聘流程服务。 */
public interface HrmRecruitApplicationService {

    Long createApplication(HrmRecruitApplicationCreateReqVO reqVO);

    /** HR 从招聘沟通手动转为候选人后创建门户可见的应聘记录。 */
    Long createApplicationFromRecruitChat(Long applicantUserId, Long candidateId, Long postId, Long resumeId);

    PageResult<HrmRecruitApplicationDO> getApplicationPage(HrmRecruitApplicationPageReqVO reqVO);

    HrmRecruitApplicationDO getApplication(Long id);

    /**
     * 按候选人查询其全部应聘记录（候选人详情的「Offer」Tab 需要据此汇总 Offer）
     *
     * @param candidateId 候选人编号
     * @return 应聘记录列表
     */
    List<HrmRecruitApplicationDO> getApplicationListByCandidateId(Long candidateId);

    /**
     * 查询候选人当前岗位最新一条应聘记录，用于录用转员工时同步状态。
     */
    HrmRecruitApplicationDO getLatestRecruitApplicationByCandidateAndPost(Long candidateId, Long postId);

    HrmRecruitApplicationDO getCurrentRecruitApplicationByCandidateId(Long candidateId);

    void startScreening(HrmRecruitApplicationStatusReqVO reqVO);

    void passScreening(HrmRecruitApplicationStatusReqVO reqVO);

    void rejectResume(HrmRecruitApplicationStatusReqVO reqVO);

    void enterInterview(HrmRecruitApplicationStatusReqVO reqVO);

    void skipInterview(Long id);

    void markInterviewScheduled(Long id);

    void markInterviewStarted(Long id);

    void markInterviewEvaluating(Long id);

    void markInterviewCanceled(Long id, String reason);

    void markInterviewAbsent(Long id, String reason);

    void markInterviewRejected(Long id, String reason);

    void markNextInterview(Long id, String reason);

    void markFinalInterviewPassed(Long id);

    void markPendingOffer(Long id);

    /** 面试通过后推进应聘状态。 */
    void markInterviewPassed(Long id);

    /** 面试未通过后推进应聘状态。 */
    void eliminateFromInterview(Long id, String reason);

    void markOfferSent(Long id);

    void markOfferApproving(Long id);
    void markOfferRejected(Long id);
    void markOfferRejected(Long id, String reason);

    void markOfferWithdrawn(Long id, String reason);

    void markOfferExpired(Long id, String reason);

    void markOfferCandidateRejected(Long id, String reason);

    void markPendingOnboard(Long id);
    void linkEmployeeForOnboard(Long id, Long employeeId);
    void markOnboarded(Long id, Long employeeId);

    void eliminate(HrmRecruitApplicationStatusReqVO reqVO);

    void withdraw(HrmRecruitApplicationStatusReqVO reqVO);

    Long createPortalApplication(Long applicantUserId, HrmPortalRecruitApplyReqVO reqVO);

    PageResult<HrmRecruitApplicationDO> getPortalApplicationPage(Long applicantUserId,
                                                                   HrmPortalRecruitApplicationPageReqVO reqVO);

    HrmRecruitApplicationDO getPortalApplication(Long applicantUserId, Long id);

    void withdrawPortalApplication(Long applicantUserId, Long id, String reason);
}
