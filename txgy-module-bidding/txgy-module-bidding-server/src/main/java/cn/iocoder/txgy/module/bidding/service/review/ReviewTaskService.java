package cn.iocoder.txgy.module.bidding.service.review;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskStatsRespVO;

/**
 * 平台审查任务服务。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§3 状态机 / §4 权限与租户隔离 / §5 接口 / §6 缺口 A1~A4。
 *
 * <p>为什么这样设计（设计遵循）：
 * <ul>
 *   <li>`createTask` 由建档接口**内部调用**（前端无单独创建入口，契约 §5"触发"），保证"录入即入审查队列"不会漏；</li>
 *   <li>查询类方法统一带 `tenantScoped` 参数，由控制器按登录身份传入 —— **可见范围由后端决定**，
 *       不做成请求参数（否则可被越权构造）。</li>
 * </ul>
 *
 * <p>业务目标：企业录入与平台审查解耦；审查结论可追溯、可回传租户。
 *
 * @author txgy
 */
public interface ReviewTaskService {

    /**
     * 建档成功后生成审查任务（契约 §5 触发时机）。
     *
     * @param bizType 业务类型（QUALIFICATION / CERTIFICATE / PERSON_CERT）
     * @param bizId   业务主键
     * @param entId   提交企业 id
     * @param entName 企业名称快照
     * @param entUscc 统一社会信用代码快照
     * @param refNo   证书/资质编号快照
     * @param remark  提交备注
     * @return 任务 id
     */
    Long createTask(String bizType, Long bizId, Long entId, String entName, String entUscc, String refNo, String remark);

    /** 分页（tenantScoped=true 时强制只看本企业）。 */
    PageResult<ReviewTaskRespVO> getTaskPage(ReviewTaskPageReqVO reqVO, boolean tenantScoped);

    /** 详情（tenantScoped=true 时校验归属，非本企业返回不存在）。 */
    ReviewTaskRespVO getTask(Long id, boolean tenantScoped);

    /** 统计（pending/todayNew/approved/rejected）。 */
    ReviewTaskStatsRespVO getStats(boolean tenantScoped);

    /** 审查通过（乐观锁 CAS）。 */
    void approve(Long taskId, Integer version, String reviewer);

    /** 审查驳回（乐观锁 CAS + 原因必填）。 */
    void reject(Long taskId, Integer version, String reason, String reviewer);

}
