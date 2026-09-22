package cn.iocoder.txgy.module.report.service.reportcenter;

import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterDataRespVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterOverviewRespVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterQueryReqVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterReportRespVO;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * 报表中心 Service 接口
 *
 * 面向业务人员：只看报表、点导出，不接触 SQL 与数据集配置。
 *
 * @author OPENLAB BS
 */
public interface ReportCenterService {

    /**
     * 获取可用报表清单（仅返回数据源已配置且启用的报表）
     *
     * @return 报表清单
     */
    List<ReportCenterReportRespVO> getReportList();

    /**
     * 获取报表中心概览统计
     *
     * 报表总数与常用报表取自报表清单；
     * 今日查询次数与最近更新时间由执行日志已有的 create_time 聚合得出。
     *
     * @return 概览统计
     */
    ReportCenterOverviewRespVO getOverview();

    /**
     * 查询报表数据（已整理为中文表头 + 行数据）
     *
     * @param reqVO 报表编码与筛选参数
     * @return 报表数据
     */
    ReportCenterDataRespVO query(ReportCenterQueryReqVO reqVO);

    /**
     * 一键导出报表 Excel
     *
     * @param reqVO    报表编码与筛选参数
     * @param response HTTP 响应（直接写出文件流）
     */
    void exportExcel(ReportCenterQueryReqVO reqVO, HttpServletResponse response);

}
