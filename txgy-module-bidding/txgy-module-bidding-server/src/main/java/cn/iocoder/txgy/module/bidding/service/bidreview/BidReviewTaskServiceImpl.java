package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewEnterpriseRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewOverviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewPortalCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskCreateReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskProgressRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewTaskRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewReportDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleSetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectUserDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewAiLogMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewIssueMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewReportMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewRuleItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewRuleSetMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectUserMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 智慧标书审查 · 任务服务实现。
 *
 * <p>方案依据：§4.1、§4.3、§5.1、§5.2。关键点：
 * ① 个人端一律 getLoginUserId() 定位本人（入参无 userId，防越权）；
 * ② 幂等键 = 人 + 标书 + 招标文件，重复点击只产生一个任务（规范 §4.5）；
 * ③ 重试限次（默认 3 次），避免无限烧 token。
 */
@Slf4j
@Service
public class BidReviewTaskServiceImpl implements BidReviewTaskService {

    private static final int MAX_RETRY = 3;

    @Resource
    private BidReviewTaskMapper taskMapper;
    @Resource
    private BidReviewIssueMapper issueMapper;
    // 钉住 bean 名：字段名 reportMapper 若走 @Resource 按名解析会命中 report 域的 ReportMapper（类型不符→启动失败）
    @Resource(name = "bidReviewReportMapper")
    private BidReviewReportMapper reportMapper;
    @Resource
    private BidReviewRuleSetMapper ruleSetMapper;
    @Resource
    private BidReviewRuleItemMapper ruleItemMapper;
    @Resource
    private BidReviewAiLogMapper aiLogMapper;
    @Resource
    private BidReviewPipelineService pipelineService;
    @Resource
    private BidReviewRuleService ruleService;
    @Resource
    private SubjectUserMapper subjectUserMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long createAndGenerateByPortal(BidReviewPortalCreateReqVO reqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        Long enterpriseTenantId = reqVO.getEnterpriseTenantId();
        if (enterpriseTenantId != null) {
            // 归属企业（2026-09-20）：只有"本人已被该企业加为经办人"才允许以此企业名义提交，
            // 避免个人端把记录挂到任意企业名下（授权来源：bidding_subject_user）。
            boolean authorized = subjectUserMapper.selectListByUserId(userId).stream()
                    .anyMatch(item -> enterpriseTenantId.equals(item.getTenantId()));
            if (!authorized) {
                throw exception(ErrorCodeConstants.BID_REVIEW_ENTERPRISE_NOT_AUTHORIZED);
            }
            // 在"企业租户"上下文里建单并触发流水线：任务/问题项/报告全部落到企业租户，
            // 企业端（按租户过滤）即可看到；异步流水线继承该上下文（TenantContextHolder 是 TransmittableThreadLocal）。
            return TenantUtils.execute(enterpriseTenantId, () -> doCreatePortalTask(userId, reqVO));
        }
        return doCreatePortalTask(userId, reqVO);
    }

