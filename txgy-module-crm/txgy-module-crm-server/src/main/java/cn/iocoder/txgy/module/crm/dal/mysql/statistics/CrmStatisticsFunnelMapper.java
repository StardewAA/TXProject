package cn.iocoder.txgy.module.crm.dal.mysql.statistics;

import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsBusinessInversionRateSummaryByDateRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsBusinessSummaryByDateRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsBusinessSummaryByEndStatusRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsBusinessSummaryByStatusRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsBusinessSummaryRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.statistics.vo.funnel.CrmStatisticsFunnelReqVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CRM 销售漏斗 Mapper
 *
 * @author HUIHUI
 */
@Mapper
public interface CrmStatisticsFunnelMapper {

    Long selectCustomerCountByDate(CrmStatisticsFunnelReqVO reqVO);

    Long selectBusinessCountByDateAndEndStatus(@Param("reqVO") CrmStatisticsFunnelReqVO reqVO, @Param("status") Integer status);

    List<CrmStatisticsBusinessSummaryByEndStatusRespVO> selectBusinessSummaryListGroupByEndStatus(CrmStatisticsFunnelReqVO reqVO);

    List<CrmStatisticsBusinessSummaryByStatusRespVO> selectBusinessSummaryListGroupByStatus(CrmStatisticsFunnelReqVO reqVO);

    List<CrmStatisticsBusinessSummaryByDateRespVO> selectBusinessSummaryGroupByDate(CrmStatisticsFunnelReqVO reqVO);

    List<CrmStatisticsBusinessInversionRateSummaryByDateRespVO> selectBusinessInversionRateSummaryByDate(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 按「商机阶段」汇总（标准化阶段；仅统计未开始 / 进行中的商机）
     */
    List<CrmStatisticsBusinessSummaryRespVO> selectBusinessSummaryGroupByStage(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 按「商机状态组」汇总（未开始 / 进行中 / 已赢单 / 已丢单）
     */
    List<CrmStatisticsBusinessSummaryRespVO> selectBusinessSummaryGroupByState(CrmStatisticsFunnelReqVO reqVO);

    /**
     * 按「丢单原因」汇总（仅统计已丢单的商机，用于复盘）
     */
    List<CrmStatisticsBusinessSummaryRespVO> selectBusinessLostReasonSummary(CrmStatisticsFunnelReqVO reqVO);

}
