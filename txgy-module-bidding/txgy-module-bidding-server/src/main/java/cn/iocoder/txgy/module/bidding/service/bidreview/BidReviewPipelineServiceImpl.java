package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewContentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewIssueDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewReportDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleItemDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewRuleSetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewContentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewIssueMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewReportMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewRuleItemMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.framework.async.config.BidReviewExecutorConfig;
import cn.iocoder.txgy.module.bidding.framework.ai.config.BidReviewAiProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智慧标书审查 · 流水线实现。
 *
 * <p>方案依据：§4.2、§4.3、§6.3。关键口径：
 * <ol>
 *   <li>扫描件（解析不出文字）→ <b>不调模型</b>，直接 FAILED(PARSE_EMPTY) 并提示上传文字版（§6.3）；</li>
 *   <li>AI 未配置/失败 → <b>降级</b>为规则匹配，任务仍 SUCCESS，报告标注"AI 未启用/已降级"（不假装成功）；</li>
 *   <li>模型返回按规则白名单过滤（防幻觉）；标书没有对应内容时原文证据写"标书没有对应内容"，
 *       并**保持规则原级别**（高危仍按高危计，用户 2026-09-17 口径）；</li>
 *   <li>正文与 AI 原始返回按规范 §5.3 落 content 表（追加式版本，不覆盖历史）。</li>
 * </ol>
 */
@Slf4j
@Service
public class BidReviewPipelineServiceImpl implements BidReviewPipelineService {

    private static final String STEP_PARSE = "PARSE";
    private static final String STEP_RULE = "RULE";
    private static final String STEP_GENERATE = "GENERATE";
    private static final String STEP_REPORT = "REPORT";

    @Resource
    private BidReviewTaskMapper taskMapper;
    @Resource
    private BidReviewRuleItemMapper ruleItemMapper;
    @Resource
    private BidReviewIssueMapper issueMapper;
    // 钉住 bean 名：字段名 reportMapper 若走 @Resource 按名解析会命中 report 域的 ReportMapper（类型不符→启动失败）
    @Resource(name = "bidReviewReportMapper")
    private BidReviewReportMapper reportMapper;
    @Resource
    private BidReviewContentMapper contentMapper;
    @Resource
    private BidReviewParseService parseService;
    @Resource
    private BidReviewAiReviewService aiReviewService;
    @Resource
    private BidReviewRuleService ruleService;
    @Resource
    private BidReviewAiProperties aiProperties;

    @Override
    @Async(BidReviewExecutorConfig.EXECUTOR_NAME)
    public void executeAsync(Long taskId) {
        execute(taskId);
    }

