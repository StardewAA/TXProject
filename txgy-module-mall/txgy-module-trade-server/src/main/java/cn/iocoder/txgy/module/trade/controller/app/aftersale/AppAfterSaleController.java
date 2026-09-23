package cn.iocoder.txgy.module.trade.controller.app.aftersale;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.service.aftersale.PlatformBuyerAfterSaleService;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "用户 App - 交易售后")
@RestController
@RequestMapping("/trade/after-sale")
@Validated
@Slf4j
public class AppAfterSaleController {

    @Resource
    private PlatformBuyerAfterSaleService platformBuyerAfterSales;

    @GetMapping(value = "/page")
    @Operation(summary = "获得售后分页")
    public CommonResult<PageResult<AppAfterSaleRespVO>> getAfterSalePage(AppAfterSalePageReqVO pageReqVO) {
        PageResult<AfterSaleDO> pageResult = platformBuyerAfterSales.page(getLoginUserId(),
                TenantContextHolder.getRequiredTenantId(), pageReqVO);
        return success(BeanUtils.toBean(pageResult, AppAfterSaleRespVO.class));
    }

    @GetMapping(value = "/get")
    @Operation(summary = "获得售后订单")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    public CommonResult<AppAfterSaleRespVO> getAfterSale(@RequestParam("id") Long id) {
        AfterSaleDO afterSale = platformBuyerAfterSales.get(getLoginUserId(),
                TenantContextHolder.getRequiredTenantId(), id);
        return success(BeanUtils.toBean(afterSale, AppAfterSaleRespVO.class));
    }

    @PostMapping(value = "/create")
    @Operation(summary = "申请售后")
    public CommonResult<Long> createAfterSale(@Valid @RequestBody AppAfterSaleCreateReqVO createReqVO) {
        return success(platformBuyerAfterSales.create(getLoginUserId(),
                TenantContextHolder.getRequiredTenantId(), createReqVO));
    }

    @PutMapping(value = "/delivery")
    @Operation(summary = "退回货物")
    public CommonResult<Boolean> deliveryAfterSale(@Valid @RequestBody AppAfterSaleDeliveryReqVO deliveryReqVO) {
        platformBuyerAfterSales.delivery(getLoginUserId(), TenantContextHolder.getRequiredTenantId(), deliveryReqVO);
        return success(true);
    }

    @DeleteMapping(value = "/cancel")
    @Operation(summary = "取消售后")
    @Parameter(name = "id", description = "售后编号", required = true, example = "1")
    public CommonResult<Boolean> cancelAfterSale(@RequestParam("id") Long id) {
        platformBuyerAfterSales.cancel(getLoginUserId(), TenantContextHolder.getRequiredTenantId(), id);
        return success(true);
    }

}
