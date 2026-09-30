package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderTraceRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderPdfExporter;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderTraceService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.txgy.module.erp.service.stock.ErpStockService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMultiMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - ERP 采购订单")
@RestController
@RequestMapping("/erp/purchase-order")
@Validated
public class ErpPurchaseOrderController {

    @Resource
    private ErpPurchaseOrderService purchaseOrderService;
    @Resource
    private ErpStockService stockService;
    @Resource
    private ErpProductService productService;
    @Resource
    private ErpSupplierService supplierService;

    @Resource
    private AdminUserApi adminUserApi;

    @Resource
    private ErpPurchaseOrderTraceService purchaseOrderTraceService;

    @GetMapping("/trace")
    @Operation(summary = "获得采购订单溯源",
            description = "一次返回：来源询价单 → 中标明细 → 采购订单 → 采购入库单，用于订单详情页溯源展示")
    @Parameter(name = "id", description = "采购订单编号", required = true, example = "30")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:query')")
    public CommonResult<ErpPurchaseOrderTraceRespVO> getTrace(@RequestParam("id") Long id) {
        return success(purchaseOrderTraceService.getTrace(id));
    }

    @PostMapping("/create")
    @Operation(summary = "创建采购订单")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:create')")
    public CommonResult<Long> createPurchaseOrder(@Valid @RequestBody ErpPurchaseOrderSaveReqVO createReqVO) {
        return success(purchaseOrderService.createPurchaseOrder(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新采购订单")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:update')")
    public CommonResult<Boolean> updatePurchaseOrder(@Valid @RequestBody ErpPurchaseOrderSaveReqVO updateReqVO) {
        purchaseOrderService.updatePurchaseOrder(updateReqVO);
        return success(true);
    }

    @PutMapping("/reverse-approve")
    @Operation(summary = "反审批采购订单")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:reverse-approve')")
    @Parameter(name = "id", description = "采购订单编号", required = true)
    @Parameter(name = "reason", description = "反审批原因", required = true)
    public CommonResult<Boolean> reverseApprovePurchaseOrder(@RequestParam("id") Long id,
                                                             @RequestParam("reason") @NotBlank(message = "反审批原因不能为空")
                                                             @Size(max = 200, message = "反审批原因不能超过 200 个字符") String reason) {
        purchaseOrderService.reverseApprovePurchaseOrder(id, getLoginUserId(), reason);
        return success(true);
    }

    @PutMapping("/submit")
    @Operation(summary = "提交采购订单审批")
    @PreAuthorize("@ss.hasPermission('erp:approval:submit')")
    public CommonResult<Boolean> submitPurchaseOrder(@RequestParam("id") Long id) {
        purchaseOrderService.submitPurchaseOrder(id, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除采购订单")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:delete')")
    public CommonResult<Boolean> deletePurchaseOrder(@RequestParam("ids") List<Long> ids) {
        purchaseOrderService.deletePurchaseOrder(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得采购订单")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:query')")
    public CommonResult<ErpPurchaseOrderRespVO> getPurchaseOrder(@RequestParam("id") Long id) {
        ErpPurchaseOrderDO purchaseOrder = purchaseOrderService.getPurchaseOrder(id);
        if (purchaseOrder == null) {
            return success(null);
        }
        List<ErpPurchaseOrderItemDO> purchaseOrderItemList = purchaseOrderService.getPurchaseOrderItemListByOrderId(id);
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                convertSet(purchaseOrderItemList, ErpPurchaseOrderItemDO::getProductId));
        return success(BeanUtils.toBean(purchaseOrder, ErpPurchaseOrderRespVO.class, purchaseOrderVO ->
                purchaseOrderVO.setItems(BeanUtils.toBean(purchaseOrderItemList, ErpPurchaseOrderRespVO.Item.class, item -> {
                    BigDecimal purchaseCount = stockService.getStockCount(item.getProductId());
                    item.setStockCount(purchaseCount != null ? purchaseCount : BigDecimal.ZERO);
                    MapUtils.findAndThen(productMap, item.getProductId(), product -> item.setProductName(product.getName())
                            .setProductBarCode(product.getBarCode()).setProductUnitName(product.getUnitName()));
                }))));
    }

    @GetMapping("/page")
    @Operation(summary = "获得采购订单分页")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:query')")
    public CommonResult<PageResult<ErpPurchaseOrderRespVO>> getPurchaseOrderPage(@Valid ErpPurchaseOrderPageReqVO pageReqVO) {
        PageResult<ErpPurchaseOrderDO> pageResult = purchaseOrderService.getPurchaseOrderPage(pageReqVO);
        return success(buildPurchaseOrderVOPageResult(pageResult));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出采购订单 Excel")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportPurchaseOrderExcel(@Valid ErpPurchaseOrderPageReqVO pageReqVO,
                                         HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<ErpPurchaseOrderRespVO> list = buildPurchaseOrderVOPageResult(purchaseOrderService.getPurchaseOrderPage(pageReqVO)).getList();
        // 导出 Excel
        ExcelUtils.write(response, "采购订单.xlsx", "数据", ErpPurchaseOrderRespVO.class, list);
    }

    @GetMapping("/export-pdf")
    @Operation(summary = "导出采购订单 PDF")
    @PreAuthorize("@ss.hasPermission('erp:purchase-order:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportPurchaseOrderPdf(@Valid ErpPurchaseOrderPageReqVO pageReqVO,
                                       HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<ErpPurchaseOrderRespVO> list = buildPurchaseOrderVOPageResult(
                purchaseOrderService.getPurchaseOrderPage(pageReqVO)).getList();
        ErpPurchaseOrderPdfExporter.write(response, list);
    }

    private PageResult<ErpPurchaseOrderRespVO> buildPurchaseOrderVOPageResult(PageResult<ErpPurchaseOrderDO> pageResult) {
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        // 1.1 订单项
        List<ErpPurchaseOrderItemDO> purchaseOrderItemList = purchaseOrderService.getPurchaseOrderItemListByOrderIds(
                convertSet(pageResult.getList(), ErpPurchaseOrderDO::getId));
        Map<Long, List<ErpPurchaseOrderItemDO>> purchaseOrderItemMap = convertMultiMap(purchaseOrderItemList, ErpPurchaseOrderItemDO::getOrderId);
        // 1.2 产品信息
        Map<Long, ErpProductRespVO> productMap = productService.getProductVOMap(
                convertSet(purchaseOrderItemList, ErpPurchaseOrderItemDO::getProductId));
        // 1.3 供应商信息
        Map<Long, ErpSupplierDO> supplierMap = supplierService.getSupplierMap(
                convertSet(pageResult.getList(), ErpPurchaseOrderDO::getSupplierId));
        // 1.4 管理员信息
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMapIgnoreTenant(
                convertSet(pageResult.getList(), purchaseOrder -> Long.parseLong(purchaseOrder.getCreator())));
        // 2. 开始拼接
        return BeanUtils.toBean(pageResult, ErpPurchaseOrderRespVO.class, purchaseOrder -> {
            purchaseOrder.setItems(BeanUtils.toBean(purchaseOrderItemMap.get(purchaseOrder.getId()), ErpPurchaseOrderRespVO.Item.class,
                    item -> MapUtils.findAndThen(productMap, item.getProductId(), product -> item.setProductName(product.getName())
                            .setProductBarCode(product.getBarCode()).setProductUnitName(product.getUnitName()))));
            purchaseOrder.setProductNames(CollUtil.join(purchaseOrder.getItems(), "，", ErpPurchaseOrderRespVO.Item::getProductName));
            MapUtils.findAndThen(supplierMap, purchaseOrder.getSupplierId(), supplier -> purchaseOrder.setSupplierName(supplier.getName()));
            MapUtils.findAndThen(userMap, Long.parseLong(purchaseOrder.getCreator()), user -> purchaseOrder.setCreatorName(user.getNickname()));
        });
    }

}
