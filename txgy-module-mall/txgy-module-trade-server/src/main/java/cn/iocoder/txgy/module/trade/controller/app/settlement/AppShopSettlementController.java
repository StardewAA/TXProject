package cn.iocoder.txgy.module.trade.controller.app.settlement;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.trade.service.settlement.ShopSettlementService;
import cn.iocoder.txgy.module.trade.framework.compatibility.LegacyMemberSupplierWriteGuard;
import cn.iocoder.txgy.module.trade.service.settlement.SettlementModels.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/trade/supplier-settlement")
public class AppShopSettlementController {
    @Resource private ShopSettlementService service;
    @Resource private LegacyMemberSupplierWriteGuard legacyWriteGuard;
    @GetMapping("/page") public CommonResult<PageResult<Bill>> page(@RequestParam Long shopId,@Valid PageParam page,@RequestParam(required=false) Integer status){return success(service.page(shopId,page,status,true));}
    @GetMapping("/get") public CommonResult<Map<String,Object>> get(@RequestParam Long id,@RequestParam Long shopId){return success(service.detail(id,shopId,true));}
    @GetMapping("/proof") public CommonResult<String> proof(@RequestParam Long id,@RequestParam Long shopId){return success(service.proof(id,shopId,true));}
    @PutMapping("/confirm") public CommonResult<Boolean> confirm(@Valid @RequestBody Confirm req){legacyWriteGuard.requireEnabled();service.confirm(req);return success(true);}
}
