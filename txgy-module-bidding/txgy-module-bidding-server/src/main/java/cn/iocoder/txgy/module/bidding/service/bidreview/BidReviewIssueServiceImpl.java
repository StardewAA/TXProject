package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssuePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueReviewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewIssueStatsRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewReportDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewIssueMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewReportMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 智慧标书审查 · 问题项实现。
 *
 * <p>设计遵循：复核状态只影响"是否计入结论"（IGNORED 视为误报），不物理删除问题——
 * 复核痕迹必须留档（谁在什么时候判定为误报），这正是 AI 审查能被人信任的前提。
 */
@Slf4j
@Service
public class BidReviewIssueServiceImpl implements BidReviewIssueService {

    @Resource
    private BidReviewIssueMapper issueMapper;
    @Resource
    private BidReviewTaskMapper taskMapper;
    // 钉住 bean 名：字段名 reportMapper 若走 @Resource 按名解析会命中 report 域的 ReportMapper（类型不符→启动失败）
    @Resource(name = "bidReviewReportMapper")
    private BidReviewReportMapper reportMapper;
    @Resource
    private BidReviewTaskService taskService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public PageResult<BidReviewIssueDO> getIssuePage(BidReviewIssuePageReqVO reqVO) {
        return issueMapper.selectPage(reqVO);
    }

    @Override
    public List<BidReviewIssueDO> getMyIssueList(Long taskId) {
        // 归属校验（非本人直接抛 BID_REVIEW_TASK_NOT_OWNER）+ 切到任务所属租户读问题项：
        // 个人端提交到企业的任务，问题项挂在企业租户下，跟随登录人租户会取空（2026-09-20）
        return taskService.executeInMyTaskTenant(taskId, () -> issueMapper.selectListByTaskId(taskId));
    }

    @Override
    public void reviewIssue(BidReviewIssueReviewReqVO reqVO) {
        BidReviewIssueDO issue = issueMapper.selectById(reqVO.getId());
        if (issue == null) {
            throw exception(ErrorCodeConstants.BID_REVIEW_ISSUE_NOT_EXISTS);
        }
        issue.setStatus(reqVO.getStatus());
        issue.setReviewRemark(StrUtil.nullToEmpty(reqVO.getReviewRemark()));
        issue.setReviewerId(SecurityFrameworkUtils.getLoginUserId());
        issue.setReviewTime(LocalDateTime.now());
        issueMapper.updateById(issue);
        refreshTaskVerdict(issue.getTaskId());
    }

    @Override
    public void refreshTaskVerdict(Long taskId) {
        BidReviewTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        List<BidReviewIssueDO> issues = issueMapper.selectListByTaskId(taskId);
        long high = issues.stream()
                .filter(i -> !"IGNORED".equals(i.getStatus()))
                .filter(i -> "HIGH".equals(i.getSeverity()))
                .count();
        long medium = issues.stream()
                .filter(i -> !"IGNORED".equals(i.getStatus()))
                .filter(i -> "MEDIUM".equals(i.getSeverity()))
                .count();
        String verdict = high > 0 ? "FAIL" : (medium > 0 ? "RISK" : "PASS");
        task.setVerdict(verdict);
        task.setHighIssueCnt((int) high);
        taskMapper.updateById(task);
        BidReviewReportDO report = reportMapper.selectByTaskId(taskId);
        if (report != null) {
            report.setVerdict(verdict);
            reportMapper.updateById(report);
        }
    }

    @Override
    public BidReviewIssueRespVO toResp(BidReviewIssueDO issue) {
        return BeanUtils.toBean(issue, BidReviewIssueRespVO.class);
    }

    @Override
    public List<BidReviewIssueRespVO> toRespList(List<BidReviewIssueDO> issues) {
        List<BidReviewIssueRespVO> list = issues.stream().map(this::toResp).toList();
        fillTaskInfo(list);
        fillReviewerNames(list);
        return list;
    }

    /** 补任务编号/任务名（一次批量查询，不按行查）。 */
    private void fillTaskInfo(List<BidReviewIssueRespVO> list) {
        List<Long> taskIds = list.stream()
                .map(BidReviewIssueRespVO::getTaskId).filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) {
            return;
        }
        Map<Long, BidReviewTaskDO> taskMap = new HashMap<>();
        for (BidReviewTaskDO task : taskMapper.selectBatchIds(taskIds)) {
            taskMap.put(task.getId(), task);
        }
        list.forEach(resp -> {
            BidReviewTaskDO task = taskMap.get(resp.getTaskId());
            if (task != null) {
                resp.setTaskNo(task.getTaskNo());
                resp.setTaskName(task.getTaskName());
            }
        });
    }

    /** 补复核人名称（一次批量解析，用户服务不可用时只留空，不影响列表）。 */
    private void fillReviewerNames(List<BidReviewIssueRespVO> list) {
        List<Long> userIds = list.stream()
                .map(BidReviewIssueRespVO::getReviewerId).filter(Objects::nonNull).distinct().toList();
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
                if (resp.getReviewerId() != null) {
                    resp.setReviewerName(nameMap.getOrDefault(resp.getReviewerId(), ""));
                }
            });
        } catch (Exception e) {
            log.warn("[fillReviewerNames] 解析复核人名称失败，userIds={}", userIds, e);
        }
    }

    @Override
    public BidReviewIssueStatsRespVO getIssueStats() {
        BidReviewIssueStatsRespVO resp = new BidReviewIssueStatsRespVO();
        resp.setOpenCount(issueMapper.selectCountByStatus("OPEN"));
        resp.setConfirmedCount(issueMapper.selectCountByStatus("CONFIRMED"));
        resp.setIgnoredCount(issueMapper.selectCountByStatus("IGNORED"));
        resp.setFixedCount(issueMapper.selectCountByStatus("FIXED"));
        resp.setTotalCount(issueMapper.selectCountByStatus(null));
        resp.setEvidenceLackCount(issueMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BidReviewIssueDO>()
                        .eq(BidReviewIssueDO::getEvidenceLack, true)));
        return resp;
    }
}