    /** 个人端建单主体：在"当前租户上下文"里落库并触发流水线（归属企业场景下上下文已被 TenantUtils 切成企业租户）。 */
    private Long doCreatePortalTask(Long userId, BidReviewPortalCreateReqVO reqVO) {
        // 幂等：同人 + 同标书 + 同招标文件 → 复用已有任务，不重复审查（省 token）
        String idempotentKey = buildIdempotentKey(userId, reqVO.getBidFileId(), reqVO.getTenderFileId());
        BidReviewTaskDO exists = taskMapper.selectByIdempotentKey(idempotentKey);
        if (exists != null) {
            return exists.getId();
        }
        BidReviewTaskDO task = new BidReviewTaskDO();
        task.setTaskNo(buildTaskNo());
        task.setTaskName(StrUtil.blankToDefault(reqVO.getBidFileName(), "标书审查-" + task.getTaskNo()));
        task.setSourceScene("PORTAL_SELF");
        task.setOwnerUserId(userId);
        task.setOwnerName(selfName(userId));
        task.setProjectId(reqVO.getProjectId());
        task.setTenderProjectId(reqVO.getTenderProjectId());
        task.setTenderFileId(reqVO.getTenderFileId());
        task.setTenderFileUrl(StrUtil.nullToEmpty(reqVO.getTenderFileUrl()));
        task.setTenderFileName(StrUtil.nullToEmpty(reqVO.getTenderFileName()));
        task.setBidFileId(reqVO.getBidFileId());
        task.setBidFileUrl(StrUtil.nullToEmpty(reqVO.getBidFileUrl()));
        task.setBidFileName(StrUtil.nullToEmpty(reqVO.getBidFileName()));
        task.setRemark(StrUtil.nullToEmpty(reqVO.getRemark()));
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setRuleCnt(0);
        task.setIssueCnt(0);
        task.setHighIssueCnt(0);
        task.setAiCalled(false);
        task.setRetryCount(0);
        task.setIdempotentKey(idempotentKey);
        try {
            taskMapper.insert(task);
        } catch (Exception e) {
            // 并发重复提交：唯一键拦住后返回已存在任务
            BidReviewTaskDO duplicated = taskMapper.selectByIdempotentKey(idempotentKey);
            if (duplicated != null) {
                return duplicated.getId();
            }
            throw e;
        }
        pipelineService.executeAsync(task.getId());
        return task.getId();
    }

