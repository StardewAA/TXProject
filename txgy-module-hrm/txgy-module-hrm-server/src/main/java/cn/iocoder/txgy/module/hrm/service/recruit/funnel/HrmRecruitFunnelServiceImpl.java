package cn.iocoder.txgy.module.hrm.service.recruit.funnel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.funnel.HrmRecruitFunnelRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationLogDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationLogMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitApplicationMapper;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * HRM 招聘漏斗 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class HrmRecruitFunnelServiceImpl implements HrmRecruitFunnelService {

    /** 筛选通过阶段 */
    private static final Set<String> SCREENING_PASSED_STATUSES = Set.of("SCREENING_PASSED");
    /** 进入面试阶段 */
    private static final Set<String> INTERVIEW_STATUSES = Set.of(
            "PENDING_INTERVIEW", "INTERVIEW_SCHEDULED", "INTERVIEWING", "INTERVIEW_EVALUATING", "NEXT_INTERVIEW");
    /** 终面通过阶段（含待发 Offer） */
    private static final Set<String> FINAL_PASSED_STATUSES = Set.of("FINAL_INTERVIEW_PASSED", "PENDING_OFFER");
    /** Offer 已发阶段（含后续状态） */
    private static final Set<String> OFFER_SENT_STATUSES =
            Set.of("OFFER_SENT", "PENDING_ONBOARD", "ONBOARDED");
    /** 已入职阶段 */
    private static final Set<String> ONBOARDED_STATUSES = Set.of("ONBOARDED");

    @Resource
    private HrmRecruitApplicationMapper applicationMapper;
    @Resource
    private HrmRecruitApplicationLogMapper applicationLogMapper;
    @Resource
    private HrmRecruitPostService postService;

    @Override
    public List<HrmRecruitFunnelRespVO> getFunnelList(Integer months) {
        int statMonths = months == null || months <= 0 ? DEFAULT_MONTHS : months;
        LocalDateTime from = LocalDateTime.now().minusMonths(statMonths);

        // 1. 取统计窗口内的应聘记录
        List<HrmRecruitApplicationDO> applications = applicationMapper.selectListByCreateTimeAfter(from);
        if (CollUtil.isEmpty(applications)) {
            return new ArrayList<>();
        }

        // 2. 取状态流转日志，得到每条应聘「到达过哪些阶段」（日志缺失时用当前状态兜底）
        Map<Long, Set<String>> reachedStatusMap = new HashMap<>();
        List<HrmRecruitApplicationLogDO> logs = applicationLogMapper.selectListByApplicationIds(
                convertSet(applications, HrmRecruitApplicationDO::getId));
        for (HrmRecruitApplicationLogDO logDO : logs) {
            reachedStatusMap.computeIfAbsent(logDO.getApplicationId(), key -> new HashSet<>())
                    .add(logDO.getToStatus());
        }
        for (HrmRecruitApplicationDO application : applications) {
            reachedStatusMap.computeIfAbsent(application.getId(), key -> new HashSet<>())
                    .add(application.getStatus());
        }

        // 3. 按职位聚合
        Map<Long, List<HrmRecruitApplicationDO>> applicationMap = applications.stream()
                .filter(item -> item.getPostId() != null)
                .collect(Collectors.groupingBy(HrmRecruitApplicationDO::getPostId));
        List<HrmRecruitFunnelRespVO> result = new ArrayList<>(applicationMap.size());
        for (Map.Entry<Long, List<HrmRecruitApplicationDO>> entry : applicationMap.entrySet()) {
            result.add(buildFunnel(entry.getKey(), entry.getValue(), reachedStatusMap));
        }
        result.sort(Comparator.comparing(HrmRecruitFunnelRespVO::getApplicationCount).reversed());
        return result;
    }

    private HrmRecruitFunnelRespVO buildFunnel(Long postId, List<HrmRecruitApplicationDO> applications,
                                               Map<Long, Set<String>> reachedStatusMap) {
        long screeningPassed = 0, interview = 0, finalPassed = 0, offerSent = 0, onboarded = 0;
        long onboardDaysTotal = 0, onboardCount = 0;
        for (HrmRecruitApplicationDO application : applications) {
            Set<String> reached = reachedStatusMap.getOrDefault(application.getId(), Set.of());
            if (reached.stream().anyMatch(SCREENING_PASSED_STATUSES::contains)) {
                screeningPassed++;
            }
            if (reached.stream().anyMatch(INTERVIEW_STATUSES::contains)) {
                interview++;
            }
            if (reached.stream().anyMatch(FINAL_PASSED_STATUSES::contains)
                    || reached.stream().anyMatch(OFFER_SENT_STATUSES::contains)) {
                finalPassed++;
            }
            if (reached.stream().anyMatch(OFFER_SENT_STATUSES::contains)) {
                offerSent++;
            }
            boolean isOnboarded = reached.stream().anyMatch(ONBOARDED_STATUSES::contains)
                    || application.getEmployeeId() != null || application.getOnboardTime() != null;
            if (isOnboarded) {
                onboarded++;
                if (application.getOnboardTime() != null && application.getCreateTime() != null) {
                    onboardDaysTotal += Duration.between(application.getCreateTime(),
                            application.getOnboardTime()).toDays();
                    onboardCount++;
                }
            }
        }
        HrmRecruitPostDO post = postService.getRecruitPost(postId);
        long total = applications.size();
        return new HrmRecruitFunnelRespVO()
                .setPostId(postId)
                .setPostName(post == null ? StrUtil.format("职位#{}", postId) : post.getPostName())
                .setApplicationCount(total)
                .setScreeningPassedCount(screeningPassed)
                .setInterviewCount(interview)
                .setFinalPassedCount(finalPassed)
                .setOfferSentCount(offerSent)
                .setOnboardedCount(onboarded)
                .setScreeningRate(percent(screeningPassed, total))
                .setInterviewRate(percent(interview, screeningPassed))
                .setOfferRate(percent(offerSent, finalPassed))
                .setOnboardRate(percent(onboarded, total))
                .setAvgOnboardDays(onboardCount == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(onboardDaysTotal).divide(BigDecimal.valueOf(onboardCount), 1, RoundingMode.HALF_UP));
    }

    /**
     * 百分比（1 位小数），分母为 0 时返回 0
     */
    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

}
