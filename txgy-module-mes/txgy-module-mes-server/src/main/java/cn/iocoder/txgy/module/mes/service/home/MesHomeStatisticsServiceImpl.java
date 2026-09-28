package cn.iocoder.txgy.module.mes.service.home;

import cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeAndonAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeAndonLevelStatRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeAndonReasonStatRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeCheckMaintenRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeDefectAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeDeliveryAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeFeedbackUserRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeMachineryStatusRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeProductionTrendRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeQcPassRateRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeRepairAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeRepairStatusStatRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeSummaryRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeWorkOrderStatusRespVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.project.MesProProjectMilestoneDO;
import cn.iocoder.txgy.module.mes.dal.mysql.home.MesHomeStatisticsMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.project.MesProProjectMilestoneMapper;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.enums.dv.MesDvMachineryStatusEnum;
import cn.iocoder.txgy.module.mes.enums.dv.MesDvRepairStatusEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProAndonLevelEnum;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import cn.iocoder.txgy.module.mes.enums.qc.MesQcTypeEnum;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MES 首页统计 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
public class MesHomeStatisticsServiceImpl implements MesHomeStatisticsService {

    /**
     * 缺陷 TOP 统计条数
     */
    private static final int DEFECT_TOP_LIMIT = 5;

    /**
     * 报工人产出 TOP 统计条数
     */
    private static final int FEEDBACK_USER_TOP_LIMIT = 5;

    @Resource
    private MesHomeStatisticsMapper homeStatisticsMapper;

    @Resource
    private MesProProjectMilestoneMapper projectMilestoneMapper;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public MesHomeSummaryRespVO getHomeSummary() {
        MesHomeSummaryRespVO summary = new MesHomeSummaryRespVO();

        // 1. 工单统计
        List<Map<String, Object>> workOrderStats = homeStatisticsMapper.selectWorkOrderCountGroupByStatus();
        long activeCount = 0L;
        long prepareCount = 0L;
        long finishedCount = 0L;
        for (Map<String, Object> stat : workOrderStats) {
            int status = ((Number) stat.get("status")).intValue();
            long count = ((Number) stat.get("count")).longValue();
            if (status == MesProWorkOrderStatusEnum.CONFIRMED.getStatus()) {
                activeCount = count;
            } else if (status == MesProWorkOrderStatusEnum.PREPARE.getStatus()) {
                prepareCount = count;
            } else if (status == MesProWorkOrderStatusEnum.FINISHED.getStatus()) {
                finishedCount = count;
            }
        }
        summary.setWorkOrderActiveCount(activeCount);
        summary.setWorkOrderPrepareCount(prepareCount);
        summary.setWorkOrderFinishedCount(finishedCount);

        // 2. 今日产量
        LocalDateTime today = LocalDateTimeUtils.getToday();
        LocalDateTime yesterday = LocalDateTimeUtils.getYesterday();
        Map<String, BigDecimal> todayFeedback = homeStatisticsMapper.selectFeedbackSummary(today, null);
        summary.setTodayOutput(getDecimal(todayFeedback, "feedbackQuantity"));
        summary.setTodayQualifiedQuantity(getDecimal(todayFeedback, "qualifiedQuantity"));
        summary.setTodayUnqualifiedQuantity(getDecimal(todayFeedback, "unqualifiedQuantity"));

        // 3. 昨日产量
        Map<String, BigDecimal> yesterdayFeedback = homeStatisticsMapper.selectFeedbackSummary(yesterday, today);
        summary.setYesterdayOutput(getDecimal(yesterdayFeedback, "feedbackQuantity"));

        // 4. 设备统计
        List<Map<String, Object>> machineryStats = homeStatisticsMapper.selectMachineryCountGroupByStatus();
        long total = 0L;
        long producing = 0L;
        long stop = 0L;
        long maintenance = 0L;
        for (Map<String, Object> stat : machineryStats) {
            int status = ((Number) stat.get("status")).intValue();
            long count = ((Number) stat.get("count")).longValue();
            total += count;
            if (status == MesDvMachineryStatusEnum.PRODUCING.getStatus()) {
                producing = count;
            } else if (status == MesDvMachineryStatusEnum.STOP.getStatus()) {
                stop = count;
            } else if (status == MesDvMachineryStatusEnum.MAINTENANCE.getStatus()) {
                maintenance = count;
            }
        }
        summary.setMachineryTotal(total);
        summary.setMachineryProducing(producing);
        summary.setMachineryStop(stop);
        summary.setMachineryMaintenance(maintenance);

        // 5. 异常/待办
        summary.setAndonActiveCount(homeStatisticsMapper.selectAndonActiveCount());
        summary.setRepairActiveCount(homeStatisticsMapper.selectRepairActiveCount());

        // 里程碑预警：未完成且 3 天内到期（含已超期）的数量
        summary.setMilestoneAlertCount(projectMilestoneMapper.selectCount(
                new LambdaQueryWrapperX<MesProProjectMilestoneDO>()
                        .isNull(MesProProjectMilestoneDO::getActualDate)
                        .isNotNull(MesProProjectMilestoneDO::getPlanDate)
                        .le(MesProProjectMilestoneDO::getPlanDate, LocalDateTime.now().plusDays(3))));

        return summary;
    }

