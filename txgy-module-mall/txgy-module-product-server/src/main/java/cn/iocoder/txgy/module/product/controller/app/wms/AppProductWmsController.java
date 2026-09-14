package cn.iocoder.txgy.module.product.controller.app.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.controller.app.wms.vo.AppProductWmsSkuSummaryRespVO;
import cn.iocoder.txgy.module.product.service.sku.ProductSkuService;
import cn.iocoder.txgy.module.product.service.wms.readonly.ProductWmsSkuSummaryDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - WMS 只读查询")
@RestController
@RequestMapping("/product/wms")
@Validated
public class AppProductWmsController {

    @Resource
    private ProductSkuService productSkuService;

    @GetMapping("/sku-summary")
    @Operation(summary = "获得 WMS 商品规格、售价及库存摘要")
    @Parameter(name = "itemId", description = "WMS 商品编号", required = true)
    @PermitAll
    public CommonResult<List<AppProductWmsSkuSummaryRespVO>> getSkuSummary(@RequestParam("itemId") Long itemId) {
        List<ProductWmsSkuSummaryDTO> summaryList = productSkuService.getWmsSkuSummary(itemId);
        return success(BeanUtils.toBean(summaryList, AppProductWmsSkuSummaryRespVO.class));
    }

}
