package cn.iocoder.txgy.module.product.controller.admin.spu;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.product.controller.admin.spu.vo.*;
import cn.iocoder.txgy.module.product.convert.spu.ProductSpuConvert;
import cn.iocoder.txgy.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.txgy.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.txgy.module.product.enums.spu.ProductSpuStatusEnum;
import cn.iocoder.txgy.module.product.service.enterprise.ProductAdminBusinessScopeService;
import cn.iocoder.txgy.module.product.service.sku.ProductSkuService;
import cn.iocoder.txgy.module.product.service.sku.dto.ProductWmsStockReconciliationDTO;
import cn.iocoder.txgy.module.product.service.spu.ProductSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.pojo.PageParam.PAGE_SIZE_NONE;

@Tag(name = "管理后台 - 商品 SPU")
@RestController
@RequestMapping("/product/spu")
@Validated
public class ProductSpuController {

    @Resource
    private ProductSpuService productSpuService;
    @Resource
    private ProductSkuService productSkuService;
    @Resource
    private ProductAdminBusinessScopeService businessScopeService;

    @PostMapping("/create")
    @Operation(summary = "创建商品 SPU")
    @PreAuthorize("@ss.hasPermission('product:spu:create')")
    public CommonResult<Long> createProductSpu(@Valid @RequestBody ProductSpuSaveReqVO createReqVO) {
        validateWriteShop(createReqVO.getShopId());
        return success(productSpuService.createSpu(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新商品 SPU")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Boolean> updateSpu(@Valid @RequestBody ProductSpuSaveReqVO updateReqVO) {
        validateManagedSpu(productSpuService.getSpu(updateReqVO.getId()));
        validateWriteShop(updateReqVO.getShopId());
        productSpuService.updateSpu(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新商品 SPU Status")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ProductSpuUpdateStatusReqVO updateReqVO) {
        validateManagedSpu(productSpuService.getSpu(updateReqVO.getId()));
        productSpuService.updateSpuStatus(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除商品 SPU")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:spu:delete')")
    public CommonResult<Boolean> deleteSpu(@RequestParam("id") Long id,
            @RequestParam("expectedVersion") @jakarta.validation.constraints.PositiveOrZero Long expectedVersion) {
        validateManagedSpu(productSpuService.getSpu(id));
        productSpuService.deleteSpu(id, expectedVersion);
        return success(true);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得商品 SPU 明细")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<ProductSpuRespVO> getSpuDetail(@RequestParam("id") Long id) {
        // 获得商品 SPU
        ProductSpuDO spu = productSpuService.getSpu(id);
        if (spu == null) {
            return success(null);
        }
        validateReadableSpu(spu);
        // 查询商品 SKU
        List<ProductSkuDO> skus = productSkuService.getSkuListBySpuId(spu.getId());
        return success(ProductSpuConvert.INSTANCE.convert(spu, skus));
    }

    @GetMapping("/list-all-simple")
    @Operation(summary = "获得商品 SPU 精简列表")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<List<ProductSpuSimpleRespVO>> getSpuSimpleList() {
        List<ProductSpuDO> list = productSpuService.getSpuListByStatus(ProductSpuStatusEnum.ENABLE.getStatus());
        filterManagedSpus(list);
        // 降序排序后，返回给前端
        list.sort(Comparator.comparing(ProductSpuDO::getSort).reversed());
        return success(BeanUtils.toBean(list, ProductSpuSimpleRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得商品 SPU 详情列表")
    @Parameter(name = "spuIds", description = "spu 编号列表", required = true, example = "[1,2,3]")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<List<ProductSpuRespVO>> getSpuList(@RequestParam("spuIds") Collection<Long> spuIds) {
        List<ProductSpuDO> spus = productSpuService.getSpuList(spuIds);
        filterManagedSpus(spus);
        return success(ProductSpuConvert.INSTANCE.convertForSpuDetailRespListVO(
                spus, productSkuService.getSkuListBySpuId(convertSpuIds(spus))));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商品 SPU 分页")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<PageResult<ProductSpuRespVO>> getSpuPage(@Valid ProductSpuPageReqVO pageVO) {
        applyManagedShopScope(pageVO);
        PageResult<ProductSpuDO> pageResult = productSpuService.getSpuPage(pageVO);
        return success(BeanUtils.toBean(pageResult, ProductSpuRespVO.class));
    }

    @GetMapping("/get-count")
    @Operation(summary = "获得商品 SPU 分页 tab count（支持按 name/categoryId/createTime 筛选）")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<Map<Integer, Long>> getSpuCount(ProductSpuPageReqVO reqVO) {
        applyManagedShopScope(reqVO);
        return success(productSpuService.getTabsCount(reqVO));
    }

    @GetMapping("/wms-stock-reconciliation")
    @Operation(summary = "获得商城商品与 WMS 的库存对账结果")
    @PreAuthorize("@ss.hasPermission('product:spu:query')")
    public CommonResult<List<ProductWmsStockReconciliationDTO>> getWmsStockReconciliation() {
        return success(productSkuService.getWmsStockReconciliation(getManagedSpuScope()));
    }

    @PutMapping("/sync-wms-stock-cache")
    @Operation(summary = "将 WMS 可售库存同步到商城商品缓存")
    @PreAuthorize("@ss.hasPermission('product:spu:update')")
    public CommonResult<Integer> syncWmsStockCache() {
        return success(productSkuService.syncWmsStockCache(getManagedSpuScope()));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出商品")
    @PreAuthorize("@ss.hasPermission('product:spu:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportSpuList(@Validated ProductSpuPageReqVO reqVO,
                               HttpServletResponse response) throws IOException {
        applyManagedShopScope(reqVO);
        reqVO.setPageSize(PAGE_SIZE_NONE);
        List<ProductSpuDO> list = productSpuService.getSpuPage(reqVO).getList();
        // 导出 Excel
        ExcelUtils.write(response, "商品列表.xls", "数据", ProductSpuRespVO.class,
                BeanUtils.toBean(list, ProductSpuRespVO.class));
    }

    private void applyManagedShopScope(ProductSpuPageReqVO reqVO) {
        ProductAdminBusinessScopeService.Scope scope = businessScopeService.currentScope();
        // 始终覆盖客户端绑定的隐藏字段，防止伪造 shopIds 扩大范围。
        reqVO.setShopIds(null);
        if (scope.unrestricted()) {
            if (reqVO.getShopId() != null) {
                businessScopeService.requireShopResource(scope.adminUserId(), reqVO.getShopId());
            }
            return;
        }
        if (reqVO.getShopId() != null && !scope.shopIds().contains(reqVO.getShopId())) {
            businessScopeService.requireShop(scope.adminUserId(), reqVO.getShopId());
        }
        reqVO.setShopIds(scope.shopIds().isEmpty() ? List.of(-1L) : scope.shopIds());
    }

    private void validateManagedSpu(ProductSpuDO spu) {
        if (spu != null) validateWriteShop(spu.getShopId());
    }

    private void validateReadableSpu(ProductSpuDO spu) {
        if (spu == null) return;
        if (spu.getShopId() == null) {
            businessScopeService.requireTenantManager(businessScopeService.currentAdminUserId());
            return;
        }
        businessScopeService.requireCurrentShopResource(spu.getShopId());
    }

    private void validateWriteShop(Long shopId) {
        // 商品经营必须落到显式企业/店铺授权。tenant_admin/super_admin 的 unrestricted
        // 仅用于治理侧查询与审核，不能代替商家的经营授权。
        businessScopeService.requireMerchantShop(businessScopeService.currentAdminUserId(), shopId);
    }

    private void filterManagedSpus(List<ProductSpuDO> spus) {
        ProductAdminBusinessScopeService.Scope scope = businessScopeService.currentScope();
        if (!scope.unrestricted()) spus.removeIf(spu -> !scope.shopIds().contains(spu.getShopId()));
    }

    /** null 表示平台管理员可查看全部；空集合表示商家当前没有商品。 */
    private Collection<Long> getManagedSpuScope() {
        ProductAdminBusinessScopeService.Scope scope = businessScopeService.currentScope();
        if (scope.unrestricted()) return null;
        if (scope.shopIds().isEmpty()) return List.of();
        ProductSpuPageReqVO reqVO = new ProductSpuPageReqVO();
        reqVO.setShopIds(scope.shopIds());
        reqVO.setPageSize(PAGE_SIZE_NONE);
        return convertSpuIds(productSpuService.getSpuPage(reqVO).getList());
    }

    private static Collection<Long> convertSpuIds(List<ProductSpuDO> spus) {
        return spus.stream().map(ProductSpuDO::getId).toList();
    }

}

