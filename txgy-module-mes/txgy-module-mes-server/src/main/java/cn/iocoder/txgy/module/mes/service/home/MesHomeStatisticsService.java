package cn.iocoder.txgy.module.mes.service.home;

import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeAndonAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeCheckMaintenRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeDefectAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeDeliveryAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeMachineryStatusRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeProductionTrendRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeQcPassRateRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeRepairAnalysisRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeSummaryRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.home.vo.MesHomeWorkOrderStatusRespVO;

import java.util.List;

/**
 * MES 首页统计 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesHomeStatisticsService {

    /**
     * 获得首页汇总统计
     *
     * @return 汇总统计数据
     */
    MesHomeSummaryRespVO getHomeSummary();

    /**
     * 获得工单状态分布
     *
     * @return 各状态的工单数量列表
     */
    List<MesHomeWorkOrderStatusRespVO> getWorkOrderStatusDistribution();

    /**
     * 获得生产趋势（近 N 天）
     *
     * @param days 天数
     * @return 每天的产量数据
     */
    List<MesHomeProductionTrendRespVO> getProductionTrend(Integer days);

    /**
     * 获得设备状态分布
     *
     * @return 各状态的设备台数列表
     */
    List<MesHomeMachineryStatusRespVO> getMachineryStatusDistribution();

    /**
     * 获得质检合格率（近 N 天，按质检类型）
     *
     * @param days 天数
     * @return 各质检类型的合格率数据
     */
    List<MesHomeQcPassRateRespVO> getQcPassRate(Integer days);

    /**
     * 获得安灯呼叫分析（近 N 天）
     *
     * @param days 天数
     * @return 安灯呼叫分析数据
     */
    MesHomeAndonAnalysisRespVO getAndonAnalysis(Integer days);

    /**
     * 获得维修工单分析（近 N 天，按报修日期）
     *
     * @param days 天数
     * @return 维修工单分析数据
     */
    MesHomeRepairAnalysisRespVO getRepairAnalysis(Integer days);

    /**
     * 获得点检保养执行情况（近 N 天）
     *
     * @param days 天数
     * @return 点检保养执行数据
     */
    MesHomeCheckMaintenRespVO getCheckMainten(Integer days);

    /**
     * 获得不良缺陷分析（近 N 天）
     *
     * @param days 天数
     * @return 不良缺陷分析数据
     */
    MesHomeDefectAnalysisRespVO getDefectAnalysis(Integer days);

    /**
     * 获得生产交付达成分析（近 N 天）
     *
     * @param days 天数
     * @return 生产交付达成分析数据
     */
    MesHomeDeliveryAnalysisRespVO getDeliveryAnalysis(Integer days);

}
