package cn.iocoder.txgy.module.bidding.service.review;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.review.vo.ReviewTaskStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.review.ReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.review.ReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.ReviewTaskStatusEnum;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台审查任务服务实现。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§2~§6。
 *
 * <p>为什么这样写（设计遵循，逐条对应契约）：
 * <ul>
 *   <li><b>租户隔离（§4）</b>：租户视角强制过滤本企业，且**由服务端按登录身份决定**，
 *       不接受前端传入的"看谁的数据"；这样即使前端被改也无法越权读他企业任务。
 *       <p><b>隔了两层，务必分清（实测踩过坑）</b>：
 *       <ul>
 *         <li>第一层 —— <b>租户</b>：由 MyBatis-Plus 租户插件按 `tenant_id` 自动加条件（DO 继承 TenantBaseDO），
 *             本类**不需要也不应该**再手写一遍。</li>
 *         <li>第二层 —— <b>企业主体</b>：本域 `ent_id` 全仓口径是"企业主体档案 id"（`subjectProfileService.getOwnSubjectId()`），
 *             **不是 tenant_id**。二者恰好都叫 id 却完全不是一回事：实测同一行数据 tenant_id=1、
 *             ent_id=182143816674785。此前把 ent_id 当 tenant_id 比较，导致租户视角永远查不到自己的任务（total=0），
 *             而接口不报错、极难发现。</li>
 *       </ul></li>
 *   <li><b>乐观锁（§4/§0.7）</b>：approve/reject 用 `version` 做 CAS —— 更新条件带 version，
 *       影响行数为 0 即判定并发冲突并报错，绝不静默覆盖他人结论。</li>
 *   <li><b>状态机（§3）</b>：只允许 PENDING → APPROVED / REJECTED；重复审查报状态非法。</li>
 *   <li><b>驳回原因必填（§3）</b>：服务端再校验一次（VO 已有 @NotBlank），防止绕过前端；原因是**租户可见**的整改依据。</li>
 * </ul>
 *
 * <p>业务目标：企业录入即入审查队列；超管统一筛选审查；结论与原因回传租户，可追溯。
 *
 * @author txgy
 */
@Service
@Slf4j
public class ReviewTaskServiceImpl implements ReviewTaskService {

    @Resource
    private ReviewTaskMapper reviewTaskMapper;

    /**
     * 本企业主体档案入口（"我是哪家企业"）。
     *
     * <p>规范出处：《后端变更通知-给前端》§12 —— "我是谁"类 id 一律由后端推导，前端只传"对哪个对象"。
     * <p>为什么注入它而不是用 TenantContextHolder：本域 `ent_id` 是企业主体 id 而非租户号，
     * 与证照建档 `org_id`、员工档案 `ent_id` 用的是同一个入口，口径必须唯一。
     */
    @Resource
    private SubjectProfileService subjectProfileService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(String bizType, Long bizId, Long entId, String entName, String entUscc, String refNo, String remark) {
        ReviewTaskDO task = new ReviewTaskDO();
        task.setBizType(bizType);
        task.setBizId(bizId);
        task.setEntId(entId);
        task.setEntName(entName);
        task.setEntUscc(entUscc);
        task.setRefNo(refNo);
        task.setRemark(remark);
        task.setSubmitTime(LocalDateTime.now());
        task.setStatus(ReviewTaskStatusEnum.PENDING.name());
        reviewTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public PageResult<ReviewTaskRespVO> getTaskPage(ReviewTaskPageReqVO reqVO, boolean tenantScoped) {
        LambdaQueryWrapper<ReviewTaskDO> query = new LambdaQueryWrapper<ReviewTaskDO>()
                .eq(reqVO.getBizType() != null, ReviewTaskDO::getBizType, reqVO.getBizType())
                .eq(reqVO.getStatus() != null, ReviewTaskDO::getStatus, reqVO.getStatus())
                .ge(reqVO.getBeginTime() != null, ReviewTaskDO::getSubmitTime, reqVO.getBeginTime())
                .le(reqVO.getEndTime() != null, ReviewTaskDO::getSubmitTime, reqVO.getEndTime())
                .orderByDesc(ReviewTaskDO::getSubmitTime)
                .orderByDesc(ReviewTaskDO::getId);
        // 关键字：企业名 或 统一社会信用代码（审查者手上通常只有其一）
        if (reqVO.getKeyword() != null && !reqVO.getKeyword().isBlank()) {
            String kw = reqVO.getKeyword().trim();
            query.and(w -> w.like(ReviewTaskDO::getEntName, kw).or().like(ReviewTaskDO::getEntUscc, kw));
        }
        if (tenantScoped) {
            // 按"企业主体 id"过滤（见类注释：两层隔离）。取不到主体档案时传 null ⇒ 不额外过滤，
            // 此时仍受租户插件 tenant_id 条件约束，只会看到本租户的数据，不会越权。
            query.eq(ReviewTaskDO::getEntId, subjectProfileService.getOwnSubjectId());
        }
        var page = reviewTaskMapper.selectPage(reqVO, query);
        return BeanUtils.toBean(page, ReviewTaskRespVO.class);
    }

    @Override
    public ReviewTaskRespVO getTask(Long id, boolean tenantScoped) {
        ReviewTaskDO task = reviewTaskMapper.selectById(id);
        if (task == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_NOT_EXISTS);
        }
        // 越权读取按"不存在"处理：不泄露他企业任务是否存在（避免枚举探测）。
        // 一条 SQL 查询同时受租户插件（tenant_id）与本判断（企业主体 ent_id）约束，两层都过关才放行。
        if (tenantScoped && !java.util.Objects.equals(subjectProfileService.getOwnSubjectId(), task.getEntId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_NOT_EXISTS);
        }
        return BeanUtils.toBean(task, ReviewTaskRespVO.class);
    }

