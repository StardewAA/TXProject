package cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehousePageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseProductRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.sharedwarehouse.vo.WmsSharedWarehouseRespVO;
import cn.iocoder.txgy.module.wms.service.sharedwarehouse.WmsSharedWarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - WMS 共享仓库")
@RestController
@RequestMapping("/wms/shared-warehouse")
@Validated
public class WmsSharedWarehouseController {

    @Resource
    private WmsSharedWarehouseService sharedWarehouseService;

    @GetMapping("/page")
    @Operation(summary = "获得共享仓库分页")
    @PreAuthorize("@ss.hasPermission('wms:shared-warehouse:query')")
    public CommonResult<PageResult<WmsSharedWarehouseRespVO>> getSharedWarehousePage(
            @Valid WmsSharedWarehousePageReqVO pageReqVO) {
        return success(sharedWarehouseService.getSharedWarehousePage(pageReqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "获得共享仓库列表")
    @PreAuthorize("@ss.hasPermission('wms:shared-warehouse:query')")
    public CommonResult<List<WmsSharedWarehouseRespVO>> getSharedWarehouseList(
            @Valid WmsSharedWarehousePageReqVO pageReqVO) {
        return success(sharedWarehouseService.getSharedWarehouseList(pageReqVO));
    }

    @GetMapping("/products")
    @Operation(summary = "获得共享仓库商品分页")
    @PreAuthorize("@ss.hasPermission('wms:shared-warehouse:query')")
    public CommonResult<PageResult<WmsSharedWarehouseProductRespVO>> getSharedWarehouseProducts(
            @Valid WmsSharedWarehouseProductPageReqVO pageReqVO) {
        return success(sharedWarehouseService.getSharedWarehouseProductPage(pageReqVO));
    }

}
