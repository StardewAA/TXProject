package cn.iocoder.txgy.module.trade.controller.app.order;

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
public class AppOrderWorkbenchController {
    private final OrderWorkbenchService service;
    public AppOrderWorkbenchController(OrderWorkbenchService service){this.service=service;}
    @GetMapping("/summary")
    public CommonResult<Summary> summary(@RequestParam Role role,@RequestParam(required=false) Long shopId,
            @RequestParam(required=false) String afterCursor,@RequestParam(required=false) List<Long> orderIds,
            @RequestParam(required=false) List<Long> afterSaleIds,@RequestParam(required=false) String no,
            @RequestParam(required=false) Boolean commentStatus) {
        var filters=new TradeOrderPageReqVO();filters.setNo(no);
        return success(service.summary(role,shopId,afterCursor,orderIds,afterSaleIds,filters,commentStatus,false));
    }
    @GetMapping("/page")
    public CommonResult<PageResult<Entry>> page(@RequestParam Role role,@RequestParam(required=false) Long shopId,
            @RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) Action action,@RequestParam(defaultValue="false") boolean unreadOnly) {
        return success(service.page(role,shopId,pageNo,pageSize,action,unreadOnly,false));
    }
    @PostMapping("/read")
    public CommonResult<Boolean> read(@Valid @RequestBody Read req){service.read(req,false);return success(true);}
}