    @Override
    public ReviewTaskStatsRespVO getStats(boolean tenantScoped) {
        // 租户视角只统计本企业主体；平台视角统计全部（仍受租户插件约束）。见类注释"两层隔离"。
        LambdaQueryWrapper<ReviewTaskDO> base = new LambdaQueryWrapper<ReviewTaskDO>()
                .eq(tenantScoped, ReviewTaskDO::getEntId, tenantScoped ? subjectProfileService.getOwnSubjectId() : null);
        ReviewTaskStatsRespVO vo = new ReviewTaskStatsRespVO();
        vo.setPending(reviewTaskMapper.selectCount(base.clone().eq(ReviewTaskDO::getStatus, ReviewTaskStatusEnum.PENDING.name())));
        vo.setApproved(reviewTaskMapper.selectCount(base.clone().eq(ReviewTaskDO::getStatus, ReviewTaskStatusEnum.APPROVED.name())));
        vo.setRejected(reviewTaskMapper.selectCount(base.clone().eq(ReviewTaskDO::getStatus, ReviewTaskStatusEnum.REJECTED.name())));
        vo.setTodayNew(reviewTaskMapper.selectCount(base.clone()
                .ge(ReviewTaskDO::getSubmitTime, LocalDate.now().atStartOfDay())));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long taskId, Integer version, String reviewer) {
        ReviewTaskDO task = requirePending(taskId);
        int updated = reviewTaskMapper.update(null, new LambdaUpdateWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getId, task.getId())
                .eq(ReviewTaskDO::getVersion, version)
                .set(ReviewTaskDO::getStatus, ReviewTaskStatusEnum.APPROVED.name())
                .set(ReviewTaskDO::getReviewer, reviewer)
                .set(ReviewTaskDO::getReviewTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated == 0) {
            // 并发：别人已审查/版本过期 —— 报冲突让用户刷新后重看，绝不静默改写结论
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_MODIFIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long taskId, Integer version, String reason, String reviewer) {
        if (reason == null || reason.isBlank()) {
            // 服务端兜底：驳回原因是企业整改的唯一依据，必须留痕（VO 已校验，此处防绕过）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_REASON_REQUIRED);
        }
        ReviewTaskDO task = requirePending(taskId);
        int updated = reviewTaskMapper.update(null, new LambdaUpdateWrapper<ReviewTaskDO>()
                .eq(ReviewTaskDO::getId, task.getId())
                .eq(ReviewTaskDO::getVersion, version)
                .set(ReviewTaskDO::getStatus, ReviewTaskStatusEnum.REJECTED.name())
                .set(ReviewTaskDO::getReason, reason)
                .set(ReviewTaskDO::getReviewer, reviewer)
                .set(ReviewTaskDO::getReviewTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (updated == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_MODIFIED);
        }
    }

    /** 取任务并校验状态为 PENDING（状态机：只允许 待审查 → 通过/驳回）。 */
    private ReviewTaskDO requirePending(Long taskId) {
        ReviewTaskDO task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_NOT_EXISTS);
        }
        if (!ReviewTaskStatusEnum.PENDING.name().equals(task.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REVIEW_TASK_STATUS_INVALID, task.getStatus());
        }
        return task;
    }

}
