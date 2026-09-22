package cn.iocoder.txgy.module.trade.controller.app.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.*;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.item.AppTradeOrderItemRespVO;
import cn.iocoder.txgy.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.aftersale.EnterprisePurchaseAfterSaleService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.erp.TradeErpInventoryService;
import cn.iocoder.txgy.module.trade.service.order.EnterprisePurchaseOrderService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderPaymentDeadlineService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** PC 采购中心的企业询价成交订单。与个人订单端点物理分开，避免扩大普通订单授权。 */
@Tag(name = "用户 App - 企业采购订单")
@RestController
@RequestMapping("/trade/purchase-order")
@Validated
@Slf4j
public class AppEnterprisePurchaseOrderController {

    @Resource
    private EnterprisePurchaseOrderService enterpriseOrders;
    @Resource
    private TradeOrderQueryService orderQueryService;
    @Resource
    private TradeOrderUpdateService orderUpdateService;
    @Resource
    private DeliveryExpressService expressService;
    @Resource
    private EnterprisePurchaseAfterSaleService enterpriseAfterSales;
    @Resource
    private TradeOrderProperties orderProperties;
    @Resource
    private TradeOrderPaymentDeadlineService paymentDeadline;
    @Resource
    private TradeErpInventoryService erpInventory;
    @Resource
    private ProductShopApi productShopApi;

    @GetMapping("/page")
    @Operation(summary = "获得本企业询价成交订单分页")
    public CommonResult<PageResult<AppTradeOrderPageItemRespVO>> getPage(AppTradeOrderPageReqVO reqVO) {
        Long memberId = getLoginUserId();
        PageResult<TradeOrderDO> page = enterpriseOrders.getPage(memberId, reqVO);
        List<TradeOrderItemDO> items = orderQueryService.getOrderItemListByOrderId(
                convertSet(page.getList(), TradeOrderDO::getId));
        PageResult<AppTradeOrderPageItemRespVO> response = TradeOrderConvert.INSTANCE.convertPage02(page, items);
        fillLatestAfterSales(response.getList().stream().flatMap(row -> row.getItems().stream()).toList(), memberId);
        for (int index = 0; index < page.getList().size(); index++) {
            TradeOrderDO order = page.getList().get(index);
            AppTradeOrderPageItemRespVO row = response.getList().get(index);
            row.setCreatedByCurrentMember(Objects.equals(order.getUserId(), memberId));
            paymentDeadline.fill(order, row, false);
        }
        return success(response);
    }

    @GetMapping("/get-detail")
    @Operation(summary = "获得本企业询价成交订单详情")
    public CommonResult<AppTradeOrderDetailRespVO> getDetail(@RequestParam("id") Long id,
                                                               @RequestParam(value = "sync", required = false) Boolean sync) {
        Long memberId = getLoginUserId();
        TradeOrderDO order = enterpriseOrders.getOrder(memberId, id);
        if (Boolean.TRUE.equals(sync) && TradeOrderStatusEnum.isUnpaid(order.getStatus())
                && !Boolean.TRUE.equals(order.getPayStatus()) && order.getPayOrderId() != null) {
            orderUpdateService.syncOrderPayStatusQuietly(order.getId(), order.getPayOrderId());
            order = enterpriseOrders.getOrder(memberId, id);
        }
        List<TradeOrderItemDO> items = orderQueryService.getOrderItemListByOrderId(order.getId());
        DeliveryExpressDO express = order.getLogisticsId() != null && order.getLogisticsId() > 0
                ? expressService.getDeliveryExpress(order.getLogisticsId()) : null;
        AppTradeOrderDetailRespVO response = TradeOrderConvert.INSTANCE.convert02(order, items, orderProperties, express);
        response.setCreatedByCurrentMember(Objects.equals(order.getUserId(), memberId));
        fillDispatchOrigin(response);
        fillLatestAfterSales(response.getItems(), memberId);
        response.setErpInventory(erpInventory.list(order.getId()));
        paymentDeadline.fill(order, response, true);
        return success(response);
    }

    @GetMapping("/get-count")
    @Operation(summary = "获得本企业询价成交订单数量")
    public CommonResult<Map<String, Long>> getCount() {
        Long memberId = getLoginUserId();
        Map<String, Long> result = enterpriseOrders.getCount(memberId);
        result.put("afterSaleCount", enterpriseAfterSales.getApplyingCount(memberId));
        return success(result);
    }

    @PutMapping("/receive")
    @Operation(summary = "企业采购成员确认收货")
    public CommonResult<Boolean> receive(@RequestParam("id") Long id) {
        enterpriseOrders.receive(getLoginUserId(), id);
        return success(true);
    }

    @DeleteMapping("/cancel")
    @Operation(summary = "企业采购成员取消待支付订单")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        enterpriseOrders.cancel(getLoginUserId(), id);
        return success(true);
    }

    @PutMapping("/update-address")
    @Operation(summary = "企业采购成员修改待支付快递订单地址")
    public CommonResult<Boolean> updateAddress(@Valid @RequestBody AppTradeOrderUpdateAddressReqVO reqVO) {
        enterpriseOrders.updateAddress(getLoginUserId(), reqVO);
        return success(true);
    }

    @GetMapping("/get-logistics")
    @Operation(summary = "获得本企业询价成交订单物流")
    @Parameter(name = "id", description = "订单编号")
    public CommonResult<AppTradeOrderLogisticsRespVO> getLogistics(@RequestParam("id") Long id) {
        return success(enterpriseOrders.getLogistics(getLoginUserId(), id));
    }

    private void fillDispatchOrigin(AppTradeOrderDetailRespVO response) {
        if (response.getShopId() == null || response.getShopId() <= 0) return;
        try {
            CommonResult<ProductShopRespDTO> result = productShopApi.getShop(response.getShopId());
            if (result != null && result.isSuccess() && result.getData() != null) {
                response.setDispatchOriginAddress(result.getData().getDispatchAddress());
            }
        } catch (Exception exception) {
            log.warn("读取企业采购订单发货地址失败，orderId={}, shopId={}",
                    response.getId(), response.getShopId(), exception);
        }
    }

    private void fillLatestAfterSales(List<AppTradeOrderItemRespVO> items, Long memberId) {
        if (items == null || items.isEmpty()) return;
        // 企业订单页展示同企业的售后处理结果；资金和退货变更仍由独立售后端点限制为实际下单人。
        Map<Long, AfterSaleDO> latest = enterpriseAfterSales.getLatest(memberId,
                items.stream().map(AppTradeOrderItemRespVO::getId).filter(Objects::nonNull).toList());
        for (AppTradeOrderItemRespVO item : items) {
            AfterSaleDO afterSale = latest.get(item.getId());
            if (afterSale == null) continue;
            String reason = afterSale.getAuditReason() != null && !afterSale.getAuditReason().isBlank()
                    ? afterSale.getAuditReason() : afterSale.getReceiveReason();
            item.setLatestAfterSaleId(afterSale.getId()).setLatestAfterSaleStatus(afterSale.getStatus())
                    .setLatestAfterSaleReason(reason);
        }
    }
}
