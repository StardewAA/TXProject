package cn.iocoder.txgy.module.trade.controller.admin.order;

import cn.iocoder.txgy.framework.common.pojo.*;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.service.workbench.*;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.*;

@RestController @RequestMapping("/trade/order-workbench") @Validated
public class OrderWorkbenchController {
    private final OrderWorkbenchService service;
    public OrderWorkbenchController(OrderWorkbenchService service){this.service=service;}
    @GetMapping("/summary")
    public CommonResult<Summary> summary(@RequestParam(defaultValue="ADMIN") Role role,@RequestParam(required=false) String afterCursor,
            @RequestParam(required=false) List<Long> orderIds,@RequestParam(required=false) List<Long> afterSaleIds,
            @Valid TradeOrderPageReqVO filters) {
        return success(service.summary(role,null,afterCursor,orderIds,afterSaleIds,filters,null,true));
    }
    @GetMapping("/page")
    public CommonResult<PageResult<Entry>> page(@RequestParam(defaultValue="ADMIN") Role role,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) Action action,@RequestParam(defaultValue="false") boolean unreadOnly) {
        return success(service.page(role,null,pageNo,pageSize,action,unreadOnly,true));
    }
    @PostMapping("/read")
    public CommonResult<Boolean> read(@Valid @RequestBody Read req){service.read(req,true);return success(true);}
}
