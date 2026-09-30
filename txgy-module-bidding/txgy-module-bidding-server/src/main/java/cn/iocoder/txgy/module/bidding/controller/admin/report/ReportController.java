package cn.iocoder.txgy.module.bidding.controller.admin.report;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.report.ReportDO;
import cn.iocoder.txgy.module.bidding.service.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 定标书面报告（招标投标法第47条：确定中标人之日起 15 日内提交书面报告）。
 */
@Tag(name = "管理后台 - 定标书面报告")
@RestController
@RequestMapping("/bidding/report")
@Validated
public class ReportController {

    @Resource
    private ReportService reportService;

    @PostMapping("/submit")
    @PreAuthorize("@ss.hasPermission('bidding:report:submit')")
    @Operation(summary = "提交定标书面报告（记录定标时间锚点：中标结果公告发布时间）")
    @Idempotent
    public CommonResult<Long> submit(@Valid @RequestBody ReportSaveReqVO reqVO) {
        return success(reportService.submit(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:report:query')")
    @Operation(summary = "书面报告详情")
    public CommonResult<ReportRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(reportService.get(id), ReportRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:report:query')")
    @Operation(summary = "书面报告台账分页（含超期未提交标记 reportOverdue）")
    public CommonResult<PageResult<ReportRespVO>> page(@Valid ReportPageReqVO reqVO) {
        PageResult<ReportDO> page = reportService.page(reqVO);
        return success(BeanUtils.toBean(page, ReportRespVO.class));
    }
}