    @Override
    public List<MesHomeWorkOrderStatusRespVO> getWorkOrderStatusDistribution() {
        List<Map<String, Object>> stats = homeStatisticsMapper.selectWorkOrderCountGroupByStatus();
        List<MesHomeWorkOrderStatusRespVO> result = new ArrayList<>();
        for (MesProWorkOrderStatusEnum statusEnum : MesProWorkOrderStatusEnum.values()) {
            long count = 0L;
            for (Map<String, Object> stat : stats) {
                if (((Number) stat.get("status")).intValue() == statusEnum.getStatus()) {
                    count = ((Number) stat.get("count")).longValue();
                    break;
                }
            }
            result.add(new MesHomeWorkOrderStatusRespVO(statusEnum.getStatus(), statusEnum.getName(), count));
        }
        return result;
    }

    @Override
    public List<MesHomeProductionTrendRespVO> getProductionTrend(Integer days) {
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDate startDate = endDate.minusDays(days);
        LocalDateTime beginTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atStartOfDay();

        // 查询数据库
        List<Map<String, Object>> dbData = homeStatisticsMapper.selectDailyFeedbackTrend(beginTime, endTime);
        Map<String, Map<String, Object>> dateMap = new LinkedHashMap<>();
        for (Map<String, Object> row : dbData) {
            dateMap.put((String) row.get("date"), row);
        }

        // 补全所有日期（无数据的日期填 0）
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<MesHomeProductionTrendRespVO> result = new ArrayList<>();
        for (LocalDate d = startDate; d.isBefore(endDate); d = d.plusDays(1)) {
            String dateStr = d.format(fmt);
            Map<String, Object> row = dateMap.get(dateStr);
            MesHomeProductionTrendRespVO vo = new MesHomeProductionTrendRespVO();
            vo.setDate(dateStr);
            if (row != null) {
                vo.setQuantity(getDecimalFromObj(row.get("quantity")));
                vo.setQualifiedQuantity(getDecimalFromObj(row.get("qualifiedQuantity")));
                vo.setUnqualifiedQuantity(getDecimalFromObj(row.get("unqualifiedQuantity")));
            } else {
                vo.setQuantity(BigDecimal.ZERO);
                vo.setQualifiedQuantity(BigDecimal.ZERO);
                vo.setUnqualifiedQuantity(BigDecimal.ZERO);
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<MesHomeMachineryStatusRespVO> getMachineryStatusDistribution() {
        List<Map<String, Object>> stats = homeStatisticsMapper.selectMachineryCountGroupByStatus();
        List<MesHomeMachineryStatusRespVO> result = new ArrayList<>();
        for (MesDvMachineryStatusEnum statusEnum : MesDvMachineryStatusEnum.values()) {
            long count = 0L;
            for (Map<String, Object> stat : stats) {
                if (((Number) stat.get("status")).intValue() == statusEnum.getStatus()) {
                    count = ((Number) stat.get("count")).longValue();
                    break;
                }
            }
            result.add(new MesHomeMachineryStatusRespVO(statusEnum.getStatus(), statusEnum.getName(), count));
        }
        return result;
    }

    @Override
    public List<MesHomeQcPassRateRespVO> getQcPassRate(Integer days) {
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();

        // 1. 查询数据库，按质检类型归集
        List<Map<String, Object>> dbData = homeStatisticsMapper.selectQcPassRateGroupByType(beginTime, endTime);
        Map<String, Map<String, Object>> typeMap = new HashMap<>();
        for (Map<String, Object> row : dbData) {
            typeMap.put((String) row.get("qcType"), row);
        }

        // 2. 按枚举输出全部类型（无数据补 0）
        List<MesHomeQcPassRateRespVO> result = new ArrayList<>();
        for (MesQcTypeEnum typeEnum : MesQcTypeEnum.values()) {
            Map<String, Object> row = typeMap.get(typeEnum.name());
            long docCount = 0L;
            long passDocCount = 0L;
            BigDecimal checkQuantity = BigDecimal.ZERO;
            BigDecimal qualifiedQuantity = BigDecimal.ZERO;
            if (row != null) {
                docCount = ((Number) row.get("docCount")).longValue();
                passDocCount = ((Number) row.get("passDocCount")).longValue();
                checkQuantity = getDecimalFromObj(row.get("checkQuantity"));
                qualifiedQuantity = getDecimalFromObj(row.get("qualifiedQuantity"));
            }
            result.add(new MesHomeQcPassRateRespVO(typeEnum.getType(), typeEnum.getName(), docCount,
                    checkQuantity, qualifiedQuantity,
                    calcRate(qualifiedQuantity, checkQuantity),
                    calcRate(BigDecimal.valueOf(passDocCount), BigDecimal.valueOf(docCount))));
        }
        return result;
    }

    @Override
    public MesHomeAndonAnalysisRespVO getAndonAnalysis(Integer days) {
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();

        // 1. 汇总统计（总数 / 已处置 / 平均处置时长）
        Map<String, Object> summary = homeStatisticsMapper.selectAndonSummary(beginTime, endTime);
        long totalCount = summary == null || summary.get("totalCount") == null ? 0L
                : ((Number) summary.get("totalCount")).longValue();
        long handledCount = summary == null || summary.get("handledCount") == null ? 0L
                : ((Number) summary.get("handledCount")).longValue();
        BigDecimal avgHandleMinutes = summary == null ? BigDecimal.ZERO
                : getDecimalFromObj(summary.get("avgHandleMinutes")).setScale(1, RoundingMode.HALF_UP);
        long activeCount = totalCount - handledCount;
        BigDecimal handleRate = calcRate(BigDecimal.valueOf(handledCount), BigDecimal.valueOf(totalCount));

        // 2. 按级别分布（枚举补全）
        List<Map<String, Object>> levelData = homeStatisticsMapper.selectAndonCountGroupByLevel(beginTime, endTime);
        List<MesHomeAndonLevelStatRespVO> levelStats = new ArrayList<>();
        for (MesProAndonLevelEnum levelEnum : MesProAndonLevelEnum.values()) {
            long count = 0L;
            for (Map<String, Object> row : levelData) {
                if (((Number) row.get("level")).intValue() == levelEnum.getLevel()) {
                    count = ((Number) row.get("count")).longValue();
                    break;
                }
            }
            levelStats.add(new MesHomeAndonLevelStatRespVO(levelEnum.getLevel(), levelEnum.getName(), count));
        }

        // 3. 呼叫原因 TOP5
        List<MesHomeAndonReasonStatRespVO> topReasons = new ArrayList<>();
        for (Map<String, Object> row : homeStatisticsMapper.selectAndonTopReasons(beginTime, endTime, 5)) {
            topReasons.add(new MesHomeAndonReasonStatRespVO((String) row.get("reason"),
                    ((Number) row.get("count")).longValue()));
        }

        return new MesHomeAndonAnalysisRespVO(totalCount, activeCount, handledCount, handleRate,
                avgHandleMinutes, levelStats, topReasons);
    }

    @Override
    public MesHomeRepairAnalysisRespVO getRepairAnalysis(Integer days) {
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();

        // 1. 汇总统计
        Map<String, Object> summary = homeStatisticsMapper.selectRepairSummary(beginTime, endTime);
        long totalCount = summary == null || summary.get("totalCount") == null ? 0L
                : ((Number) summary.get("totalCount")).longValue();
        long finishedCount = summary == null || summary.get("finishedCount") == null ? 0L
                : ((Number) summary.get("finishedCount")).longValue();
        long passCount = summary == null || summary.get("passCount") == null ? 0L
                : ((Number) summary.get("passCount")).longValue();
        long failCount = summary == null || summary.get("failCount") == null ? 0L
                : ((Number) summary.get("failCount")).longValue();
        BigDecimal avgRepairHours = summary == null ? BigDecimal.ZERO
                : getDecimalFromObj(summary.get("avgRepairHours")).setScale(1, RoundingMode.HALF_UP);
        long pendingCount = totalCount - finishedCount;

        // 2. 按状态分布（枚举补全）
        List<Map<String, Object>> statusData = homeStatisticsMapper.selectRepairCountGroupByStatus(beginTime, endTime);
        List<MesHomeRepairStatusStatRespVO> statusStats = new ArrayList<>();
        for (MesDvRepairStatusEnum statusEnum : MesDvRepairStatusEnum.values()) {
            long count = 0L;
            for (Map<String, Object> row : statusData) {
                if (((Number) row.get("status")).intValue() == statusEnum.getStatus()) {
                    count = ((Number) row.get("count")).longValue();
                    break;
                }
            }
            statusStats.add(new MesHomeRepairStatusStatRespVO(statusEnum.getStatus(), statusEnum.getName(), count));
        }

        return new MesHomeRepairAnalysisRespVO(totalCount, pendingCount, finishedCount, avgRepairHours,
                passCount, failCount, statusStats);
    }

    @Override
    public MesHomeCheckMaintenRespVO getCheckMainten(Integer days) {
        // 1. 点检保养方案（全量，方案为长期有效数据）
        Map<String, Object> planSummary = homeStatisticsMapper.selectCheckPlanSummary();

        // 2. 点检记录 + 保养记录（近 N 天）
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();
        Map<String, Object> checkSummary = homeStatisticsMapper.selectCheckRecordSummary(beginTime, endTime);
        Map<String, Object> maintenSummary = homeStatisticsMapper.selectMaintenRecordSummary(beginTime, endTime);

        // 3. 组装 + 计算比率
        long checkTotal = getLong(checkSummary, "totalCount");
        long checkFinished = getLong(checkSummary, "finishedCount");
        long maintenTotal = getLong(maintenSummary, "totalCount");
        long maintenSubmitted = getLong(maintenSummary, "submittedCount");
        return new MesHomeCheckMaintenRespVO(
                getLong(planSummary, "totalCount"), getLong(planSummary, "enabledCount"),
                checkTotal, checkFinished,
                calcRate(BigDecimal.valueOf(checkFinished), BigDecimal.valueOf(checkTotal)),
                maintenTotal, maintenSubmitted,
                calcRate(BigDecimal.valueOf(maintenSubmitted), BigDecimal.valueOf(maintenTotal)));
    }

    @Override
    public MesHomeDefectAnalysisRespVO getDefectAnalysis(Integer days) {
        // 1. 时间范围
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();

        // 2. 缺陷数量汇总（按等级）
        Map<String, Object> summary = homeStatisticsMapper.selectDefectSummary(beginTime, endTime);

        // 3. 缺陷 TOP 5（按缺陷描述聚合数量）
        List<MesHomeDefectAnalysisRespVO.DefectItem> topList = new ArrayList<>();
        for (Map<String, Object> item : homeStatisticsMapper.selectDefectTopList(beginTime, endTime, DEFECT_TOP_LIMIT)) {
            topList.add(new MesHomeDefectAnalysisRespVO.DefectItem(
                    (String) item.get("name"), getLong(item, "quantity")));
        }

        // 4. 组装
        return new MesHomeDefectAnalysisRespVO(
                getLong(summary, "recordCount"), getLong(summary, "totalQuantity"),
                getLong(summary, "criticalQuantity"), getLong(summary, "majorQuantity"),
                getLong(summary, "minorQuantity"), topList);
    }

    @Override
    public MesHomeDeliveryAnalysisRespVO getDeliveryAnalysis(Integer days) {
        // 1. 时间范围
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();

        // 2. 近 N 天完成的工单交付情况 + 在制超期数
        Map<String, Object> summary = homeStatisticsMapper.selectDeliverySummary(beginTime, endTime);
        Long overdueOpenCount = homeStatisticsMapper.selectOverdueOpenCount();

        // 3. 组装 + 计算按期完成率
        long total = getLong(summary, "totalCount");
        long onTime = getLong(summary, "onTimeCount");
        Object avgOverdueDays = summary == null ? null : summary.get("avgOverdueDays");
        return new MesHomeDeliveryAnalysisRespVO(
                total, onTime, getLong(summary, "overdueCount"),
                calcRate(BigDecimal.valueOf(onTime), BigDecimal.valueOf(total)),
                overdueOpenCount == null ? 0L : overdueOpenCount,
                avgOverdueDays == null ? BigDecimal.ZERO
                        : new BigDecimal(avgOverdueDays.toString()).setScale(1, RoundingMode.HALF_UP));
    }

    @Override
    public MesHomeFeedbackUserRespVO getFeedbackUserAnalysis(Integer days) {
        // 1. 时间范围
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDateTime endTime = endDate.atStartOfDay();
        LocalDateTime beginTime = endDate.minusDays(days).atStartOfDay();

        // 2. 报工汇总 + 报工人产出 TOP N
        Map<String, Object> summary = homeStatisticsMapper.selectFeedbackUserSummary(beginTime, endTime);
        List<Map<String, Object>> topRows = homeStatisticsMapper.selectFeedbackUserTopList(beginTime, endTime,
                FEEDBACK_USER_TOP_LIMIT);

        // 3. 批量获取报工人昵称
        Set<Long> userIds = topRows.stream()
                .map(row -> ((Number) row.get("feedbackUserId")).longValue())
                .collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : adminUserApi.getUserMap(userIds);

        // 4. 组装 TOP 列表
        List<MesHomeFeedbackUserRespVO.UserItem> topList = new ArrayList<>();
        for (Map<String, Object> row : topRows) {
            long userId = ((Number) row.get("feedbackUserId")).longValue();
            AdminUserRespDTO user = userMap.get(userId);
            topList.add(new MesHomeFeedbackUserRespVO.UserItem(
                    userId, user == null ? String.valueOf(userId) : user.getNickname(),
                    getLong(row, "recordCount"), toBigDecimal(row.get("qualifiedQuantity"))));
        }

        // 5. 组装汇总 + 计算合格率
        BigDecimal totalQuantity = toBigDecimal(summary == null ? null : summary.get("totalQuantity"));
        BigDecimal qualifiedQuantity = toBigDecimal(summary == null ? null : summary.get("qualifiedQuantity"));
        return new MesHomeFeedbackUserRespVO(getLong(summary, "userCount"), totalQuantity, qualifiedQuantity,
                calcRate(qualifiedQuantity, totalQuantity), topList);
    }

    // ========== 工具方法 ==========

    /**
     * 从查询结果中提取 long 值，为空时返回 0
     */
    private long getLong(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) {
            return 0L;
        }
        return ((Number) map.get(key)).longValue();
    }

    /**
     * 从查询结果中提取 BigDecimal 值，为空时返回 0
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    /**
     * 计算百分比（保留 1 位小数）；分母为 0 时返回 0
     */
    private BigDecimal calcRate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal getDecimal(Map<String, BigDecimal> map, String key) {
        if (map == null || map.get(key) == null) {
            return BigDecimal.ZERO;
        }
        return map.get(key);
    }

    private BigDecimal getDecimalFromObj(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO;
        }
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        return new BigDecimal(obj.toString());
    }

}
