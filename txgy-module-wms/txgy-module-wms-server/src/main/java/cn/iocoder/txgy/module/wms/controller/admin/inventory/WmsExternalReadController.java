package cn.iocoder.txgy.module.wms.controller.admin.inventory;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.api.stock.dto.*;
import cn.iocoder.txgy.module.wms.service.inventory.WmsStockQueryService;
import cn.iocoder.txgy.module.wms.service.inventory.WmsStockHistoryQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** Authenticated read-only access for ERP/MES. Never delegates to mutation services. */
@RestController
@RequestMapping("/wms/external-read")
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPermission('wms:inventory:query')")
public class WmsExternalReadController {
    private final WmsStockQueryService stock;
    private final WmsStockHistoryQueryService history;

    @GetMapping("/inventory")
    public CommonResult<PageResult<WmsStockRespDTO>> inventory(@Valid WmsStockPageReqDTO q) {
        validatePage(q.getPageNo(), q.getPageSize());
        return success(stock.getStockPage(q));
    }
    @GetMapping("/history")
    public CommonResult<PageResult<WmsStockHistoryRespDTO>> history(@Valid WmsStockHistoryPageReqDTO q) {
        return success(history.getHistoryPage(q));
    }
    @GetMapping("/warehouses")
    public CommonResult<PageResult<WmsWarehouseReadRespDTO>> warehouses(
            @RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String code, @RequestParam(required=false) String name) {
        validatePage(pageNo, pageSize);
        return success(page(stock.getWarehouses().stream()
                .filter(w -> code == null || code.isBlank() || code.equals(w.getCode()))
                .filter(w -> name == null || name.isBlank() || w.getName().contains(name)).toList(), pageNo, pageSize));
    }
    @GetMapping("/skus")
    public CommonResult<PageResult<cn.iocoder.txgy.module.wms.api.stockapplication.dto.WmsStockApplicationOptionsRespDTO.Sku>> skus(
            @Valid WmsStockPageReqDTO q) {
        validatePage(q.getPageNo(), q.getPageSize());
        return success(page(stock.getSkuOptions().stream()
                .filter(s -> q.getMaterialCode() == null || q.getMaterialCode().isBlank() || q.getMaterialCode().equals(s.getMaterialCode()))
                .filter(s -> q.getSkuCode() == null || q.getSkuCode().isBlank() || q.getSkuCode().equals(s.getSkuCode()))
                .filter(s -> q.getMaterialName() == null || q.getMaterialName().isBlank() || s.getMaterialName().contains(q.getMaterialName()))
                .toList(), q.getPageNo(), q.getPageSize()));
    }
    private static void validatePage(int number, int size) {
        if (number < 1 || size < 1 || size > 200) throw new IllegalArgumentException("pageNo>=1, pageSize=1..200");
    }
    private static <T> PageResult<T> page(List<T> rows, int number, int size) {
        int start = (int) Math.min(rows.size(), ((long) number - 1) * size);
        return new PageResult<>(rows.subList(start, Math.min(rows.size(), start + size)), (long) rows.size());
    }
}