    /** 流水线主体：步骤级异常都收敛成任务 FAILED，不让异常吞掉状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long taskId) {
        BidReviewTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        long start = System.currentTimeMillis();
        task.setStatus("RUNNING");
        task.setStartedTime(LocalDateTime.now());
        task.setProgress(5);
        task.setCurrentStep(STEP_PARSE);
        task.setErrorCode("");
        task.setErrorMsg("");
        task.setAiCalled(false);
        taskMapper.updateById(task);
        boolean aiCalled = false;
        try {
            // ---------- 1) PARSE ----------
            BidReviewParseService.ParseResult bid = parseService.parse(task.getBidFileUrl(), task.getBidFileName());
            if (bid.isEmpty()) {
                // 扫描件：不调模型，明确失败（DeepSeek 无视觉模型，见方案 §2.4 约束③）
                fail(task, "BID_REVIEW_PARSE_EMPTY", "文件无法解析出文字，扫描件请上传文字版 PDF 或 Word", start, false);
                return;
            }
            task.setParseMode(bid.mode());
            task.setBidFileSha256(DigestUtil.sha256Hex(bid.text()));
            appendContent(taskId, "BID_TEXT", bid.text());
            updateStep(task, STEP_PARSE, 25);

            String tenderText = "";
            if (StrUtil.isNotBlank(task.getTenderFileUrl())) {
                BidReviewParseService.ParseResult tender =
                        parseService.parse(task.getTenderFileUrl(), StrUtil.blankToDefault(task.getTenderFileName(), "招标文件"));
                if (!tender.isEmpty()) {
                    tenderText = tender.text();
                    task.setTenderFileSha256(DigestUtil.sha256Hex(tenderText));
                    appendContent(taskId, "TENDER_TEXT", tenderText);
                }
            }

            // ---------- 2) RULE ----------
            updateStep(task, STEP_RULE, 35);
            BidReviewRuleSetDO ruleSet = ruleService.resolveRuleSetForTask(task, tenderText);
            task.setRuleSetId(ruleSet.getId());
            List<BidReviewRuleItemDO> rules = ruleItemMapper.selectEnabledListByRuleSetId(ruleSet.getId());
            task.setRuleCnt(rules.size());
            if (rules.isEmpty()) {
                // 没有规则就没有审查依据：明确失败，而不是"空报告=通过"
                fail(task, "BID_REVIEW_RULE_SET_NOT_EXISTS", "没有可用的审查规则（规则集为空），请先维护规则库", start, aiCalled);
                return;
            }
            updateStep(task, STEP_RULE, 50);

            // ---------- 3) GENERATE ----------
            issueMapper.deleteByTaskId(taskId);
            List<BidReviewIssueDO> issues = new ArrayList<>();
            List<BidReviewRuleItemDO> aiRules = rules.stream()
                    .filter(r -> !"RULE".equalsIgnoreCase(r.getCheckMode()))
                    .collect(Collectors.toList());
            if (!aiRules.isEmpty() && aiReviewService.isAvailable()) {
                aiCalled = true;
                task.setAiCalled(true);
                List<BidReviewAiReviewService.ItemResult> results =
                        aiReviewService.reviewRules(aiRules, bid.text(), taskId);
                Map<String, BidReviewRuleItemDO> ruleMap = aiRules.stream()
                        .collect(Collectors.toMap(BidReviewRuleItemDO::getRuleCode, r -> r, (a, b) -> a));
                for (BidReviewAiReviewService.ItemResult result : results) {
                    if (!result.isProblem()) {
                        continue;
                    }
                    issues.add(buildIssue(taskId, ruleMap.get(result.ruleCode()), result, "AI"));
                }
            } else if (!aiRules.isEmpty()) {
                log.warn("[execute][AI 未启用或未配置，语义规则降级为未判定 taskId={}]", taskId);
            }
            // 规则类（check_mode=RULE）：确定性关键词匹配，不消耗模型调用
            for (BidReviewRuleItemDO rule : rules) {
                if (!"RULE".equalsIgnoreCase(rule.getCheckMode())) {
                    continue;
                }
                BidReviewAiReviewService.ItemResult result = ruleCheck(rule, bid.text());
                if (result.isProblem()) {
                    issues.add(buildIssue(taskId, rule, result, "RULE"));
                }
            }
            for (BidReviewIssueDO issue : issues) {
                issueMapper.insert(issue);
            }
            updateStep(task, STEP_GENERATE, 80);

            // ---------- 4) REPORT ----------
            BidReviewReportDO report = buildReport(task, rules, issues, !aiCalled && !aiRules.isEmpty());
            BidReviewReportDO exists = reportMapper.selectByTaskId(taskId);
            if (exists == null) {
                reportMapper.insert(report);
            } else {
                report.setId(exists.getId());
                reportMapper.updateById(report);
            }
            appendContent(taskId, "REPORT_MD", StrUtil.nullToEmpty(report.getConclusion()));

            long highCount = issues.stream()
                    .filter(i -> "HIGH".equals(i.getSeverity()))
                    .count();
            task.setIssueCnt(issues.size());
            task.setHighIssueCnt((int) highCount);
            task.setVerdict(report.getVerdict());
            task.setScoreTotal(report.getScoreTotal());
            task.setScoreJson(report.getScoreJson());
            task.setStatus("SUCCESS");
            task.setCurrentStep(STEP_REPORT);
            task.setProgress(100);
            task.setFinishedTime(LocalDateTime.now());
            task.setCostMs(System.currentTimeMillis() - start);
            taskMapper.updateById(task);
        } catch (ServiceException e) {
            String code = e.getCode() == null ? "BID_REVIEW_AI_CALL_FAILED" : errorCodeName(e);
            fail(task, code, StrUtil.sub(e.getMessage(), 0, 200), start, aiCalled);
        } catch (Exception e) {
            log.error("[execute][审查流水线异常 taskId={}]", taskId, e);
            fail(task, "BID_REVIEW_AI_CALL_FAILED", "审查执行异常：" + e.getClass().getSimpleName(), start, aiCalled);
        }
    }

    /** 规则类检查：evidence_hint 里用 | 或 、分隔关键词，全部未命中即视为"标书中找不到相关内容"。 */
    private BidReviewAiReviewService.ItemResult ruleCheck(BidReviewRuleItemDO rule, String text) {
        String hint = StrUtil.nullToEmpty(rule.getEvidenceHint());
        if (StrUtil.isBlank(hint)) {
            return new BidReviewAiReviewService.ItemResult(rule.getRuleCode(), "PASS", "无检查点提示，跳过",
                    "", "", null, BigDecimal.ONE, null);
        }
        String[] keywords = hint.split("[|、,，]");
        int hit = 0;
        for (String keyword : keywords) {
            String k = keyword.trim();
            if (!k.isEmpty() && StrUtil.contains(text, k)) {
                hit++;
            }
        }
        if (hit == 0) {
            return new BidReviewAiReviewService.ItemResult(rule.getRuleCode(), "NOT_FOUND",
                    "标书中未出现检查点关键词：" + hint, "", "", "请补充：" + rule.getRequirement(),
                    BigDecimal.valueOf(0.6), null);
        }
        return new BidReviewAiReviewService.ItemResult(rule.getRuleCode(), "PASS",
                "命中检查点关键词 " + hit + " 个", "", "", null, BigDecimal.ONE, null);
    }

