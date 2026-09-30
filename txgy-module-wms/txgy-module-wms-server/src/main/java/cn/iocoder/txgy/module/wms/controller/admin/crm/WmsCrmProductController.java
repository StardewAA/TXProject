package cn.iocoder.txgy.module.wms.controller.admin.crm;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductRespVO;
import cn.iocoder.txgy.module.wms.service.inventory.WmsCrmProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "CRM 对接 - WMS 商品只读")
@RestController
@RequestMapping("/wms/crm/products")
@RequiredArgsConstructor
@Validated
public class WmsCrmProductController {
    private final WmsCrmProductQueryService service;

    @GetMapping("/page")
    @Operation(summary = "供 CRM 产品管理实时读取商品 SKU 分页", description = "含仓库库存数量与销售单价；只读取，不修改 WMS 库存")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<PageResult<WmsCrmProductRespVO>> getPage(@Valid WmsCrmProductPageReqVO request) {
        return success(service.getPage(request));
    }

    @GetMapping("/get")
    @Operation(summary = "供 CRM 按 SKU 编号实时读取单个商品", description = "WMS 不存在该规格时返回空")
    @Parameter(name = "skuId", description = "WMS 规格编号", required = true, example = "101")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<WmsCrmProductRespVO> get(@RequestParam("skuId") Long skuId) {
        return success(service.getBySkuId(skuId));
    }

    @GetMapping("/list")
    @Operation(summary = "供 CRM 按 SKU 编号批量读取商品", description = "WMS 已删除的规格不会返回")
    @Parameter(name = "skuIds", description = "WMS 规格编号数组", required = true, example = "101,102")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<List<WmsCrmProductRespVO>> list(@RequestParam("skuIds") List<Long> skuIds) {
        return success(service.getBySkuIds(skuIds));
    }
}