    @Override
    public List<BidReviewEnterpriseRespVO> getMyEnterprises() {
        Long userId = currentUserId();
        if (userId == null) {
            return List.of();
        }
        // 同一企业可能有多条授权行（不同主体类型）→ 按租户去重，保留第一条（列表已按 tenantId 升序）
        Map<Long, BidReviewEnterpriseRespVO> merged = new LinkedHashMap<>();
        for (SubjectUserDO item : subjectUserMapper.selectListByUserId(userId)) {
            if (item.getTenantId() == null) {
                continue;
            }
            merged.computeIfAbsent(item.getTenantId(), tenantId -> {
                BidReviewEnterpriseRespVO vo = new BidReviewEnterpriseRespVO();
                vo.setTenantId(tenantId);
                vo.setSubjectType(item.getSubjectType());
                vo.setUserRole(item.getUserRole());
                return vo;
            });
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public Long createTaskByAdmin(BidReviewTaskCreateReqVO reqVO) {
        Long owner = reqVO.getOwnerUserId() != null ? reqVO.getOwnerUserId() : SecurityFrameworkUtils.getLoginUserId();
        BidReviewTaskDO task = new BidReviewTaskDO();
        task.setTaskNo(buildTaskNo());
        task.setTaskName(reqVO.getTaskName());
        task.setSourceScene("ADMIN_TASK");
        task.setOwnerUserId(owner);
        task.setOwnerName(selfName(owner));
        task.setRuleSetId(reqVO.getRuleSetId());
        task.setProjectId(reqVO.getProjectId());
        task.setTenderProjectId(reqVO.getTenderProjectId());
        task.setTenderFileId(reqVO.getTenderFileId());
        task.setTenderFileUrl(StrUtil.nullToEmpty(reqVO.getTenderFileUrl()));
        task.setTenderFileName(StrUtil.nullToEmpty(reqVO.getTenderFileName()));
        task.setBidFileId(reqVO.getBidFileId());
        task.setBidFileUrl(StrUtil.nullToEmpty(reqVO.getBidFileUrl()));
        task.setBidFileName(StrUtil.nullToEmpty(reqVO.getBidFileName()));
        task.setRemark(StrUtil.nullToEmpty(reqVO.getRemark()));
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setRuleCnt(0);
        task.setIssueCnt(0);
        task.setHighIssueCnt(0);
        task.setAiCalled(false);
        task.setRetryCount(0);
        task.setIdempotentKey("");
        taskMapper.insert(task);
        if (!Boolean.FALSE.equals(reqVO.getGenerate())) {
            pipelineService.executeAsync(task.getId());
        }
        return task.getId();
    }

    @Override
    public PageResult<BidReviewTaskRespVO> getMyTaskPage(BidReviewTaskPageReqVO reqVO) {
        // 个人端记录按"归属人"隔离（owner_user_id 已写死进 SQL），不跟随登录人租户：
        // 个人端提交到企业租户的任务同样要能在自己的历史里看到（2026-09-20）。
        return TenantUtils.executeIgnore(() -> toRespPage(taskMapper.selectMyPage(currentUserId(), reqVO)));
    }

    @Override
    public List<BidReviewTaskRespVO> getMySimpleList() {
        return TenantUtils.executeIgnore(() -> {
            BidReviewTaskPageReqVO reqVO = new BidReviewTaskPageReqVO();
            reqVO.setPageNo(1);
            reqVO.setPageSize(20);
            List<BidReviewTaskRespVO> list = taskMapper.selectMyPage(currentUserId(), reqVO).getList().stream()
                    .map(this::toResp).toList();
            fillOwnerNames(list);
            return list;
        });
    }

    @Override
    public BidReviewTaskRespVO getMyTask(Long id) {
        BidReviewTaskDO task = getOwnedTaskOrThrow(id);
        // 详情里带"报告是否就绪/评分项"，这些子表在任务所属租户下（跨租户提交场景）
        return runInTenant(task.getTenantId(), () -> toResp(task));
    }

    @Override
    public BidReviewTaskProgressRespVO getMyTaskProgress(Long id) {
        BidReviewTaskDO task = getOwnedTaskOrThrow(id);
        // 问题项（废标原因）在"任务所属租户"里读：跨租户提交的企业任务也取得到
        return runInTenant(task.getTenantId(), () -> {
            BidReviewTaskProgressRespVO resp = new BidReviewTaskProgressRespVO();
            resp.setId(task.getId());
            resp.setStatus(task.getStatus());
            resp.setCurrentStep(task.getCurrentStep());
            resp.setProgress(task.getProgress());
            resp.setVerdict(task.getVerdict());
            resp.setIssueCnt(task.getIssueCnt());
            resp.setHighIssueCnt(task.getHighIssueCnt());
            resp.setErrorCode(task.getErrorCode());
            resp.setErrorMsg(task.getErrorMsg());
            // 废标判定：命中高危项（且未被复核忽略）即视为存在废标风险
            resp.setRejected("FAIL".equals(task.getVerdict()));
            resp.setRejectReasons(collectRejectReasons(task.getId()));
            return resp;
        });
    }

    @Override
    public void retryMyTask(Long id) {
        BidReviewTaskDO task = getOwnedTaskOrThrow(id);
        // 重试要在任务自己的租户里跑：异步流水线会写问题项/报告，租户错了企业管理端就看不到
        runInTenant(task.getTenantId(), () -> doRetry(task));
    }

    @Override
    public void cancelMyTask(Long id) {
        BidReviewTaskDO task = getOwnedTaskOrThrow(id);
        runInTenant(task.getTenantId(), () -> doCancel(task));
    }

    @Override
    public void deleteMyTask(Long id) {
        BidReviewTaskDO task = getOwnedTaskOrThrow(id);
        runInTenant(task.getTenantId(), () -> taskMapper.deleteById(task.getId()));
    }

    @Override
    public <T> T executeInMyTaskTenant(Long taskId, Supplier<T> action) {
        BidReviewTaskDO task = getOwnedTaskOrThrow(taskId);
        return runInTenant(task.getTenantId(), action);
    }

    /** 在指定租户上下文里执行（tenantId 为空时按当前上下文原样执行，避免 NPE）。 */
    private <T> T runInTenant(Long tenantId, Supplier<T> action) {
        return tenantId == null ? action.get() : TenantUtils.execute(tenantId, action::get);
    }

    private void runInTenant(Long tenantId, Runnable action) {
        if (tenantId == null) {
            action.run();
        } else {
            TenantUtils.execute(tenantId, action);
        }
    }

    @Override
    public PageResult<BidReviewTaskRespVO> getTaskPage(BidReviewTaskPageReqVO reqVO) {
        return toRespPage(taskMapper.selectPage(reqVO));
    }

    @Override
    public BidReviewTaskRespVO getTask(Long id) {
        return toResp(getTaskOrThrow(id));
    }

    @Override
    public void retryTask(Long id) {
        doRetry(getTaskOrThrow(id));
    }

    @Override
    public void cancelTask(Long id) {
        doCancel(getTaskOrThrow(id));
    }

    @Override
    public BidReviewOverviewRespVO getOverview() {
        BidReviewOverviewRespVO resp = new BidReviewOverviewRespVO();
        resp.setTotalCount(countByStatus(null));
        resp.setRunningCount(countByStatus("RUNNING"));
        resp.setSuccessCount(countByStatus("SUCCESS"));
        resp.setFailedCount(countByStatus("FAILED"));
        List<BidReviewOverviewRespVO.NameCount> verdictStats = new ArrayList<>();
        for (String verdict : List.of("PASS", "RISK", "FAIL")) {
            BidReviewOverviewRespVO.NameCount item = new BidReviewOverviewRespVO.NameCount();
            item.setName(verdict);
            item.setCount(taskMapper.selectCount(new LambdaQueryWrapper<BidReviewTaskDO>()
                    .eq(BidReviewTaskDO::getVerdict, verdict)));
            verdictStats.add(item);
        }
        resp.setVerdictStats(verdictStats);
        List<BidReviewOverviewRespVO.NameCount> severityStats = new ArrayList<>();
        for (String severity : List.of("HIGH", "MEDIUM", "LOW")) {
            BidReviewOverviewRespVO.NameCount item = new BidReviewOverviewRespVO.NameCount();
            item.setName(severity);
            item.setCount(issueMapper.selectCount(new LambdaQueryWrapper<BidReviewIssueDO>()
                    .eq(BidReviewIssueDO::getSeverity, severity)));
            severityStats.add(item);
        }
        resp.setSeverityStats(severityStats);
        // 高频问题 Top10：按【规则名称】聚合（2026-09-18 用户口径：不要展示规则编码）。
        // 名称取值优先级：规则项名称（rule_item.rule_name）→ 问题标题 → 规则编码（兜底，避免出现空名）。
        // 编码不再作为展示值，但仍保留在问题清单里，便于按码排查。
        Map<String, Long> top = new java.util.LinkedHashMap<>();
        List<BidReviewIssueDO> recentIssues = issueMapper.selectList(new LambdaQueryWrapper<BidReviewIssueDO>()
                .orderByDesc(BidReviewIssueDO::getId).last("LIMIT 500"));
        Map<Long, String> ruleNames = resolveRuleNames(recentIssues);
        for (BidReviewIssueDO issue : recentIssues) {
            String name = ruleNames.get(issue.getRuleItemId());
            if (StrUtil.isBlank(name)) {
                name = issue.getIssueTitle();
            }
            if (StrUtil.isBlank(name)) {
                name = issue.getRuleCode();
            }
            top.merge(StrUtil.blankToDefault(name, "-"), 1L, Long::sum);
        }
        List<BidReviewOverviewRespVO.NameCount> topRules = top.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    BidReviewOverviewRespVO.NameCount item = new BidReviewOverviewRespVO.NameCount();
                    item.setName(e.getKey());
                    item.setCount(e.getValue());
                    return item;
                }).toList();
        resp.setTopRules(topRules);
        Long calls = aiLogMapper.selectCountSince(24);
        Long failed = aiLogMapper.selectCountByStatusSince("FAILED", 24);
        resp.setAiCallsLast24h(calls);
        resp.setAiFailureRate(calls == null || calls == 0 ? 0D : (double) (failed == null ? 0 : failed) / calls);
        return resp;
    }

