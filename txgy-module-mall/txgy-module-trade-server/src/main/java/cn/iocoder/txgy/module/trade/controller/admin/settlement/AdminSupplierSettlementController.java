package cn.iocoder.txgy.module.trade.controller.admin.settlement;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.Bill;
import cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.Confirm;
import cn.iocoder.txgy.module.trade.service.settlement.ShopSettlementService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** 店铺财务在后台核对、确认本店账单；生成、审核和付款仍由租户管理人员负责。 */
@RestController
@RequestMapping("/trade/supplier-settlement")
public class AdminSupplierSettlementController {

    @Resource
    private ShopSettlementService service;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('trade:supplier-settlement:query')")
    public CommonResult<PageResult<Bill>> page(@Valid PageParam page,
                                                @RequestParam Long shopId,
                                                @RequestParam(required = false) Integer status) {
        return success(service.adminSupplierPage(shopId, page, status));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('trade:supplier-settlement:query')")
    public CommonResult<Map<String, Object>> get(@RequestParam Long id, @RequestParam Long shopId) {
        return success(service.adminSupplierDetail(id, shopId));
    }

    @GetMapping("/proof")
    @PreAuthorize("@ss.hasPermission('trade:supplier-settlement:query')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<String> proof(@RequestParam Long id, @RequestParam Long shopId) {
        return success(service.adminSupplierProof(id, shopId));
    }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('trade:supplier-settlement:confirm')")
    public CommonResult<Boolean> confirm(@Valid @RequestBody Confirm request) {
        service.adminSupplierConfirm(request);
        return success(true);
    }
}
