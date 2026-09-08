package cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckAiRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckReviewReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesPollutionCheckLogDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;
import cn.iocoder.txgy.module.mes.service.set.pollutioncheck.MesSetPollutionCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MES 安全环保检测-污染判定记录")
@RestController
@RequestMapping("/mes/safety-env/pollution-check")
@Validated
public class MesSetPollutionCheckController {

    @Resource
    private MesSetPollutionCheckService pollutionCheckService;

    @PostMapping("/create")
    @Operation(summary = "创建污染判定记录(AI 初筛自动回填，落为待复核)")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:create')")
    public CommonResult<Long> createPollutionCheck(@Valid @RequestBody MesSetPollutionCheckSaveReqVO createReqVO) {
        return success(pollutionCheckService.createPollutionCheck(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新污染判定记录(仅待复核)")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:update')")
    public CommonResult<Boolean> updatePollutionCheck(@Valid @RequestBody MesSetPollutionCheckSaveReqVO updateReqVO) {
        pollutionCheckService.updatePollutionCheck(updateReqVO);
        return success(true);
    }

    @PostMapping("/review")
    @Operation(summary = "人工复核(终态收口：无污染/有污染)")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:review')")
    public CommonResult<Boolean> reviewPollutionCheck(@Valid @RequestBody MesSetPollutionCheckReviewReqVO reviewReqVO) {
        pollutionCheckService.reviewPollutionCheck(reviewReqVO);
        return success(true);
    }

    @PostMapping("/prescreen")
    @Operation(summary = "AI 初筛预览(不落库)")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<MesSetPollutionCheckAiRespVO> prescreenPollutionCheck(
            @Valid @RequestBody MesSetPollutionCheckSaveReqVO prescreenReqVO) {
        return success(pollutionCheckService.prescreenPollutionCheck(prescreenReqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除污染判定记录(仅待复核)")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:delete')")
    public CommonResult<Boolean> deletePollutionCheck(@RequestParam("id") Long id) {
        pollutionCheckService.deletePollutionCheck(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得污染判定记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<MesSetPollutionCheckRespVO> getPollutionCheck(@RequestParam("id") Long id) {
        MesSetPollutionCheckDO pollutionCheck = pollutionCheckService.getPollutionCheck(id);
        return success(BeanUtils.toBean(pollutionCheck, MesSetPollutionCheckRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得污染判定记录分页")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<PageResult<MesSetPollutionCheckRespVO>> getPollutionCheckPage(
            @Valid MesSetPollutionCheckPageReqVO pageReqVO) {
        PageResult<MesSetPollutionCheckDO> pageResult = pollutionCheckService.getPollutionCheckPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MesSetPollutionCheckRespVO.class));
    }

    @GetMapping("/history")
    @Operation(summary = "获得污染判定履历(创建→改单→复核，AI/人工快照只增不改)")
    @Parameter(name = "checkId", description = "判定记录编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<List<MesPollutionCheckLogDO>> getPollutionCheckHistory(@RequestParam("checkId") Long checkId) {
        return success(pollutionCheckService.getPollutionCheckLogList(checkId));
    }

}