    private BidReviewIssueDO buildIssue(Long taskId, BidReviewRuleItemDO rule,
                                        BidReviewAiReviewService.ItemResult result, String source) {
        BidReviewIssueDO issue = new BidReviewIssueDO();
        issue.setTaskId(taskId);
        issue.setRuleItemId(rule == null ? null : rule.getId());
        issue.setRuleCode(result.ruleCode());
        issue.setCategory(rule == null ? "FORM" : rule.getCategory());
        issue.setSeverity(rule == null ? "MEDIUM" : rule.getSeverity());
        issue.setIssueTitle(rule == null ? result.ruleCode() : rule.getRuleName());
        issue.setIssueDesc(result.reason());
        issue.setHitLocation(result.hitLocation());
        issue.setEvidenceText(result.evidenceText());
        // 证据口径（用户 2026-09-17 调整）：标书里没有对应内容 = 该要求未满足 → **保持规则级别**
        // （高危仍按高危计，不再降级为中危），原文证据写明"标书没有对应内容"，便于人工复核与整改。
        boolean evidenceLack = result.evidenceLack();
        issue.setEvidenceLack(evidenceLack);
        if (evidenceLack || StrUtil.isBlank(issue.getEvidenceText())) {
            issue.setEvidenceLack(true);
            issue.setEvidenceText("标书没有对应内容");
        }
        issue.setSuggestion(StrUtil.blankToDefault(result.suggestion(),
                "请按招标要求补充或修正相关内容"));
        issue.setSource(source);
        issue.setAiConfidence(result.confidence());
        issue.setStatus("OPEN");
        return issue;
    }

