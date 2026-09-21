package cn.iocoder.txgy.module.erp.controller.admin.stock;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror.ErpStockReconciliationRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.stock.vo.mirror.ErpStockUnmappedRespVO;
import cn.iocoder.txgy.module.erp.service.stock.ErpStockMirrorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * ERP 实物库存镜像与对账 Controller
 *
 * <p>WMS 是实物库存唯一权威。本 Controller 提供三件事：</p>
 * <ol>
 *     <li>手动触发同步（把 WMS 库存拉成 ERP 实物账镜像）</li>
 *     <li>库存对账（业务账 vs 实物账，含差额）</li>
 *     <li>映射缺失诊断（为什么同步不出来）</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - ERP 库存实物账镜像与对账")
@RestController
@RequestMapping("/erp/stock-mirror")
@Validated
public class ErpStockMirrorController {

    @Resource
    private ErpStockMirrorService stockMirrorService;

    @PutMapping("/sync")
    @Operation(summary = "手动触发实物库存同步", description = "拉取 WMS 实物库存，写入 ERP 实物账镜像。旁路操作，不影响 ERP 出入库主流程")
    @PreAuthorize("@ss.hasPermission('erp:stock:query')")
    public CommonResult<Integer> syncMirror() {
        return success(stockMirrorService.syncMirror());
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "获得库存对账", description = "业务账（ERP 单据推演）与实物账（WMS 同步）逐项比对，差额即待查原因")
    @PreAuthorize("@ss.hasPermission('erp:stock:query')")
    public CommonResult<List<ErpStockReconciliationRespVO>> getReconciliation() {
        return success(stockMirrorService.getReconciliation());
    }

    @GetMapping("/unmapped")
    @Operation(summary = "获得映射缺失诊断", description = "列出未映射到 WMS 的 ERP 仓库编码与产品编码")
    @PreAuthorize("@ss.hasPermission('erp:stock:query')")
    public CommonResult<ErpStockUnmappedRespVO> getUnmappedDiagnostics() {
        return success(stockMirrorService.getUnmappedDiagnostics());
    }

}
