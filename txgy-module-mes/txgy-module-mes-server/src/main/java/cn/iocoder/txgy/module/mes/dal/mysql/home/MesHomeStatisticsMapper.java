package cn.iocoder.txgy.module.mes.dal.mysql.home;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MES 首页统计 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesHomeStatisticsMapper {

    /**
     * 按状态统计工单数量
     *
     * @return [{ "status": 0, "count": 5 }, ...]
     */
    List<Map<String, Object>> selectWorkOrderCountGroupByStatus();

    /**
     * 统计报工产量合计
     *
     * @param beginTime >= 开始时间
     * @param endTime   < 结束时间
     * @return [feedbackQuantity, qualifiedQuantity, unqualifiedQuantity]
     */
    Map<String, BigDecimal> selectFeedbackSummary(@Param("beginTime") LocalDateTime beginTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 按设备状态统计设备台数
     *
     * @return [{ "status": 1, "count": 3 }, ...]
     */
    List<Map<String, Object>> selectMachineryCountGroupByStatus();

    /**
     * 统计未处置安灯记录数
     *
     * @return 数量
     */
    Long selectAndonActiveCount();

    /**
     * 统计未完成维修工单数（草稿 + 维修中 + 待验收）
     *
     * @return 数量
     */
    Long selectRepairActiveCount();

    /**
     * 按天聚合报工产量（近 N 天）
     *
     * @param beginTime >= 开始时间
     * @param endTime   < 结束时间
     * @return [{ "date": "2026-04-05", "quantity": 1234, "qualifiedQuantity": 1200, "unqualifiedQuantity": 34 }, ...]
     */
    List<Map<String, Object>> selectDailyFeedbackTrend(@Param("beginTime") LocalDateTime beginTime,
                                                       @Param("endTime") LocalDateTime endTime);

    /**
     * 按质检类型统计合格率（近 N 天，仅已完成检验单）
     *
     * @param beginTime >= 检测日期
     * @param endTime   < 检测日期
     * @return [{ "qcType": "IQC", "docCount": 10, "checkQuantity": 1000, "qualifiedQuantity": 950, "passDocCount": 9 }, ...]
     */
    List<Map<String, Object>> selectQcPassRateGroupByType(@Param("beginTime") LocalDateTime beginTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 统计安灯呼叫汇总（近 N 天）
     *
     * @param beginTime >= 呼叫时间
     * @param endTime   < 呼叫时间
     * @return { "totalCount": 36, "handledCount": 31, "avgHandleMinutes": 42.5 }
     */
    Map<String, Object> selectAndonSummary(@Param("beginTime") LocalDateTime beginTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按级别统计安灯呼叫数量（近 N 天）
     *
     * @return [{ "level": 1, "count": 10 }, ...]
     */
    List<Map<String, Object>> selectAndonCountGroupByLevel(@Param("beginTime") LocalDateTime beginTime,
                                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 统计安灯呼叫原因 TOP N（近 N 天）
     *
     * @param limit 取前 N 条
     * @return [{ "reason": "设备故障", "count": 8 }, ...]
     */
    List<Map<String, Object>> selectAndonTopReasons(@Param("beginTime") LocalDateTime beginTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("limit") Integer limit);

    /**
     * 统计维修工单汇总（近 N 天，按报修日期）
     *
     * @param beginTime >= 报修日期
     * @param endTime   < 报修日期
     * @return { "totalCount": 20, "finishedCount": 13, "passCount": 12, "failCount": 1, "avgRepairHours": 6.5 }
     */
    Map<String, Object> selectRepairSummary(@Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 按状态统计维修工单数量（近 N 天，按报修日期）
     *
     * @return [{ "status": 1, "count": 6 }, ...]
     */
    List<Map<String, Object>> selectRepairCountGroupByStatus(@Param("beginTime") LocalDateTime beginTime,
                                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 统计点检保养方案（全量）
     *
     * @return { "totalCount": 12, "enabledCount": 9 }
     */
    Map<String, Object> selectCheckPlanSummary();

    /**
     * 统计点检记录（近 N 天，按点检时间）
     *
     * @return { "totalCount": 46, "finishedCount": 40 }
     */
    Map<String, Object> selectCheckRecordSummary(@Param("beginTime") LocalDateTime beginTime,
                                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 统计设备保养记录（近 N 天，按保养时间）
     *
     * @return { "totalCount": 18, "submittedCount": 15 }
     */
    Map<String, Object> selectMaintenRecordSummary(@Param("beginTime") LocalDateTime beginTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 统计缺陷数量汇总（近 N 天，按等级）
     *
     * @return { "recordCount": 23, "totalQuantity": 156, "criticalQuantity": 12, "majorQuantity": 58, "minorQuantity": 86 }
     */
    Map<String, Object> selectDefectSummary(@Param("beginTime") LocalDateTime beginTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计缺陷 TOP N（近 N 天，按缺陷描述聚合数量）
     *
     * @return [ { "name": "焊缝气孔", "quantity": 42 } ]
     */
    List<Map<String, Object>> selectDefectTopList(@Param("beginTime") LocalDateTime beginTime,
                                                  @Param("endTime") LocalDateTime endTime,
                                                  @Param("limit") Integer limit);

}
