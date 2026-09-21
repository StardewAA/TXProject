package cn.iocoder.txgy.module.report.controller.admin.center;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterDataRespVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterQueryReqVO;
import cn.iocoder.txgy.module.report.controller.admin.center.vo.ReportCenterReportRespVO;
import cn.iocoder.txgy.module.report.service.reportcenter.ReportCenterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * MES 报表中心 Controller
 *
 * 面向业务人员（车间、品质、生产文员）：只提供「报表清单 → 查询/一键导出」，
 * 不暴露 SQL、数据集等后台概念。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - MES 报表中心")
@RestController
@RequestMapping("/report/center")
@Validated
public class ReportCenterController {

    @Resource
    private ReportCenterService reportCenterService;

    @GetMapping("/list")
    @Operation(summary = "获得可用报表清单", description = "仅返回数据源已配置的报表")
    @PreAuthorize("@ss.hasPermission('report:center:query')")
    public CommonResult<List<ReportCenterReportRespVO>> getReportList() {
        return success(reportCenterService.getReportList());
    }

    @PostMapping("/data")
    @Operation(summary = "查询报表数据", description = "返回中文表头 + 行数据，租户条件由服务端强制注入")
    @PreAuthorize("@ss.hasPermission('report:center:query')")
    public CommonResult<ReportCenterDataRespVO> query(@Valid @RequestBody ReportCenterQueryReqVO reqVO) {
        return success(reportCenterService.query(reqVO));
    }

    @PostMapping("/export-excel")
    @Operation(summary = "一键导出报表 Excel", description = "直接返回 Excel 文件流，业务人员点一下即可下载")
    @PreAuthorize("@ss.hasPermission('report:center:export')")
    public void exportExcel(@Valid @RequestBody ReportCenterQueryReqVO reqVO, HttpServletResponse response) {
        reportCenterService.exportExcel(reqVO, response);
    }

}