    /** 报告：结论/得分/摘要由确定性代码汇总（不交给模型，保证可复算）。 */
    private BidReviewReportDO buildReport(BidReviewTaskDO task, List<BidReviewRuleItemDO> rules,
                                          List<BidReviewIssueDO> issues, boolean aiDegraded) {
        // 统计口径：忽略（IGNORED）不计入；不再排除"证据不足"——标书没有对应内容本身就是不满足要求
        long high = issues.stream()
                .filter(i -> !"IGNORED".equals(i.getStatus()))
                .filter(i -> "HIGH".equals(i.getSeverity()))
                .count();
        long medium = issues.stream()
                .filter(i -> !"IGNORED".equals(i.getStatus()))
                .filter(i -> "MEDIUM".equals(i.getSeverity()))
                .count();
        long low = issues.stream()
                .filter(i -> !"IGNORED".equals(i.getStatus()))
                .filter(i -> "LOW".equals(i.getSeverity()))
                .count();

        // 评分项汇总（AI 给出的 score / 规则满分）
        BigDecimal scoreTotal = BigDecimal.ZERO;
        BigDecimal scoreMaxTotal = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (BidReviewRuleItemDO rule : rules) {
            if (!"SCORING".equalsIgnoreCase(rule.getRuleType()) || rule.getScoreMax() == null) {
                continue;
            }
            scoreMaxTotal = scoreMaxTotal.add(rule.getScoreMax());
            BigDecimal got = rule.getScoreMax();
            for (BidReviewIssueDO issue : issues) {
                if (rule.getRuleCode().equals(issue.getRuleCode())) {
                    got = BigDecimal.ZERO;
                    break;
                }
            }
            scoreTotal = scoreTotal.add(got);
            byCategory.merge(rule.getCategory(), got, BigDecimal::add);
        }

        String verdict = high > 0 ? "FAIL" : (medium > 0 ? "RISK" : "PASS");
        StringBuilder md = new StringBuilder();
        md.append("## 审查结论\n\n");
        md.append("- 结论：").append(verdictLabel(verdict)).append('\n');
        md.append("- 问题统计：高危 ").append(high).append(" 条 / 中危 ").append(medium)
                .append(" 条 / 低危 ").append(low).append(" 条\n");
        if (scoreMaxTotal.compareTo(BigDecimal.ZERO) > 0) {
            md.append("- 自评得分（AI 预估）：").append(scoreTotal.setScale(2, RoundingMode.HALF_UP))
                    .append(" / ").append(scoreMaxTotal.setScale(2, RoundingMode.HALF_UP)).append('\n');
        }
        if (aiDegraded) {
            md.append("- ⚠ 本次 AI 未启用或调用失败，语义类规则已降级，结论仅供参考\n");
        }
        if (StrUtil.isBlank(task.getTenderFileUrl())) {
            md.append("- ℹ 本次未上传招标文件，仅按平台规则库自查（未使用本项目招标要求）\n");
        }
        // 【2026-09-20 修复「报告文档内容重复」】问题清单**单独成串**，不再拼进 conclusion。
        //   原因：conclusion 字段语义是"总体结论与建议"（见 DO 注释），而问题清单是独立数据
        //   （bidding_bid_review_issue）。旧实现把清单塞进 conclusion，Word 生成器（BidReviewReportDocServiceImpl）
        //   又在结论之后拼一遍「问题清单（逐条）」→ 同一份清单在文档里出现两次、标题也重复。
        //   现在：conclusion 只留结论；HTML 报告仍在拼装时把清单接回去（对外呈现与旧版一致）。
        StringBuilder issuesMd = new StringBuilder();
        if (!issues.isEmpty()) {
            issuesMd.append("\n## 问题清单\n\n");
            int index = 1;
            for (BidReviewIssueDO issue : issues) {
                issuesMd.append(index++).append(". **[").append(issue.getSeverity()).append("] ")
                        .append(issue.getIssueTitle()).append("**\n");
                if (StrUtil.isNotBlank(issue.getIssueDesc())) {
                    issuesMd.append("   - 说明：").append(issue.getIssueDesc()).append('\n');
                }
                if (StrUtil.isNotBlank(issue.getEvidenceText())) {
                    issuesMd.append("   - 证据（").append(StrUtil.blankToDefault(issue.getHitLocation(), "未定位"))
                            .append("）：").append(issue.getEvidenceText()).append('\n');
                } else {
                    issuesMd.append("   - 证据：标书没有对应内容\n");
                }
                issuesMd.append("   - 建议：").append(issue.getSuggestion()).append('\n');
            }
        }
        // 免责声明单独持有：conclusion（结论+声明）与 HTML 报告（结论+清单+声明）都要以它收尾
        String footer = "\n> 本报告由 AI 生成，需人工复核后使用。\n";

        BidReviewReportDO report = new BidReviewReportDO();
        report.setTaskId(task.getId());
        report.setReportNo("BRR" + task.getId());
        report.setVerdict(verdict);
        report.setScoreTotal(scoreMaxTotal.compareTo(BigDecimal.ZERO) > 0 ? scoreTotal : null);
        report.setScoreJson(toJson(byCategory));
        report.setIssueSummaryJson(toJson(Map.of("high", high, "medium", medium, "low", low)));
        // conclusion 不含问题清单（清单由渲染方按需拼接，避免报告文档里出现两遍清单）
        report.setConclusion(md.toString() + footer);
        report.setHtmlContent("<pre style=\"white-space:pre-wrap;font-family:inherit\">"
                + (md.toString() + issuesMd + footer).replace("<", "&lt;") + "</pre>");
        report.setTemplateCode("DEFAULT");
        report.setAiGenerated(true);
        return report;
    }

    private String verdictLabel(String verdict) {
        return switch (verdict) {
            case "FAIL" -> "不通过（命中高危问题，需整改后重投）";
            case "RISK" -> "存在风险（有中低风险问题，建议整改）";
            default -> "通过（未发现明确问题）";
        };
    }

    private void updateStep(BidReviewTaskDO task, String step, int progress) {
        task.setCurrentStep(step);
        task.setProgress(progress);
        taskMapper.updateById(task);
    }

    private void appendContent(Long taskId, String contentType, String content) {
        BidReviewContentDO contentDO = new BidReviewContentDO();
        contentDO.setBizId(taskId);
        contentDO.setContentType(contentType);
        contentDO.setContent(content);
        contentDO.setSha256(DigestUtil.sha256Hex(StrUtil.nullToEmpty(content)));
        contentDO.setVersion(contentMapper.selectMaxVersion(taskId, contentType) + 1);
        contentMapper.insert(contentDO);
    }

    private void fail(BidReviewTaskDO task, String errorCode, String errorMsg, long start, boolean aiCalled) {
        task.setStatus("FAILED");
        task.setErrorCode(errorCode);
        task.setErrorMsg(errorMsg);
        task.setAiCalled(aiCalled);
        task.setFinishedTime(LocalDateTime.now());
        task.setCostMs(System.currentTimeMillis() - start);
        taskMapper.updateById(task);
    }

    /** 把错误码数值反查成常量名（报告与前端按名字展示更好读）。 */
    private String errorCodeName(ServiceException e) {
        return "1-060-110-XXX(" + e.getCode() + ")";
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
