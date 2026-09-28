package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewOverviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewEnterpriseRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewPortalCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskProgressRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;

import java.util.List;
import java.util.function.Supplier;

/**
 * 智慧标书审查 · 任务服务（个人端自助 + 管理端管控）。
 *
 * <p>方案依据：§4.1（个人端最简路径）、§4.3（状态机）、§5.1/§5.2（接口）。
 * <b>越权口径</b>：个人端所有方法都以当前登录人（getLoginUserId）为准，入参不含 userId。
 */
public interface BidReviewTaskService {

    /** 个人端：创建任务并立即触发一键生成（幂等：同人同文件只产生一个任务）。 */
    Long createAndGenerateByPortal(BidReviewPortalCreateReqVO reqVO);

    /**
     * 个人端：我已被授权为经办人的企业列表（上传页"所属企业"下拉用）。
     *
     * <p>来源：bidding_subject_user（企业把该用户加为经办人的授权行）。
     * 顺序即"企业 → 记录"的归属依据：个人端选中的企业会被写成任务的租户，企业端据此可见。
     */
    List<BidReviewEnterpriseRespVO> getMyEnterprises();

    /** 管理端：建单（可指定归属人/规则集/招标文件），generate=true 时立即执行。 */
    Long createTaskByAdmin(BidReviewTaskCreateReqVO reqVO);

    /** 个人端：我的记录分页。 */
    PageResult<BidReviewTaskRespVO> getMyTaskPage(BidReviewTaskPageReqVO reqVO);

    /** 个人端：我的最近记录（下拉用）。 */
    List<BidReviewTaskRespVO> getMySimpleList();

    /** 个人端：详情（非本人 → BID_REVIEW_TASK_NOT_OWNER）。 */
    BidReviewTaskRespVO getMyTask(Long id);

    /** 个人端：轻量进度（轮询）。 */
    BidReviewTaskProgressRespVO getMyTaskProgress(Long id);

    /** 个人端：失败重试（限次）。 */
    void retryMyTask(Long id);

    /** 个人端：取消。 */
    void cancelMyTask(Long id);

    /** 个人端：逻辑删除本人记录。 */
    void deleteMyTask(Long id);

    /**
     * 个人端：先按"归属人"校验（跨租户也认），再切到"任务所属租户"执行 action。
     *
     * <p>为什么需要（2026-09-20）：个人端可把任务提交到企业租户（上传页"所属企业"），
     * 之后个人端读问题项/报告、重试、取消都必须落在"任务自己的租户"里才自洽——
     * 个人账号通常属于平台默认租户，若一律跟随登录人租户，这些子表查询会全部落空。
     * 安全性由 action 执行前的归属校验保证（非本人直接抛 BID_REVIEW_TASK_NOT_OWNER）。
     */
    <T> T executeInMyTaskTenant(Long taskId, Supplier<T> action);

    /** 管理端：分页。 */
    PageResult<BidReviewTaskRespVO> getTaskPage(BidReviewTaskPageReqVO reqVO);

    /** 管理端：详情（不校验归属）。 */
    BidReviewTaskRespVO getTask(Long id);

    /** 管理端：重跑（清空旧问题与报告后重新执行）。 */
    void retryTask(Long id);

    /** 管理端：取消。 */
    void cancelTask(Long id);

    /** 管理端：统计看板。 */
    BidReviewOverviewRespVO getOverview();

    /** 供报告/问题服务复用的任务查询（不存在即抛错）。 */
    BidReviewTaskDO getTaskOrThrow(Long id);

    /** 供流水线复用的更新。 */
    void updateTask(BidReviewTaskDO task);
}