    @Override
    public BidReviewTaskDO getTaskOrThrow(Long id) {
        BidReviewTaskDO task = id == null ? null : taskMapper.selectById(id);
        if (task == null) {
            throw exception(ErrorCodeConstants.BID_REVIEW_TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public void updateTask(BidReviewTaskDO task) {
        taskMapper.updateById(task);
    }

    // ==================== 内部 ====================

    private void doRetry(BidReviewTaskDO task) {
        if ("RUNNING".equals(task.getStatus())) {
            throw exception(ErrorCodeConstants.BID_REVIEW_TASK_RUNNING);
        }
        int retried = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retried >= MAX_RETRY) {
            throw exception(ErrorCodeConstants.BID_REVIEW_RETRY_LIMIT);
        }
        // 清空上次结果：问题清单按本次规则集重新生成；历史正文仍在 content 表留痕。
        issueMapper.deleteByTaskId(task.getId());
        // 【2026-09-18 修复】不再软删除报告行，只清空它上一次生成的 Word/PDF 指针：
        //   ① 报告正文由流水线就地 updateById 覆盖（见 BidReviewPipelineServiceImpl §4 REPORT），不需要先删；
        //   ② report_no = "BRR" + taskId 会复用，而 uk_report_no(report_no, deleted, tenant_id)
        //      只允许「一行未删 + 一行已删」，第二次重跑再软删就撞唯一键
        //      （实测 task 8/BRR8/租户2019：Duplicate entry 'BRR8-1-2019'），
        //      报错点正好在"问题项已删、任务未重置"之间，导致重跑 500 且问题清单凭空消失；
        //   ③ 指针必须清掉，否则重跑后「下载 Word」会拿到上一次的文档（updateById 不覆盖 null 字段）。
        reportMapper.clearGeneratedFilesByTaskId(task.getId());
        task.setRetryCount(retried + 1);
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setVerdict(null);
        task.setIssueCnt(0);
        task.setHighIssueCnt(0);
        task.setErrorCode("");
        task.setErrorMsg("");
        taskMapper.updateById(task);
        pipelineService.executeAsync(task.getId());
    }

    private void doCancel(BidReviewTaskDO task) {
        if ("SUCCESS".equals(task.getStatus()) || "CANCELED".equals(task.getStatus())) {
            return;
        }
        task.setStatus("CANCELED");
        task.setFinishedTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private Long countByStatus(String status) {
        return taskMapper.selectCount(new LambdaQueryWrapper<BidReviewTaskDO>()
                .eq(status != null, BidReviewTaskDO::getStatus, status));
    }

    private BidReviewTaskDO getOwnedTaskOrThrow(Long id) {
        // 跨租户读：个人端提交的企业任务挂在企业租户下，用登录人租户过滤会查不到；
        // 越权防线是下面的"归属人 == 当前登录人"，与租户无关（个人端只应看到自己的记录）。
        BidReviewTaskDO task = TenantUtils.executeIgnore(() ->
                id == null ? null : taskMapper.selectById(id));
        if (task == null) {
            throw exception(ErrorCodeConstants.BID_REVIEW_TASK_NOT_EXISTS);
        }
        Long userId = currentUserId();
        if (userId == null || !userId.equals(task.getOwnerUserId())) {
            throw exception(ErrorCodeConstants.BID_REVIEW_TASK_NOT_OWNER);
        }
        return task;
    }

    private Long currentUserId() {
        return SecurityFrameworkUtils.getLoginUserId();
    }

    /** 建单时的名称快照：只有"归属人 == 当前登录人"才拿得到名字，替他人建单留空、由列表页批量补齐。 */
    private String selfName(Long ownerUserId) {
        if (ownerUserId == null || !ownerUserId.equals(currentUserId())) {
            return "";
        }
        return StrUtil.nullToEmpty(SecurityFrameworkUtils.getLoginUserNickname());
    }

    private PageResult<BidReviewTaskRespVO> toRespPage(PageResult<BidReviewTaskDO> page) {
        List<BidReviewTaskRespVO> list = page.getList().stream().map(this::toResp).toList();
        fillOwnerNames(list);
        return new PageResult<>(list, page.getTotal());
    }

    /**
     * 补齐"提交人"名称。
     *
     * <p>为什么需要：owner_name 是建单时的名称快照，历史数据（2026-09-16 之前建的任务）为空，
     * 且管理端"替他人建单"时当前登录人不是归属人，快照也写不出别人的名字。
     * 因此列表页按 owner_user_id 批量解析一次（一次 Feign 调用，不是 N+1），
     * 解析失败只留空、不影响列表本身可用。
     */
    private void fillOwnerNames(List<BidReviewTaskRespVO> list) {
        List<Long> userIds = list.stream()
                .filter(resp -> StrUtil.isBlank(resp.getOwnerName()))
                .map(BidReviewTaskRespVO::getOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        try {
            List<AdminUserRespDTO> users = adminUserApi.getUserList(userIds).getCheckedData();
            Map<Long, String> nameMap = new HashMap<>();
            for (AdminUserRespDTO user : users) {
                nameMap.put(user.getId(), StrUtil.nullToEmpty(user.getNickname()));
            }
            list.forEach(resp -> {
                if (StrUtil.isBlank(resp.getOwnerName())) {
                    resp.setOwnerName(nameMap.getOrDefault(resp.getOwnerUserId(), ""));
                }
            });
        } catch (Exception e) {
            // 名称解析是展示增强，不做 fail-fast：用户服务不可用时列表仍应能打开
            log.warn("[fillOwnerNames] 解析归属人名称失败，userIds={}", userIds, e);
        }
    }

    /**
     * 批量解析规则名称（rule_item_id → rule_name）。
     *
     * <p>为什么按名称而不是编码：用户在「高频问题 TOP10」里要看的是"哪类问题最常出现"，
     * 编码（如 P012）对人不可读；编码仍保留在问题清单里作为排查依据。
     * 规则项可能已被删除或在个人端由 AI 从招标文件临时抽取（无 rule_item_id），
     * 此时由调用方回落到问题标题与编码。
     */
    private Map<Long, String> resolveRuleNames(List<BidReviewIssueDO> issues) {
        List<Long> ruleItemIds = issues.stream()
                .map(BidReviewIssueDO::getRuleItemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ruleItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (BidReviewRuleItemDO item : ruleItemMapper.selectBatchIds(ruleItemIds)) {
            names.put(item.getId(), item.getRuleName());
        }
        return names;
    }

    private BidReviewTaskRespVO toResp(BidReviewTaskDO task) {
        BidReviewTaskRespVO resp = BeanUtils.toBean(task, BidReviewTaskRespVO.class);
        if (task.getRuleSetId() != null) {
            BidReviewRuleSetDO ruleSet = ruleSetMapper.selectById(task.getRuleSetId());
            if (ruleSet != null) {
                resp.setRuleSetName(ruleSet.getRuleSetName());
            }
        }
        BidReviewReportDO report = reportMapper.selectByTaskId(task.getId());
        resp.setReportReady(report != null);
        resp.setRejected("FAIL".equals(task.getVerdict()));
        resp.setRejectReasons(collectRejectReasons(task.getId()));
        if (report != null) {
            resp.setScoreItems(parseScoreItems(report.getScoreJson()));
        }
        return resp;
    }

    /** 废标原因：命中高危且未被忽略的规则名称（去重、保序）。 */
    private List<String> collectRejectReasons(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        // 按 taskId 取问题项（taskId 全局唯一）：跨租户读，避免"任务在 A 租户、读的人在 B 租户"时取空
        return TenantUtils.executeIgnore(() -> issueMapper.selectListByTaskId(taskId)).stream()
                .filter(i -> "HIGH".equals(i.getSeverity()))
                .filter(i -> !"IGNORED".equals(i.getStatus()))
                .map(BidReviewIssueDO::getIssueTitle)
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<BidReviewTaskRespVO.ScoreItem> parseScoreItems(String scoreJson) {
        if (StrUtil.isBlank(scoreJson)) {
            return List.of();
        }
        try {
            Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(scoreJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
            List<BidReviewTaskRespVO.ScoreItem> items = new ArrayList<>();
            map.forEach((key, value) -> {
                BidReviewTaskRespVO.ScoreItem item = new BidReviewTaskRespVO.ScoreItem();
                item.setCategory(key);
                try {
                    item.setScore(new BigDecimal(String.valueOf(value)));
                } catch (Exception ignore) {
                    item.setScore(BigDecimal.ZERO);
                }
                items.add(item);
            });
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildIdempotentKey(Long userId, Long bidFileId, Long tenderFileId) {
        return "P-" + userId + "-" + bidFileId + "-" + (tenderFileId == null ? 0 : tenderFileId);
    }

    private String buildTaskNo() {
        return "BR" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + RandomUtil.randomNumbers(3);
    }

    /** 供其它服务判断错误类型（ServiceException 透传）。 */
    private ServiceException serviceError(String message) {
        return exception(ErrorCodeConstants.BID_REVIEW_TASK_NOT_EXISTS, message);
    }
}
