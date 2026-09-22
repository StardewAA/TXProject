package cn.iocoder.txgy.module.trade.controller.admin.settlement;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.trade.service.settlement.ShopSettlementService;
import cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/trade/shop-settlement")
public class ShopSettlementController {
    @Resource private ShopSettlementService service;
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:query') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    public CommonResult<PageResult<Bill>> page(@Valid PageParam page,@RequestParam(required=false) Long shopId,@RequestParam(required=false) Integer status){return success(service.page(shopId,page,status,false));}
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:query') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    public CommonResult<Map<String,Object>> get(@RequestParam Long id){return success(service.detail(id,null,false));}
    @GetMapping("/proof") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:query') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    public CommonResult<String> proof(@RequestParam Long id){return success(service.proof(id,null,false));}
    @PostMapping("/generate") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:create') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    public CommonResult<Bill> generate(@Valid @RequestBody Generate req){return success(service.generate(req));}
    @PutMapping("/audit") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:audit') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    public CommonResult<Boolean> audit(@Valid @RequestBody Audit req){service.audit(req);return success(true);}
    @PutMapping("/pay") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:confirm') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    @cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Boolean> pay(@Valid @RequestBody Pay req){service.pay(req);return success(true);}
    @PutMapping("/carry") @PreAuthorize("@ss.hasPermission('trade:shop-settlement:confirm') and @ss.hasAnyRoles('super_admin', 'tenant_admin')")
    public CommonResult<Boolean> carry(@Valid @RequestBody Audit req){service.carry(req.id(),req.version());return success(true);}
    // No admin /confirm: only an explicitly authorized shop member may confirm a bill.
}
