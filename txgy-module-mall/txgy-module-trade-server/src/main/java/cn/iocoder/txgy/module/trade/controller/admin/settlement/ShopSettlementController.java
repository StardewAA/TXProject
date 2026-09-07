package cn.iocoder.txgy.module.trade.controller.admin.settlement;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** 简化版店铺结算接口：用于商城联调，暂不接财务系统。 */
@RestController
@RequestMapping("/trade/shop-settlement")
public class ShopSettlementController {
    private static final List<Map<String,Object>> DATA = new CopyOnWriteArrayList<>();

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('trade:shop-settlement:query')")
    public CommonResult<PageResult<Map<String,Object>>> page(@RequestParam(defaultValue="1") Integer pageNo,
                                                              @RequestParam(defaultValue="20") Integer pageSize,
                                                              @RequestParam(required=false) Long shopId) {
        List<Map<String,Object>> list = DATA.stream().filter(x -> shopId == null || Objects.equals(x.get("shopId"), shopId)).toList();
        return success(new PageResult<>(list, (long) list.size()));
    }

    @PostMapping("/generate")
    @PreAuthorize("@ss.hasPermission('trade:shop-settlement:generate')")
    public CommonResult<Map<String,Object>> generate(@RequestBody Map<String,Object> body) {
        Map<String,Object> row = new LinkedHashMap<>(body);
        row.put("id", System.currentTimeMillis()); row.put("no", "SET" + row.get("id"));
        row.putIfAbsent("orderCount", 0); row.putIfAbsent("settlementAmount", 0); row.put("status", 10);
        DATA.add(row); return success(row);
    }

    @PutMapping("/audit")
    @PreAuthorize("@ss.hasPermission('trade:shop-settlement:audit')")
    public CommonResult<Boolean> audit(@RequestBody Map<String,Object> body) { return updateStatus(body, 20); }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('trade:shop-settlement:confirm')")
    public CommonResult<Boolean> confirm(@RequestParam Long id) { Map<String,Object> b=new HashMap<>(); b.put("id",id); return updateStatus(b,30); }

    private CommonResult<Boolean> updateStatus(Map<String,Object> body, int status) {
        DATA.stream().filter(x -> Objects.equals(String.valueOf(x.get("id")), String.valueOf(body.get("id"))))
                .findFirst().ifPresent(x -> x.put("status", status));
        return success(true);
    }
}
