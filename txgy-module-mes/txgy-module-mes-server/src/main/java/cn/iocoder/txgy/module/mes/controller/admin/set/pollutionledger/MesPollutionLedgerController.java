package cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutionledger.vo.MesPollutionLedgerStatusReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutionledger.MesPollutionLedgerLogDO;
import cn.iocoder.txgy.module.mes.service.set.pollutionledger.MesPollutionLedgerService;
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

@Tag(name = "管理后台 - MES 安全环保检测-污染/危废暂存台账")
@RestController
@RequestMapping("/mes/safety-env/pollution-ledger")
@Validated
public class MesPollutionLedgerController {

    @Resource
    private MesPollutionLedgerService pollutionLedgerService;

    @PutMapping("/status")
    @Operation(summary = "处置流转(终态清批次污染戳)")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:review')")
    public CommonResult<Boolean> updateLedgerStatus(@Valid @RequestBody MesPollutionLedgerStatusReqVO reqVO) {
        pollutionLedgerService.updateLedgerStatus(reqVO);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得污染/危废暂存台账")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<MesPollutionLedgerRespVO> getLedger(@RequestParam("id") Long id) {
        MesPollutionLedgerDO ledger = pollutionLedgerService.getLedger(id);
        return success(BeanUtils.toBean(ledger, MesPollutionLedgerRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得污染/危废暂存台账分页")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<PageResult<MesPollutionLedgerRespVO>> getLedgerPage(@Valid MesPollutionLedgerPageReqVO pageReqVO) {
        PageResult<MesPollutionLedgerDO> pageResult = pollutionLedgerService.getLedgerPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, MesPollutionLedgerRespVO.class));
    }

    @GetMapping("/history")
    @Operation(summary = "获得台账流转历史(登记→处置→闭环，只增不改)")
    @Parameter(name = "ledgerId", description = "台账编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('mes:set-pollution-check:query')")
    public CommonResult<List<MesPollutionLedgerLogDO>> getLedgerHistory(@RequestParam("ledgerId") Long ledgerId) {
        return success(pollutionLedgerService.getLedgerLogList(ledgerId));
    }

}
