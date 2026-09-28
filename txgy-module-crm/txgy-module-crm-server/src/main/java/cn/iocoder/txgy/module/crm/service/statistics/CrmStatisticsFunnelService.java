package cn.iocoder.txgy.module.crm.service.statistics;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.*;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;

import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsBusinessSummaryRespVO;

import java.util.List;

/**
 * CRM 销售漏斗分析 Service
 *
 * @author HUIHUI
 */
public interface CrmStatisticsFunnelService {

    /**
     * 获得销售漏斗数据
     *
     * @param reqVO 请求
     * @return 销售漏斗数据
     */
    CrmStatisticFunnelSummaryRespVO getFunnelSummary(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得商机结束状态统计
     *
     * @param reqVO 请求
     * @return 商机结束状态统计
     */
    List<CrmStatisticsBusinessSummaryByEndStatusRespVO> getBusinessSummaryByEndStatus(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得商机阶段统计
     *
     * @param reqVO 请求
     * @return 商机阶段统计
     */
    List<CrmStatisticsBusinessSummaryByStatusRespVO> getBusinessSummaryByStatus(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得商机阶段统计（标准化后的商机阶段；仅统计未开始 / 进行中的商机）
     *
     * @param reqVO 请求
     * @return 商机阶段统计
     */
    List<CrmStatisticsBusinessSummaryRespVO> getBusinessSummaryByStage(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得商机状态组统计（未开始 / 进行中 / 已赢单 / 已丢单）
     *
     * @param reqVO 请求
     * @return 商机状态组统计
     */
    List<CrmStatisticsBusinessSummaryRespVO> getBusinessSummaryByState(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得丢单原因统计（仅统计已丢单的商机，用于丢单复盘）
     *
     * @param reqVO 请求
     * @return 丢单原因统计
     */
    List<CrmStatisticsBusinessSummaryRespVO> getBusinessLostReasonSummary(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获取新增商机分析(按日期)
     *
     * @param reqVO 请求
     * @return 新增商机分析
     */
    List<CrmStatisticsBusinessSummaryByDateRespVO> getBusinessSummaryByDate(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得商机转化率分析(按日期)
     *
     * @param reqVO 请求
     * @return 商机转化率分析
     */
    List<CrmStatisticsBusinessInversionRateSummaryByDateRespVO> getBusinessInversionRateSummaryByDate(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 获得商机分页(按日期)
     *
     * @param pageVO 请求
     * @return 商机分页
     */
    PageResult<CrmBusinessDO> getBusinessPageByDate(CrmStatisticsFunnelReqVO pageVO);

}
