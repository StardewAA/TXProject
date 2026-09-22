package cn.iocoder.txgy.module.trade.service.order;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.module.member.api.address.MemberAddressApi;
import cn.iocoder.txgy.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.txgy.module.pay.api.order.PayOrderApi;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.api.refund.PayRefundApi;
import cn.iocoder.txgy.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.refund.dto.PayRefundRespDTO;
import cn.iocoder.txgy.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.txgy.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.txgy.module.product.api.comment.ProductCommentApi;
import cn.iocoder.txgy.module.product.api.comment.dto.ProductCommentCreateReqDTO;
import cn.iocoder.txgy.module.product.api.inquiry.ProductInquiryApi;
import cn.iocoder.txgy.module.product.api.inquiry.dto.InquiryAcceptedQuoteRespDTO;
import cn.iocoder.txgy.module.product.api.sku.ProductSkuApi;
import cn.iocoder.txgy.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.product.api.spu.ProductSpuApi;
import cn.iocoder.txgy.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.txgy.module.product.api.tenant.MallTenantWriteGuard;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.txgy.module.promotion.api.combination.dto.CombinationRecordRespDTO;
import cn.iocoder.txgy.module.promotion.enums.combination.CombinationRecordStatusEnum;
import cn.iocoder.txgy.module.system.api.social.SocialClientApi;
import cn.iocoder.txgy.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderRemarkReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderUpdateAddressReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.order.vo.TradeOrderUpdatePriceReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeInquiryOrderCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderSettlementRespVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderUpdateAddressReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.item.AppTradeOrderItemCommentCreateReqVO;
import cn.iocoder.txgy.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.cart.CartDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryPickUpStoreDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.txgy.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.txgy.module.trade.enums.order.*;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.framework.order.core.annotations.TradeOrderLog;
import cn.iocoder.txgy.module.trade.framework.order.core.utils.TradeOrderLogUtils;
import cn.iocoder.txgy.module.trade.service.cart.CartService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryPickUpStoreService;
import cn.iocoder.txgy.module.trade.service.message.TradeMessageService;
import cn.iocoder.txgy.module.trade.service.message.bo.TradeOrderMessageWhenDeliveryOrderReqBO;
import cn.iocoder.txgy.module.trade.service.order.handler.TradeOrderHandler;
import cn.iocoder.txgy.module.trade.service.price.TradePriceService;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.txgy.module.trade.service.price.bo.TradePriceCalculateRespBO;
import cn.iocoder.txgy.module.trade.service.price.calculator.TradePriceCalculatorHelper;
import cn.iocoder.txgy.module.trade.service.price.calculator.TradeDeliveryPriceCalculator;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsStockService;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsOutboundService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils.minusTime;
import static cn.iocoder.txgy.framework.common.util.servlet.ServletUtils.getClientIP;
import static cn.iocoder.txgy.framework.web.core.util.WebFrameworkUtils.getTerminal;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.*;
import static cn.iocoder.txgy.module.trade.enums.MessageTemplateConstants.WXA_ORDER_DELIVERY;

/**
 * 交易订单【写】Service 实现类
 *
 * @author LeeYan9
 * @since 2022-08-26
 */
@Service
@Slf4j
public class TradeOrderUpdateServiceImpl implements TradeOrderUpdateService {

    @Resource
    private cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchJournal workbenchJournal;

    private void workbenchChanged(Long orderId) {
        // 生产启动强制注入；直接构造的历史单测未启用待办基础设施。
        if (workbenchJournal != null) workbenchJournal.changed(orderId);
    }
    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private TradeNoRedisDAO tradeNoRedisDAO;

    @Resource
    private List<TradeOrderHandler> tradeOrderHandlers;

    @Resource
    private CartService cartService;
    @Resource
    private TradePriceService tradePriceService;
    @Resource
    private DeliveryExpressService deliveryExpressService;
    @Resource
    private TradeMessageService tradeMessageService;
    @Resource
    private DeliveryPickUpStoreService pickUpStoreService;

    @Resource
    private PayOrderApi payOrderApi;
    @Resource
    private MemberAddressApi addressApi;
    @Resource
    private ProductCommentApi productCommentApi;
    @Resource
    private ProductShopApi productShopApi;
    @Resource
    private ProductSkuApi productSkuApi;
    @Resource
    private ProductSpuApi productSpuApi;
    @Resource
    private ProductInquiryApi productInquiryApi;
    @Resource
    private TradeDeliveryPriceCalculator deliveryPriceCalculator;
    @Resource
    public SocialClientApi socialClientApi;
    @Resource
    public PayRefundApi payRefundApi;
    @Resource
    private CombinationRecordApi combinationRecordApi;

    @Resource
    private TradeOrderProperties tradeOrderProperties;
    @Resource
    private TradeOrderPaymentDeadlineService paymentDeadline;
    @Resource
    private TradeWmsStockService wmsStockService;
    @Resource
    private TradeWmsOutboundService wmsOutboundService;

    // =================== Order ===================

    @Override
    public AppTradeOrderSettlementRespVO settlementOrder(Long userId, AppTradeOrderSettlementReqVO settlementReqVO) {
        // 1. 获得收货地址
        MemberAddressRespDTO address = getAddress(userId, settlementReqVO.getAddressId());
        if (address != null) {
            settlementReqVO.setAddressId(address.getId());
        }

        // 2. 计算价格
        TradePriceCalculateRespBO calculateRespBO = calculatePrice(userId, settlementReqVO);
        requireSingleShop(calculateRespBO);
        wmsStockService.validateSkuCounts(calculateRespBO.getItems().stream().collect(
                java.util.stream.Collectors.toMap(TradePriceCalculateRespBO.OrderItem::getSkuId,
                        TradePriceCalculateRespBO.OrderItem::getCount, Math::addExact, LinkedHashMap::new)));

        // 3. 拼接返回
        return TradeOrderConvert.INSTANCE.convert(calculateRespBO, address);
    }

    /**
     * 获得用户地址
     *
     * @param userId    用户编号
     * @param addressId 地址编号
     * @return 地址
     */
    private MemberAddressRespDTO getAddress(Long userId, Long addressId) {
        if (addressId != null) {
            return addressApi.getAddress(addressId, userId).getCheckedData();
        }
        return addressApi.getDefaultAddress(userId).getCheckedData();
    }

    /**
     * 计算订单价格
     *
     * @param userId          用户编号
     * @param settlementReqVO 结算信息
     * @return 订单价格
     */
    private TradePriceCalculateRespBO calculatePrice(Long userId, AppTradeOrderSettlementReqVO settlementReqVO) {
        // 1. 如果来自购物车，则获得购物车的商品
        List<CartDO> cartList = cartService.getCartList(userId,
                convertSet(settlementReqVO.getItems(), AppTradeOrderSettlementReqVO.Item::getCartId));

        // 2. 计算价格
        TradePriceCalculateReqBO calculateReqBO = TradeOrderConvert.INSTANCE.convert(userId, settlementReqVO, cartList);
        calculateReqBO.getItems().forEach(item -> Assert.isTrue(item.getSelected(), // 防御性编程，保证都是选中的
                "商品({}) 未设置为选中", item.getSkuId()));
        return tradePriceService.calculateOrderPrice(calculateReqBO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_CREATE)
    public TradeOrderDO createOrder(Long userId, AppTradeOrderCreateReqVO createReqVO) {
        requireWritableTenant();
        // 1.1 价格计算
        TradePriceCalculateRespBO calculateRespBO = calculatePrice(userId, createReqVO);
        return createCalculatedOrder(userId, createReqVO, calculateRespBO, null);
    }

    @Override
    public AppTradeOrderSettlementRespVO settlementInquiryOrder(Long userId, Long quoteId, Long addressId) {
        InquiryAcceptedQuoteRespDTO quote = getAcceptedInquiryQuote(userId, quoteId);
        MemberAddressRespDTO address = getAddress(userId, addressId);
        Assert.notNull(address, "地址({}) 不能为空", addressId);
        AppTradeOrderCreateReqVO createReqVO = buildInquiryOrderRequest(quote, address.getId(), null);
        TradePriceCalculateRespBO calculateRespBO = calculateInquiryPrice(userId, quote, createReqVO);
        requireSingleShop(calculateRespBO);
        wmsStockService.validateSkuCounts(calculateRespBO.getItems().stream().collect(
                java.util.stream.Collectors.toMap(TradePriceCalculateRespBO.OrderItem::getSkuId,
                        TradePriceCalculateRespBO.OrderItem::getCount, Math::addExact, LinkedHashMap::new)));
        return TradeOrderConvert.INSTANCE.convert(calculateRespBO, address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_CREATE)
    public TradeOrderDO createInquiryOrder(Long userId, AppTradeInquiryOrderCreateReqVO createReqVO) {
        requireWritableTenant();
        // 先由商品服务验证当前会员属于采购企业，再按报价做企业级幂等查询。
        // 不能按 userId 查询，否则同企业另一名采购员会撞上数据库唯一键却取不到原订单。
        InquiryAcceptedQuoteRespDTO quote = getAcceptedInquiryQuote(userId, createReqVO.getQuoteId());
        TradeOrderDO existing = tradeOrderMapper.selectByInquiryQuoteId(createReqVO.getQuoteId());
        if (existing != null) {
            validateInquiryOrderEnterprise(existing, quote);
            log.info("[createInquiryOrder][quoteId({}) requestKey({}) 已生成订单({})，直接返回]",
                    createReqVO.getQuoteId(), createReqVO.getRequestKey(), existing.getId());
            return existing;
        }
        AppTradeOrderCreateReqVO standardReqVO = buildInquiryOrderRequest(
                quote, createReqVO.getAddressId(), createReqVO.getRemark());
        TradePriceCalculateRespBO calculateRespBO = calculateInquiryPrice(userId, quote, standardReqVO);
        try {
            return createCalculatedOrder(userId, standardReqVO, calculateRespBO, quote);
        } catch (DuplicateKeyException duplicate) {
            // 数据库唯一键是跨实例并发下的最终防线。
            existing = tradeOrderMapper.selectByInquiryQuoteIdForUpdate(createReqVO.getQuoteId());
            if (existing != null) {
                validateInquiryOrderEnterprise(existing, quote);
                return existing;
            }
            throw duplicate;
        }
    }

    @Override
    public TradeOrderDO getInquiryOrder(Long userId, Long quoteId) {
        InquiryAcceptedQuoteRespDTO quote = getAcceptedInquiryQuote(userId, quoteId);
        TradeOrderDO order = tradeOrderMapper.selectByInquiryQuoteId(quoteId);
        if (order != null) {
            validateInquiryOrderEnterprise(order, quote);
        }
        return order;
    }

    private TradeOrderDO createCalculatedOrder(Long userId, AppTradeOrderCreateReqVO createReqVO,
                                               TradePriceCalculateRespBO calculateRespBO,
                                               InquiryAcceptedQuoteRespDTO inquiryQuote) {
        // 店铺归属只信任商品服务价格计算结果，不能使用客户端参数。现有订单状态和物流字段属于父单，
        // 因此首版必须按店分别下单；平台自营 0 与企业店铺混合也属于跨店。
        Long orderShopId = requireSingleShop(calculateRespBO);
        // 1.2 构建订单
        TradeOrderDO order = buildTradeOrder(userId, createReqVO, calculateRespBO);
        if (inquiryQuote != null) {
            order.setSourceType(TradeOrderDO.SOURCE_TYPE_INQUIRY)
                    .setInquiryId(inquiryQuote.getInquiryId())
                    .setInquiryQuoteId(inquiryQuote.getQuoteId())
                    .setInquiryQuoteVersion(inquiryQuote.getVersion())
                    .setBuyerEnterpriseId(inquiryQuote.getBuyerEnterpriseId());
        }
        List<TradeOrderItemDO> orderItems = buildTradeOrderItems(order, calculateRespBO);
        // 创建订单前再次读取 WMS，防止结算页停留期间继续沿用旧库存摘要。
        wmsStockService.validateAvailable(orderItems);
        // 平台自营也参与分组，避免自营和商家混合订单被错误归为单店铺订单。
        orderItems.forEach(item -> item.setShopId(item.getShopId() == null ? 0L : item.getShopId()));
        Set<Long> orderShopIds = convertSet(orderItems, TradeOrderItemDO::getShopId);
        if (orderShopIds.size() != 1 || !orderShopIds.contains(orderShopId)) {
            throw exception(ORDER_CREATE_FAIL_MULTI_SHOP);
        }
        order.setShopId(orderShopId);
        // 订单和订单项都保存店铺快照。后续店铺名称变更或列表跨服务查询时，
        // 仍能稳定显示下单时的归属；查询失败时使用可识别的降级名称，不阻断下单。
        fillShopSnapshots(order, orderItems);

        // 2. 订单创建前的逻辑
        tradeOrderHandlers.forEach(handler -> handler.beforeOrderCreate(order, orderItems));

        // 3. 保存订单
        tradeOrderMapper.insert(order);
        orderItems.forEach(orderItem -> orderItem.setOrderId(order.getId()));
        tradeOrderItemMapper.insertBatch(orderItems);

        // 4. 订单创建后的逻辑
        afterCreateTradeOrder(order, orderItems, createReqVO);
        return order;
    }

    private static void requireWritableTenant() {
        MallTenantWriteGuard.requireWritable(TenantContextHolder.getRequiredTenantId());
    }

    static Long requireSingleShop(TradePriceCalculateRespBO calculateRespBO) {
        if (calculateRespBO == null || CollUtil.isEmpty(calculateRespBO.getItems())) {
            throw exception(ORDER_CREATE_FAIL_MULTI_SHOP);
        }
        Set<Long> shopIds = calculateRespBO.getItems().stream()
                .map(item -> item.getShopId() == null ? 0L : item.getShopId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (shopIds.size() != 1) {
            throw exception(ORDER_CREATE_FAIL_MULTI_SHOP);
        }
        return shopIds.iterator().next();
    }

    private InquiryAcceptedQuoteRespDTO getAcceptedInquiryQuote(Long userId, Long quoteId) {
        CommonResult<InquiryAcceptedQuoteRespDTO> result = productInquiryApi.getAcceptedQuote(userId, quoteId);
        InquiryAcceptedQuoteRespDTO quote = result == null ? null : result.getCheckedData();
        if (quote == null) {
            throw exception(ORDER_INQUIRY_QUOTE_NOT_AVAILABLE);
        }
        validateAcceptedInquiryQuote(userId, quoteId, quote);
        return quote;
    }

    private AppTradeOrderCreateReqVO buildInquiryOrderRequest(InquiryAcceptedQuoteRespDTO quote,
                                                              Long addressId, String remark) {
        AppTradeOrderCreateReqVO reqVO = new AppTradeOrderCreateReqVO();
        reqVO.setAddressId(addressId);
        reqVO.setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        reqVO.setPointStatus(false);
        reqVO.setRemark(remark);
        List<AppTradeOrderSettlementReqVO.Item> items = new ArrayList<>(quote.getItems().size());
        for (InquiryAcceptedQuoteRespDTO.Item quoteItem : quote.getItems()) {
            AppTradeOrderSettlementReqVO.Item item = new AppTradeOrderSettlementReqVO.Item();
            item.setSkuId(quoteItem.getSkuId());
            item.setCount(quoteItem.getCount());
            items.add(item);
        }
        reqVO.setItems(items);
        return reqVO;
    }

    private TradePriceCalculateRespBO calculateInquiryPrice(Long userId, InquiryAcceptedQuoteRespDTO quote,
                                                            AppTradeOrderCreateReqVO createReqVO) {
        // 先走标准价格链路，取得经过上下架、库存、配送方式校验的可信商品元数据。
        TradePriceCalculateRespBO calculateRespBO = calculatePrice(userId, createReqVO);
        // 成交价只能来自商品服务返回的已采纳不可变快照，不能接受客户端价格。
        applyAcceptedInquiryQuotePrice(userId, quote, calculateRespBO);

        // 报价成交不参与活动包邮；在成交价基础上重新执行普通全局/模板运费规则。
        TradePriceCalculateReqBO deliveryReqBO = new TradePriceCalculateReqBO();
        deliveryReqBO.setUserId(userId);
        deliveryReqBO.setPointStatus(false);
        deliveryReqBO.setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        deliveryReqBO.setAddressId(createReqVO.getAddressId());
        deliveryPriceCalculator.calculate(deliveryReqBO, calculateRespBO);
        if (calculateRespBO.getPrice().getPayPrice() == null || calculateRespBO.getPrice().getPayPrice() <= 0) {
            throw exception(ORDER_INQUIRY_QUOTE_INVALID, "成交金额必须大于 0");
        }
        return calculateRespBO;
    }

    static void validateAcceptedInquiryQuote(Long userId, Long requestedQuoteId,
                                             InquiryAcceptedQuoteRespDTO quote) {
        if (quote.getQuoteId() == null || quote.getInquiryId() == null || quote.getBuyerEnterpriseId() == null
                || quote.getBuyerMemberId() == null
                || quote.getShopId() == null || quote.getVersion() == null
                || quote.getQuoteId() <= 0 || quote.getInquiryId() <= 0 || quote.getBuyerEnterpriseId() <= 0
                || quote.getShopId() <= 0
                || quote.getVersion() <= 0) {
            throw exception(ORDER_INQUIRY_QUOTE_INVALID, "报价主信息缺失");
        }
        if (!Objects.equals(requestedQuoteId, quote.getQuoteId())) {
            throw exception(ORDER_INQUIRY_QUOTE_INVALID, "返回的报价编号不一致");
        }
        if (!Objects.equals(userId, quote.getBuyerMemberId())) {
            throw exception(ORDER_INQUIRY_QUOTE_BUYER_MISMATCH);
        }
        if (CollUtil.isEmpty(quote.getItems())) {
            throw exception(ORDER_INQUIRY_QUOTE_INVALID, "报价商品不能为空");
        }
        Set<Long> inquiryItemIds = new java.util.HashSet<>();
        Set<Long> skuIds = new java.util.HashSet<>();
        for (InquiryAcceptedQuoteRespDTO.Item item : quote.getItems()) {
            if (item == null || item.getInquiryItemId() == null || item.getSkuId() == null
                    || item.getCount() == null || item.getUnitPrice() == null
                    || item.getInquiryItemId() <= 0 || item.getSkuId() <= 0
                    || item.getCount() <= 0 || item.getUnitPrice() <= 0) {
                throw exception(ORDER_INQUIRY_QUOTE_INVALID, "报价商品编号、数量或单价不合法");
            }
            if (!inquiryItemIds.add(item.getInquiryItemId()) || !skuIds.add(item.getSkuId())) {
                throw exception(ORDER_INQUIRY_QUOTE_INVALID, "报价商品存在重复行");
            }
        }
    }

    private static void validateInquiryOrderEnterprise(TradeOrderDO order,
                                                       InquiryAcceptedQuoteRespDTO quote) {
        if (!Objects.equals(order.getBuyerEnterpriseId(), quote.getBuyerEnterpriseId())) {
            throw exception(ORDER_INQUIRY_ORDER_ENTERPRISE_MISMATCH);
        }
    }

    static void applyAcceptedInquiryQuotePrice(Long userId, InquiryAcceptedQuoteRespDTO quote,
                                               TradePriceCalculateRespBO calculateRespBO) {
        validateAcceptedInquiryQuote(userId, quote.getQuoteId(), quote);
        if (calculateRespBO == null || calculateRespBO.getPrice() == null
                || calculateRespBO.getItems() == null
                || calculateRespBO.getItems().size() != quote.getItems().size()) {
            throw exception(ORDER_INQUIRY_QUOTE_INVALID, "商城商品与报价商品不一致");
        }
        Map<Long, InquiryAcceptedQuoteRespDTO.Item> quoteItemMap = new LinkedHashMap<>();
        quote.getItems().forEach(item -> quoteItemMap.put(item.getSkuId(), item));
        long quotedTotal = 0L;
        for (TradePriceCalculateRespBO.OrderItem orderItem : calculateRespBO.getItems()) {
            InquiryAcceptedQuoteRespDTO.Item quoteItem = quoteItemMap.get(orderItem.getSkuId());
            if (quoteItem == null || !Objects.equals(quoteItem.getCount(), orderItem.getCount())) {
                throw exception(ORDER_INQUIRY_QUOTE_INVALID, "商城商品规格或数量与报价不一致");
            }
            long itemTotal;
            try {
                itemTotal = Math.multiplyExact((long) quoteItem.getUnitPrice(), quoteItem.getCount());
                quotedTotal = Math.addExact(quotedTotal, itemTotal);
            } catch (ArithmeticException overflow) {
                throw exception(ORDER_INQUIRY_QUOTE_INVALID, "报价金额超出订单金额范围");
            }
            if (itemTotal > Integer.MAX_VALUE || quotedTotal > Integer.MAX_VALUE) {
                throw exception(ORDER_INQUIRY_QUOTE_INVALID, "报价金额超出订单金额范围");
            }
            long itemShopId = orderItem.getShopId() == null ? 0L : orderItem.getShopId();
            if (itemShopId != quote.getShopId()) {
                throw exception(ORDER_INQUIRY_QUOTE_SHOP_MISMATCH);
            }
            orderItem.setPrice(quoteItem.getUnitPrice())
                    .setDiscountPrice(0).setDeliveryPrice(0).setCouponPrice(0)
                    .setPointPrice(0).setUsePoint(0).setGivePoint(0).setVipPrice(0)
                    .setPayPrice((int) itemTotal);
        }
        calculateRespBO.setType(TradeOrderTypeEnum.NORMAL.getType());
        calculateRespBO.setCouponId(null);
        calculateRespBO.setCoupons(new ArrayList<>());
        calculateRespBO.setPromotions(new ArrayList<>());
        calculateRespBO.setUsePoint(0);
        calculateRespBO.setTotalPoint(0);
        calculateRespBO.setGivePoint(0);
        calculateRespBO.setBargainActivityId(null);
        calculateRespBO.setFreeDelivery(false);
        calculateRespBO.setGiveCouponTemplateCounts(new LinkedHashMap<>());
        TradePriceCalculatorHelper.recountAllPrice(calculateRespBO);
    }

    private TradeOrderDO buildTradeOrder(Long userId, AppTradeOrderCreateReqVO createReqVO,
                                         TradePriceCalculateRespBO calculateRespBO) {
        TradeOrderDO order = TradeOrderConvert.INSTANCE.convert(userId, createReqVO, calculateRespBO);
        order.setType(calculateRespBO.getType());
        order.setSourceType(TradeOrderDO.SOURCE_TYPE_NORMAL);
        order.setNo(tradeNoRedisDAO.generate(TradeNoRedisDAO.TRADE_ORDER_NO_PREFIX));
        order.setStatus(TradeOrderStatusEnum.UNPAID.getStatus());
        order.setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        order.setProductCount(getSumValue(calculateRespBO.getItems(), TradePriceCalculateRespBO.OrderItem::getCount, Integer::sum));
        order.setUserIp(getClientIP()).setTerminal(getTerminal());
        // 使用 + 赠送优惠券
        order.setGiveCouponTemplateCounts(calculateRespBO.getGiveCouponTemplateCounts());
        // 支付 + 退款信息
        order.setAdjustPrice(0).setPayStatus(false);
        paymentDeadline.initialize(order, LocalDateTime.now());
        order.setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus()).setRefundPrice(0);
        // 物流信息
        order.setDeliveryType(createReqVO.getDeliveryType());
        if (Objects.equals(createReqVO.getDeliveryType(), DeliveryTypeEnum.EXPRESS.getType())) {
            MemberAddressRespDTO address = addressApi.getAddress(createReqVO.getAddressId(), userId).getCheckedData();
            Assert.notNull(address, "地址({}) 不能为空", createReqVO.getAddressId()); // 价格计算时，已经计算
            order.setReceiverName(address.getName()).setReceiverMobile(address.getMobile())
                    .setReceiverAreaId(address.getAreaId()).setReceiverDetailAddress(address.getDetailAddress());
            TradeOrderAddressSnapshot.copy(address, order);
        } else if (Objects.equals(createReqVO.getDeliveryType(), DeliveryTypeEnum.PICK_UP.getType())) {
            order.setReceiverName(createReqVO.getReceiverName()).setReceiverMobile(createReqVO.getReceiverMobile());
            order.setPickUpVerifyCode(RandomUtil.randomNumbers(8)); // 随机一个核销码，长度为 8 位
        }
        return order;
    }

    private List<TradeOrderItemDO> buildTradeOrderItems(TradeOrderDO tradeOrderDO,
                                                        TradePriceCalculateRespBO calculateRespBO) {
        return TradeOrderConvert.INSTANCE.convertList(tradeOrderDO, calculateRespBO);
    }

    private void fillShopSnapshots(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        Map<Long, ProductShopRespDTO> shopMap = new HashMap<>();
        for (TradeOrderItemDO item : orderItems) {
            Long shopId = item.getShopId() == null ? 0L : item.getShopId();
            item.setShopId(shopId);
            if (shopId == 0L) {
                item.setShopName("平台自营");
                continue;
            }
            ProductShopRespDTO shop = shopMap.computeIfAbsent(shopId, this::getShopSafely);
            item.setShopName(resolveShopName(shopId, shop));
        }

        if (order.getShopId() == null) {
            // 生产库 trade_order.shop_id 为 NOT NULL DEFAULT 0。
            // 多店铺订单用 0 占位，店铺名称快照保留“多店铺”，避免插入 NULL 失败；
            // 订单项仍保存各自真实店铺编号，履约分组不受影响。
            order.setShopId(0L);
            order.setShopName("多店铺");
            return;
        }
        Long shopId = order.getShopId();
        if (shopId == 0L) {
            order.setShopName("平台自营");
            order.setShopLogoUrl(null);
            return;
        }
        ProductShopRespDTO shop = shopMap.computeIfAbsent(shopId, this::getShopSafely);
        order.setShopName(resolveShopName(shopId, shop));
        order.setShopLogoUrl(shop == null ? null : shop.getLogoUrl());
    }

    private ProductShopRespDTO getShopSafely(Long shopId) {
        try {
            CommonResult<ProductShopRespDTO> result = productShopApi.getShop(shopId);
            return result == null ? null : result.getCheckedData();
        } catch (RuntimeException ex) {
            log.warn("[fillShopSnapshots][shopId({}) 查询店铺失败]", shopId, ex);
            return null;
        }
    }

    private String resolveShopName(Long shopId, ProductShopRespDTO shop) {
        return shop != null && StrUtil.isNotBlank(shop.getName()) ? shop.getName() : "店铺 " + shopId;
    }

    /**
     * 订单创建后，执行后置逻辑
     * <p>
     * 例如说：优惠劵的扣减、积分的扣减、支付单的创建等等
     *
     * @param order       订单
     * @param orderItems  订单项
     * @param createReqVO 创建订单请求
     */
    private void afterCreateTradeOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems,
                                       AppTradeOrderCreateReqVO createReqVO) {
        // 1. 执行订单创建后置处理器
        tradeOrderHandlers.forEach(handler -> handler.afterOrderCreate(order, orderItems));

        // 2. 删除购物车商品
        Set<Long> cartIds = convertSet(createReqVO.getItems(), AppTradeOrderSettlementReqVO.Item::getCartId);
        if (CollUtil.isNotEmpty(cartIds)) {
            cartService.deleteCart(order.getUserId(), cartIds);
        }

        // 3. 生成预支付
        // 特殊情况：积分兑换时，可能支付金额为零
        if (order.getPayPrice() > 0) {
            createPayOrder(order, orderItems);
        }

        // 4. 插入订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), null, order.getStatus());

        // TODO @LeeYan9: 是可以思考下, 订单的营销优惠记录, 应该记录在哪里, 微信讨论起来!
    }

    private void createPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        workbenchChanged(order.getId());
        LocalDateTime proposedDeadline = paymentDeadline.startPayment(order, LocalDateTime.now());
        // 创建支付单，用于后续的支付
        PayOrderCreateReqDTO payOrderCreateReqDTO = TradeOrderConvert.INSTANCE.convert(
                order, orderItems, tradeOrderProperties);
        Long payOrderId = payOrderApi.createOrder(payOrderCreateReqDTO).getCheckedData();

        order.setPayOrderId(payOrderId);
        // 远程支付创建可能成功而本地事务回滚。重试必须复用那张支付单的原期限，不能重置。
        PayOrderRespDTO payOrder = paymentDeadline.getVerifiedPayOrder(order);
        if (payOrder.getExpireTime().isAfter(proposedDeadline)) {
            throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                    "支付截止与订单期限不一致，请核实原支付单");
        }
        order.setPayExpireTime(payOrder.getExpireTime());
        tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId()).setPayOrderId(payOrderId)
                .setPayExpireTime(order.getPayExpireTime()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_PAY)
    public void updateOrderPaid(Long id, Long payOrderId) {
        // 1.1 校验订单是否存在
        // 回调与前端主动同步必须读取同一行的最新已提交状态，避免并发重复执行后置处理。
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 1.2 校验订单已支付
        if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || order.getPayStatus()) {
            // 特殊：支付单号相同，直接返回，说明重复回调
            if (Boolean.TRUE.equals(order.getPayStatus()) && ObjectUtil.equals(order.getPayOrderId(), payOrderId)) {
                log.warn("[updateOrderPaid][order({}) 已支付，且支付单号相同({})，直接返回]", order, payOrderId);
                return;
            }
            log.error("[updateOrderPaid][order({}) 支付单不匹配({})，请进行处理！order 数据是：{}]",
                    id, payOrderId, JsonUtils.toJsonString(order));
            throw exception(ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR);
        }

        // 2. 校验支付订单的合法性
        PayOrderRespDTO payOrder = validatePayOrderPaid(order, payOrderId);

        // 3. 更新 TradeOrderDO 状态为已支付，等待发货
        int updateCount = tradeOrderMapper.updateByIdAndStatus(id, order.getStatus(),
                new TradeOrderDO().setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus()).setPayStatus(true)
                        .setPayTime(LocalDateTime.now()).setPayChannelCode(payOrder.getChannelCode()));
        if (updateCount == 0) {
            throw exception(ORDER_UPDATE_PAID_STATUS_NOT_UNPAID);
        }

        // 4. 执行 TradeOrderHandler 的后置处理
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderId(id);
        tradeOrderHandlers.forEach(handler -> handler.afterPayOrder(order, orderItems));

        // 5. 记录订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), TradeOrderStatusEnum.UNDELIVERED.getStatus());
        TradeOrderLogUtils.setUserInfo(order.getUserId(), UserTypeEnum.MEMBER.getValue());
    }

    @Override
    public void syncOrderPayStatusQuietly(Long id, Long payOrderId) {
        if (payOrderId == null) return;
        PayOrderRespDTO payOrder = payOrderApi.getOrder(payOrderId).getCheckedData();
        if (payOrder == null) {
            return;
        }
        if (!PayOrderStatusEnum.isSuccess(payOrder.getStatus())) {
            return;
        }
        try {
            getSelf().updateOrderPaid(id, payOrderId);
        } catch (Throwable e) {
            log.warn("[syncOrderPayStatusQuietly][id({}) payOrderId({}) 同步支付状态失败]", id, payOrderId, e);
        }
    }

    /**
     * 校验支付订单的合法性
     *
     * @param order      交易订单
     * @param payOrderId 支付订单编号
     * @return 支付订单
     */
    private PayOrderRespDTO validatePayOrderPaid(TradeOrderDO order, Long payOrderId) {
        // 1. 校验支付单是否存在
        PayOrderRespDTO payOrder = payOrderApi.getOrder(payOrderId).getCheckedData();
        if (payOrder == null) {
            log.error("[validatePayOrderPaid][order({}) payOrder({}) 不存在，请进行处理！]", order.getId(), payOrderId);
            throw exception(ORDER_NOT_FOUND);
        }

        // 2.1 校验支付单已支付
        if (!PayOrderStatusEnum.isSuccess(payOrder.getStatus())) {
            log.error("[validatePayOrderPaid][order({}) payOrder({}) 未支付，请进行处理！payOrder 数据是：{}]",
                    order.getId(), payOrderId, JsonUtils.toJsonString(payOrder));
            throw exception(ORDER_UPDATE_PAID_FAIL_PAY_ORDER_STATUS_NOT_SUCCESS);
        }
        // 2.2 校验支付金额一致
        if (ObjectUtil.notEqual(payOrder.getPrice(), order.getPayPrice())) {
            log.error("[validatePayOrderPaid][order({}) payOrder({}) 支付金额不匹配，请进行处理！order 数据是：{}，payOrder 数据是：{}]",
                    order.getId(), payOrderId, JsonUtils.toJsonString(order), JsonUtils.toJsonString(payOrder));
            throw exception(ORDER_UPDATE_PAID_FAIL_PAY_PRICE_NOT_MATCH);
        }
        // 2.2 校验支付订单匹配（二次）
        if (ObjectUtil.notEqual(payOrder.getMerchantOrderId(), order.getId().toString())) {
            log.error("[validatePayOrderPaid][order({}) 支付单不匹配({})，请进行处理！payOrder 数据是：{}]",
                    order.getId(), payOrderId, JsonUtils.toJsonString(payOrder));
            throw exception(ORDER_UPDATE_PAID_FAIL_PAY_ORDER_ID_ERROR);
        }
        return payOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_DELIVERY)
    public void deliveryOrder(TradeOrderDeliveryReqVO deliveryReqVO) {
        // 1.1 校验并获得交易订单（可发货）
        TradeOrderDO order = validateOrderDeliverable(deliveryReqVO.getId());
        // 1.2 校验 deliveryType 是否为快递，是快递才可以发货
        if (ObjectUtil.notEqual(order.getDeliveryType(), DeliveryTypeEnum.EXPRESS.getType())) {
            throw exception(ORDER_DELIVERY_FAIL_DELIVERY_TYPE_NOT_EXPRESS);
        }

        // 2. 更新订单为已发货
        TradeOrderDO updateOrderObj = new TradeOrderDO();
        // 2.1 快递发货
        DeliveryExpressDO express = null;
        if (ObjectUtil.notEqual(deliveryReqVO.getLogisticsId(), TradeOrderDO.LOGISTICS_ID_NULL)) {
            express = deliveryExpressService.validateDeliveryExpress(deliveryReqVO.getLogisticsId());
            updateOrderObj.setLogisticsId(deliveryReqVO.getLogisticsId()).setLogisticsNo(deliveryReqVO.getLogisticsNo());
        } else {
            // 2.2 无需发货
            updateOrderObj.setLogisticsId(0L).setLogisticsNo("");
        }
        // 方案二可能拆成多张 WMS 申请；必须全部执行完成，商城主订单才能确认发货。
        wmsOutboundService.validateCompleted(order.getId());
        // 执行更新
        updateOrderObj.setStatus(TradeOrderStatusEnum.DELIVERED.getStatus()).setDeliveryTime(LocalDateTime.now());
        int updateCount = tradeOrderMapper.updateByIdAndStatus(order.getId(), order.getStatus(), updateOrderObj);
        if (updateCount == 0) {
            throw exception(ORDER_DELIVERY_FAIL_STATUS_NOT_UNDELIVERED);
        }

        // 3. 记录订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), TradeOrderStatusEnum.DELIVERED.getStatus(),
                MapUtil.<String, Object>builder().put("expressName", express != null ? express.getName() : "")
                        .put("logisticsNo", express != null ? deliveryReqVO.getLogisticsNo() : "").build());

        // 4.1 发送站内信
        tradeMessageService.sendMessageWhenDeliveryOrder(new TradeOrderMessageWhenDeliveryOrderReqBO()
                .setOrderId(order.getId()).setUserId(order.getUserId()).setMessage(null));
        // 4.2 发送订阅消息
        getSelf().sendDeliveryOrderMessage(order, deliveryReqVO);

        // 5. 处理订单发货后逻辑
        order.setLogisticsId(updateOrderObj.getLogisticsId()).setLogisticsNo(updateOrderObj.getLogisticsNo())
                .setStatus(updateOrderObj.getStatus()).setDeliveryTime(updateOrderObj.getDeliveryTime());
        tradeOrderHandlers.forEach(handler -> handler.afterDeliveryOrder(order));
    }

    @Async
    public void sendDeliveryOrderMessage(TradeOrderDO order, TradeOrderDeliveryReqVO deliveryReqVO) {
        // 构建并发送模版消息
        Long orderId = order.getId();
        socialClientApi.sendWxaSubscribeMessage(new SocialWxaSubscribeMessageSendReqDTO()
                .setUserId(order.getUserId()).setUserType(UserTypeEnum.MEMBER.getValue())
                .setTemplateTitle(WXA_ORDER_DELIVERY)
                .setPage("pages/order/detail?id=" + orderId) // 订单详情页
                .addMessage("character_string3", String.valueOf(orderId)) // 订单编号
                .addMessage("phrase6", TradeOrderStatusEnum.DELIVERED.getName()) // 订单状态
                .addMessage("date4", LocalDateTimeUtil.formatNormal(LocalDateTime.now()))// 发货时间
                .addMessage("character_string5", StrUtil.blankToDefault(deliveryReqVO.getLogisticsNo(), "-")) // 快递单号
                .addMessage("thing9", order.getReceiverDetailAddress())).checkError(); // 收货地址
    }

    /**
     * 校验交易订单满足被发货的条件
     * <p>
     * 1. 交易订单未发货
     *
     * @param id 交易订单编号
     * @return 交易订单
     */
    private TradeOrderDO validateOrderDeliverable(Long id) {
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(id);
        if (order == null) throw exception(ORDER_NOT_FOUND);
        if (!TradeOrderStatusEnum.isUndelivered(order.getStatus()) || !Boolean.TRUE.equals(order.getPayStatus()))
            throw exception(ORDER_DELIVERY_FAIL_STATUS_NOT_UNDELIVERED);
        if (tradeOrderItemMapper.selectListByOrderId(id).stream()
                .anyMatch(item -> !Objects.equals(item.getAfterSaleStatus(), TradeOrderItemAfterSaleStatusEnum.NONE.getStatus())))
            throw exception(ORDER_DELIVERY_FAIL_REFUND_STATUS_NOT_NONE);
        // 1. 校验订单是否未发货
        if (ObjectUtil.notEqual(TradeOrderRefundStatusEnum.NONE.getStatus(), order.getRefundStatus())) {
            throw exception(ORDER_DELIVERY_FAIL_REFUND_STATUS_NOT_NONE);
        }

        // 2. 执行 TradeOrderHandler 前置处理
        tradeOrderHandlers.forEach(handler -> handler.beforeDeliveryOrder(order));
        return order;
    }

    @NotNull
    private TradeOrderDO validateOrderExists(Long id) {
        // 校验订单是否存在
        TradeOrderDO order = tradeOrderMapper.selectById(id);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_RECEIVE)
    public void receiveOrderByMember(Long userId, Long id) {
        // 校验并获得交易订单（可收货）
        TradeOrderDO order = validateOrderReceivable(userId, id);

        // 收货订单
        receiveOrder0(order);
    }

    @Override
    public int receiveOrderBySystem() {
        // 1. 查询过期的待支付订单
        LocalDateTime expireTime = minusTime(tradeOrderProperties.getReceiveExpireTime());
        List<TradeOrderDO> orders = tradeOrderMapper.selectListByStatusAndDeliveryTimeLt(
                TradeOrderStatusEnum.DELIVERED.getStatus(), expireTime);
        if (CollUtil.isEmpty(orders)) {
            return 0;
        }

        // 2. 遍历执行，逐个取消
        int count = 0;
        for (TradeOrderDO order : orders) {
            try {
                getSelf().receiveOrderBySystem(order);
                count++;
            } catch (Throwable e) {
                log.error("[receiveOrderBySystem][order({}) 自动收货订单异常]", order.getId(), e);
            }
        }
        return count;
    }

    /**
     * 自动收货单个订单
     *
     * @param order 订单
     */
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.SYSTEM_RECEIVE)
    public void receiveOrderBySystem(TradeOrderDO order) {
        receiveOrder0(order);
    }

    /**
     * 收货订单的核心实现
     *
     * @param order 订单
     */
    private void receiveOrder0(TradeOrderDO order) {
        // 1. 更新 TradeOrderDO 状态为已完成
        LocalDateTime receiveTime = LocalDateTime.now();
        int updateCount = tradeOrderMapper.updateByIdAndStatus(order.getId(), order.getStatus(),
                new TradeOrderDO().setStatus(TradeOrderStatusEnum.COMPLETED.getStatus()).setReceiveTime(receiveTime));
        if (updateCount == 0) {
            throw exception(ORDER_RECEIVE_FAIL_STATUS_NOT_DELIVERED);
        }

        // 2. 插入订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), TradeOrderStatusEnum.COMPLETED.getStatus());

        // 3. 执行 TradeOrderHandler 后置处理
        order.setStatus(TradeOrderStatusEnum.COMPLETED.getStatus()).setReceiveTime(receiveTime);
        tradeOrderHandlers.forEach(handler -> handler.afterReceiveOrder(order));
    }

    /**
     * 校验交易订单满足可售货的条件
     * <p>
     * 1. 交易订单待收货
     *
     * @param userId 用户编号
     * @param id     交易订单编号
     * @return 交易订单
     */
    private TradeOrderDO validateOrderReceivable(Long userId, Long id) {
        // 校验订单是否存在
        TradeOrderDO order = tradeOrderMapper.selectByIdAndUserId(id, userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 校验订单是否是待收货状态
        if (!TradeOrderStatusEnum.isDelivered(order.getStatus())) {
            throw exception(ORDER_RECEIVE_FAIL_STATUS_NOT_DELIVERED);
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_CANCEL)
    public void cancelOrderByMember(Long userId, Long id) {
        // 1.1 校验存在
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(id);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 1.2 校验状态
        if (ObjectUtil.notEqual(order.getStatus(), TradeOrderStatusEnum.UNPAID.getStatus())) {
            throw exception(ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
        }
        // 1.3 校验是否支持延迟（不允许取消）
        if (order.getPayOrderId() != null) {
            PayOrderRespDTO payOrder = paymentDeadline.getVerifiedPayOrder(order);
            if (PayOrderStatusEnum.isSuccessOrRefund(payOrder.getStatus())) {
                log.warn("[cancelOrderByMember][order({}) 支付单已支付（支付回调延迟），不支持取消]", order.getId());
                throw exception(ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
            }
            // A live payment tab must stop accepting money before inventory/points are released.
            // Unknown or in-flight channel results are not evidence of cancellation.
            if (!PayOrderStatusEnum.isClosed(payOrder.getStatus())) {
                if (!Boolean.TRUE.equals(payOrderApi.closeOrder(order.getPayOrderId(),
                        tradeOrderProperties.getPayAppKey(), String.valueOf(order.getId())).getCheckedData())) {
                    throw exception(ORDER_CANCEL_PAYMENT_UNCONFIRMED);
                }
                payOrder = paymentDeadline.getVerifiedPayOrder(order);
                if (!PayOrderStatusEnum.isClosed(payOrder.getStatus())) {
                    throw exception(ORDER_CANCEL_PAYMENT_UNCONFIRMED);
                }
            }
        }

        // 2. 取消订单
        cancelOrder0(order, TradeOrderCancelTypeEnum.MEMBER_CANCEL);
    }

    @Override
    public int cancelOrderBySystem() {
        // 游标分批查询：新单使用两个持久化期限，旧单逐笔核实真实支付期限，不批量回填。
        LocalDateTime scanTime = LocalDateTime.now();
        long afterId = 0L;
        int count = 0;
        while (true) {
            List<TradeOrderDO> orders = tradeOrderMapper.selectPaymentTimeoutCandidates(scanTime, afterId, 200);
            if (CollUtil.isEmpty(orders)) break;
            for (TradeOrderDO order : orders) {
                afterId = order.getId();
                try {
                    if (getSelf().cancelOrderBySystem(order)) count++;
                } catch (Throwable e) {
                    log.error("[cancelOrderBySystem][order({}) 过期订单异常，保留状态等待重试]", order.getId(), e);
                }
            }
        }
        return count;
    }

    /**
     * 自动取消单个订单
     *
     * @param order 订单
     */
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.SYSTEM_CANCEL)
    public boolean cancelOrderBySystem(TradeOrderDO candidate) {
        // 与 ERP 确认、支付回调争用同一行锁；不能依据扫描时的陈旧状态取消。
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(candidate.getId());
        if (order == null || !TradeOrderStatusEnum.isUnpaid(order.getStatus())
                || Boolean.TRUE.equals(order.getPayStatus())) return false;
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayOrderId() == null) {
            LocalDateTime legacyDeadline = paymentDeadline.legacyDeadlineWithoutPayOrder(order);
            if (legacyDeadline == null || now.isBefore(legacyDeadline)) return false;
        } else {
            PayOrderRespDTO pay = paymentDeadline.getVerifiedPayOrder(order);
            if (PayOrderStatusEnum.isSuccessOrRefund(pay.getStatus()) || now.isBefore(pay.getExpireTime())) return false;
            if (!PayOrderStatusEnum.isClosed(pay.getStatus())) {
                // 支付服务核实所有渠道；WAITING/未知/请求失败不能当作未扣款完成取消。
                var closed = payOrderApi.expireOrder(order.getPayOrderId(), tradeOrderProperties.getPayAppKey(),
                        String.valueOf(order.getId()));
                if (closed == null || !Boolean.TRUE.equals(closed.getCheckedData())) return false;
                pay = paymentDeadline.getVerifiedPayOrder(order);
                if (!PayOrderStatusEnum.isClosed(pay.getStatus())) return false;
            }
        }
        cancelOrder0(order, TradeOrderCancelTypeEnum.PAY_TIMEOUT);
        return true;
    }

    /**
     * 取消订单的核心实现
     *
     * @param order      订单
     * @param cancelType 取消类型
     */
    private void cancelOrder0(TradeOrderDO order, TradeOrderCancelTypeEnum cancelType) {
        // 先完成所有只读预检，避免已知历史活动商品缺失时先远程返积分、再在后续步骤失败。
        // 预检不能替代远程服务的持久化幂等，预检后出现超时/并发仍必须能安全重试。
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderId(order.getId());
        tradeOrderHandlers.forEach(handler -> handler.beforeCancelOrder(order, orderItems));
        // 1. 更新 TradeOrderDO 状态为已取消
        int updateCount = tradeOrderMapper.updateByIdAndStatus(order.getId(), order.getStatus(),
                new TradeOrderDO().setStatus(TradeOrderStatusEnum.CANCELED.getStatus())
                        .setCancelType(cancelType.getType()).setCancelTime(LocalDateTime.now()));
        if (updateCount == 0) {
            throw exception(ORDER_CANCEL_FAIL_STATUS_NOT_UNPAID);
        }

        // 2. 执行 TradeOrderHandler 的后置处理
        tradeOrderHandlers.forEach(handler -> handler.afterCancelOrder(order, orderItems));

        // 3. 增加订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), TradeOrderStatusEnum.CANCELED.getStatus());
    }

    /**
     * 如果金额全部被退款，则取消订单
     * 如果还有未被退款的金额，则无需取消订单
     *
     * @param order       订单
     * @param refundPrice 退款金额
     */
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_CANCEL_AFTER_SALE)
    public void cancelOrderByAfterSale(TradeOrderDO order, Integer refundPrice) {
        // 1. 更新订单
        if (refundPrice < order.getPayPrice()) {
            return;
        }
        // The refund caller already holds the parent lock. A normal RR snapshot can still
        // show another refunded item as NONE and compensate it again under an order-level key.
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderIdForUpdate(order.getId());
        tradeOrderHandlers.forEach(handler -> handler.beforeCancelOrder(order, orderItems));
        tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId())
                .setStatus(TradeOrderStatusEnum.CANCELED.getStatus())
                .setCancelType(TradeOrderCancelTypeEnum.AFTER_SALE_CLOSE.getType()).setCancelTime(LocalDateTime.now()));

        // 2. 执行 TradeOrderHandler 的后置处理
        tradeOrderHandlers.forEach(handler -> handler.afterCancelOrder(order, orderItems));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_DELETE)
    public void deleteOrder(Long userId, Long id) {
        // 1.1 校验存在
        TradeOrderDO order = tradeOrderMapper.selectOrderByIdAndUserId(id, userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 已采纳报价永久只能转换一张订单。保留取消订单记录，避免通过逻辑删除
        // 绕过 inquiry_quote_id 幂等边界，同时保留报价、订单与后续对账的追溯链。
        if (TradeOrderDO.SOURCE_TYPE_INQUIRY.equals(order.getSourceType())) {
            throw exception(ORDER_INQUIRY_ORDER_DELETE_FORBIDDEN);
        }
        // 1.2 校验状态
        if (ObjectUtil.notEqual(order.getStatus(), TradeOrderStatusEnum.CANCELED.getStatus())) {
            throw exception(ORDER_DELETE_FAIL_STATUS_NOT_CANCEL);
        }
        // 2. 删除订单
        tradeOrderMapper.deleteById(id);

        // 3. 记录日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus());
    }

    @Override
    public void updateOrderRemark(TradeOrderRemarkReqVO reqVO) {
        // 校验并获得交易订单
        validateOrderExists(reqVO.getId());

        // 更新
        TradeOrderDO order = TradeOrderConvert.INSTANCE.convert(reqVO);
        tradeOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_UPDATE_PRICE)
    public void updateOrderPrice(TradeOrderUpdatePriceReqVO reqVO) {
        // 1.1 校验交易订单
        TradeOrderDO order = validateOrderExists(reqVO.getId());
        // 询价成交价是采购方采纳的报价快照，不允许通用后台调价绕过报价版本和采纳流程。
        if (TradeOrderDO.SOURCE_TYPE_INQUIRY.equals(order.getSourceType())) {
            throw exception(ORDER_INQUIRY_ORDER_PRICE_CHANGE_FORBIDDEN);
        }
        if (order.getPayStatus()) {
            throw exception(ORDER_UPDATE_PRICE_FAIL_PAID);
        }
        // 1.2 校验调价金额是否变化
        if (order.getAdjustPrice() > 0) {
            throw exception(ORDER_UPDATE_PRICE_FAIL_ALREADY);
        }
        // 1.3 支付价格不能为 0
        int newPayPrice = order.getPayPrice() + reqVO.getAdjustPrice();
        if (newPayPrice <= 0) {
            throw exception(ORDER_UPDATE_PRICE_FAIL_PRICE_ERROR);
        }

        // 2. 更新订单
        tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId())
                .setAdjustPrice(reqVO.getAdjustPrice() + order.getAdjustPrice()).setPayPrice(newPayPrice));

        // 3. 更新 TradeOrderItem，需要做 adjustPrice 的分摊
        List<TradeOrderItemDO> orderOrderItems = tradeOrderItemMapper.selectListByOrderId(order.getId());
        List<Integer> dividePrices = TradePriceCalculatorHelper.dividePrice2(orderOrderItems, reqVO.getAdjustPrice());
        List<TradeOrderItemDO> updateItems = new ArrayList<>();
        for (int i = 0; i < orderOrderItems.size(); i++) {
            TradeOrderItemDO item = orderOrderItems.get(i);
            updateItems.add(new TradeOrderItemDO().setId(item.getId())
                    .setAdjustPrice(item.getAdjustPrice() + dividePrices.get(i))
                    .setPayPrice(item.getPayPrice() + dividePrices.get(i)));
        }
        tradeOrderItemMapper.updateBatch(updateItems);

        // 4. 更新支付订单
        payOrderApi.updatePayOrderPrice(order.getPayOrderId(), newPayPrice).checkError();

        // 5. 记录订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus(),
                MapUtil.<String, Object>builder().put("oldPayPrice", MoneyUtils.fenToYuanStr(order.getPayPrice()))
                        .put("adjustPrice", MoneyUtils.fenToYuanStr(reqVO.getAdjustPrice()))
                        .put("newPayPrice", MoneyUtils.fenToYuanStr(newPayPrice)).build());
    }

    @Override
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_UPDATE_ADDRESS)
    public void updateOrderAddress(TradeOrderUpdateAddressReqVO reqVO) {
        // 校验交易订单
        TradeOrderDO order = validateOrderExists(reqVO.getId());
        // 只有待发货状态，才可以修改订单收货地址；
        if (!TradeOrderStatusEnum.isUndelivered(order.getStatus())) {
            throw exception(ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED);
        }

        // 更新
        // Admin text-address changes have no verified point. Clear the old snapshot explicitly.
        int changed = tradeOrderMapper.updateAddressAndClearLocation(TradeOrderConvert.INSTANCE.convert(reqVO), order.getStatus());
        if (changed == 0) throw exception(ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED);

        // 记录订单日志
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_UPDATE_ADDRESS)
    public void updateOrderAddressByMember(Long userId, AppTradeOrderUpdateAddressReqVO reqVO) {
        updateOrderAddressByBuyer(userId, null, reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_UPDATE_ADDRESS)
    public void updateInquiryOrderAddressByEnterprise(Long memberId, Long buyerEnterpriseId,
                                                       AppTradeOrderUpdateAddressReqVO reqVO) {
        updateOrderAddressByBuyer(memberId, buyerEnterpriseId, reqVO);
    }

    private void updateOrderAddressByBuyer(Long actorMemberId, Long buyerEnterpriseId,
                                           AppTradeOrderUpdateAddressReqVO reqVO) {
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(reqVO.getId());
        boolean personalOrder = buyerEnterpriseId == null && order != null
                && Objects.equals(order.getUserId(), actorMemberId);
        boolean enterpriseInquiryOrder = buyerEnterpriseId != null && order != null
                && TradeOrderDO.SOURCE_TYPE_INQUIRY.equals(order.getSourceType())
                && Objects.equals(order.getBuyerEnterpriseId(), buyerEnterpriseId);
        if (!personalOrder && !enterpriseInquiryOrder) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (!TradeOrderStatusEnum.isUnpaid(order.getStatus()) || Boolean.TRUE.equals(order.getPayStatus())) {
            throw exception(ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_UNPAID);
        }
        if (!DeliveryTypeEnum.EXPRESS.getType().equals(order.getDeliveryType())) {
            throw exception(ORDER_UPDATE_ADDRESS_FAIL_NOT_EXPRESS);
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayOrderId() == null || order.getPayExpireTime() == null
                || !now.isBefore(order.getPayExpireTime())) {
            throw exception(ORDER_UPDATE_ADDRESS_FAIL_PAYMENT_UNAVAILABLE);
        }

        MemberAddressRespDTO address = addressApi.getAddress(reqVO.getAddressId(), actorMemberId).getCheckedData();
        if (address == null) {
            throw exception(ORDER_UPDATE_ADDRESS_FAIL_PAYMENT_UNAVAILABLE);
        }
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderId(order.getId());
        TradePriceCalculateRespBO delivery = calculateExistingOrderDelivery(actorMemberId, address.getId(), orderItems);
        int oldDeliveryPrice = ObjectUtil.defaultIfNull(order.getDeliveryPrice(), 0);
        int newDeliveryPrice = delivery.getPrice().getDeliveryPrice();
        int newPayPrice = order.getPayPrice() - oldDeliveryPrice + newDeliveryPrice;
        if (newPayPrice <= 0) {
            throw exception(ORDER_UPDATE_PRICE_FAIL_PRICE_ERROR);
        }

        // 支付模块在行锁下确认仍为待付款且没有任何支付尝试；金额不变也必须执行此校验。
        payOrderApi.updatePayOrderPrice(order.getPayOrderId(), newPayPrice).checkError();

        TradeOrderDO update = new TradeOrderDO().setId(order.getId())
                .setReceiverName(address.getName()).setReceiverMobile(address.getMobile())
                .setReceiverAreaId(address.getAreaId()).setReceiverDetailAddress(address.getDetailAddress())
                .setDeliveryPrice(newDeliveryPrice).setPayPrice(newPayPrice);
        TradeOrderAddressSnapshot.copy(address, update);
        int changed = buyerEnterpriseId == null
                ? tradeOrderMapper.updateUnpaidAddressAndPrice(update, actorMemberId)
                : tradeOrderMapper.updateEnterpriseInquiryUnpaidAddressAndPrice(update, buyerEnterpriseId);
        if (changed != 1) {
            throw exception(ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_UNPAID);
        }

        List<TradeOrderItemDO> itemUpdates = new ArrayList<>(orderItems.size());
        for (int i = 0; i < orderItems.size(); i++) {
            TradeOrderItemDO item = orderItems.get(i);
            int oldItemDelivery = ObjectUtil.defaultIfNull(item.getDeliveryPrice(), 0);
            int newItemDelivery = delivery.getItems().get(i).getDeliveryPrice();
            itemUpdates.add(new TradeOrderItemDO().setId(item.getId())
                    .setDeliveryPrice(newItemDelivery)
                    .setPayPrice(item.getPayPrice() - oldItemDelivery + newItemDelivery));
        }
        tradeOrderItemMapper.updateBatch(itemUpdates);
        workbenchChanged(order.getId());
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus(),
                MapUtil.<String, Object>builder()
                        .put("oldPayPrice", MoneyUtils.fenToYuanStr(order.getPayPrice()))
                        .put("newPayPrice", MoneyUtils.fenToYuanStr(newPayPrice)).build());
    }

    /** 保留已锁定的商品、优惠和积分分摊，仅按新地址及当前运费模板重新计算运费。 */
    private TradePriceCalculateRespBO calculateExistingOrderDelivery(Long userId, Long addressId,
                                                                      List<TradeOrderItemDO> orderItems) {
        Map<Long, ProductSkuRespDTO> skuMap = productSkuApi.getSkuMap(convertSet(orderItems, TradeOrderItemDO::getSkuId));
        Map<Long, ProductSpuRespDTO> spuMap = productSpuApi.getSpuMap(convertSet(orderItems, TradeOrderItemDO::getSpuId));
        List<TradePriceCalculateRespBO.OrderItem> items = new ArrayList<>(orderItems.size());
        for (TradeOrderItemDO source : orderItems) {
            ProductSkuRespDTO sku = skuMap.get(source.getSkuId());
            ProductSpuRespDTO spu = spuMap.get(source.getSpuId());
            if (sku == null || spu == null) {
                throw exception(ORDER_ITEM_NOT_FOUND);
            }
            int oldDelivery = ObjectUtil.defaultIfNull(source.getDeliveryPrice(), 0);
            items.add(new TradePriceCalculateRespBO.OrderItem()
                    .setSpuId(source.getSpuId()).setSkuId(source.getSkuId()).setCount(source.getCount()).setSelected(true)
                    .setPrice(source.getPrice()).setDiscountPrice(ObjectUtil.defaultIfNull(source.getDiscountPrice(), 0))
                    .setDeliveryPrice(0).setCouponPrice(ObjectUtil.defaultIfNull(source.getCouponPrice(), 0))
                    .setPointPrice(ObjectUtil.defaultIfNull(source.getPointPrice(), 0))
                    .setVipPrice(ObjectUtil.defaultIfNull(source.getVipPrice(), 0))
                    .setPayPrice(source.getPayPrice() - oldDelivery)
                    .setDeliveryTypes(spu.getDeliveryTypes()).setDeliveryTemplateId(spu.getDeliveryTemplateId())
                    .setWeight(sku.getWeight()).setVolume(sku.getVolume()));
        }
        TradePriceCalculateRespBO result = new TradePriceCalculateRespBO()
                .setItems(items).setPrice(new TradePriceCalculateRespBO.Price()).setFreeDelivery(false);
        TradePriceCalculatorHelper.recountAllPrice(result);
        deliveryPriceCalculator.calculate(new TradePriceCalculateReqBO().setUserId(userId)
                .setAddressId(addressId).setDeliveryType(DeliveryTypeEnum.EXPRESS.getType()), result);
        return result;
    }

    @Override
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_PICK_UP_RECEIVE)
    public void pickUpOrderByAdmin(Long userId, Long id) {
        getSelf().pickUpOrder(userId, tradeOrderMapper.selectById(id));
    }

    @Override
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.ADMIN_PICK_UP_RECEIVE)
    public void pickUpOrderByAdmin(Long userId, String pickUpVerifyCode) {
        getSelf().pickUpOrder(userId, tradeOrderMapper.selectOneByPickUpVerifyCode(pickUpVerifyCode));
    }

    @Override
    public TradeOrderDO getByPickUpVerifyCode(String pickUpVerifyCode) {
        return tradeOrderMapper.selectOneByPickUpVerifyCode(pickUpVerifyCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public void pickUpOrder(Long userId, TradeOrderDO order) {
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (ObjUtil.notEqual(DeliveryTypeEnum.PICK_UP.getType(), order.getDeliveryType())) {
            throw exception(ORDER_RECEIVE_FAIL_DELIVERY_TYPE_NOT_PICK_UP);
        }
        if (!TradeOrderStatusEnum.isUndelivered(order.getStatus())) {
            throw exception(ORDER_PICK_UP_FAIL_STATUS_NOT_UNDELIVERED);
        }
        // 情况一：如果是拼团订单，则校验拼团是否成功
        if (TradeOrderTypeEnum.isCombination(order.getType())) {
            CombinationRecordRespDTO combinationRecord = combinationRecordApi.getCombinationRecordByOrderId(
                    order.getUserId(), order.getId()).getCheckedData();
            if (!CombinationRecordStatusEnum.isSuccess(combinationRecord.getStatus())) {
                throw exception(ORDER_PICK_UP_FAIL_COMBINATION_NOT_SUCCESS);
            }
        }
        DeliveryPickUpStoreDO deliveryPickUpStore = pickUpStoreService.getDeliveryPickUpStore(order.getPickUpStoreId());
        if (deliveryPickUpStore == null
                || !CollUtil.contains(deliveryPickUpStore.getVerifyUserIds(), userId)) {
            throw exception(ORDER_PICK_UP_FAIL_NOT_VERIFY_USER);
        }

        wmsOutboundService.validateCompleted(order.getId());
        receiveOrder0(order);
    }

    // =================== Order Item ===================

    @Override
    public void updateOrderItemWhenAfterSaleCreate(Long id, Long afterSaleId) {
        // 更新订单项
        updateOrderItemAfterSaleStatus(id, TradeOrderItemAfterSaleStatusEnum.NONE.getStatus(),
                TradeOrderItemAfterSaleStatusEnum.APPLY.getStatus(), afterSaleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderItemWhenAfterSaleSuccess(Long id, Integer refundPrice) {
        // The hint identifies the parent only. It may establish an old MySQL RR snapshot;
        // all business decisions below must therefore use current reads under order -> item locks.
        TradeOrderItemDO hint = tradeOrderItemMapper.selectById(id);
        if (hint == null) throw exception(ORDER_ITEM_NOT_FOUND);
        TradeOrderDO order = tradeOrderMapper.selectByIdForUpdate(hint.getOrderId());
        if (order == null) throw exception(ORDER_NOT_FOUND);
        List<TradeOrderItemDO> currentItems = tradeOrderItemMapper.selectListByOrderIdForUpdate(order.getId());
        TradeOrderItemDO orderItem = currentItems.stream().filter(item -> Objects.equals(item.getId(), id))
                .findFirst().orElseThrow(() -> exception(ORDER_ITEM_NOT_FOUND));
        if (!Objects.equals(orderItem.getAfterSaleStatus(), TradeOrderItemAfterSaleStatusEnum.APPLY.getStatus()))
            throw exception(ORDER_ITEM_UPDATE_AFTER_SALE_STATUS_FAIL);
        if (refundPrice == null || refundPrice < 0 || orderItem.getPayPrice() == null
                || refundPrice > orderItem.getPayPrice() || order.getRefundPrice() == null
                || order.getRefundPrice() < 0 || order.getPayPrice() == null
                || order.getRefundPoint() == null || order.getRefundPoint() < 0
                || orderItem.getUsePoint() == null || orderItem.getUsePoint() < 0)
            throw exception(AFTER_SALE_CREATE_FAIL_REFUND_PRICE_ERROR);
        // Validate aggregate bounds before any remote compensation is invoked.
        int orderRefundPrice = Math.addExact(order.getRefundPrice(), refundPrice);
        int orderRefundPoint = Math.addExact(order.getRefundPoint(), orderItem.getUsePoint());
        if (orderRefundPrice > order.getPayPrice()) throw exception(AFTER_SALE_CREATE_FAIL_REFUND_PRICE_ERROR);

        // 1.1 Only the winning application may run compensation; duplicate callbacks do not add again.
        updateOrderItemAfterSaleStatus(id, TradeOrderItemAfterSaleStatusEnum.APPLY.getStatus(),
                TradeOrderItemAfterSaleStatusEnum.SUCCESS.getStatus(), null);
        orderItem.setAfterSaleStatus(TradeOrderItemAfterSaleStatusEnum.SUCCESS.getStatus());
        // 1.2 执行 TradeOrderHandler 的后置处理
        tradeOrderHandlers.forEach(handler -> handler.beforeCancelOrderItem(order, orderItem));
        tradeOrderHandlers.forEach(handler -> handler.afterCancelOrderItem(order, orderItem));

        // 2.1 更新订单的退款金额、积分
        Integer refundStatus = currentItems.stream().allMatch(item -> Objects.equals(item.getAfterSaleStatus(),
                TradeOrderItemAfterSaleStatusEnum.SUCCESS.getStatus())) ?
                TradeOrderRefundStatusEnum.ALL.getStatus() // 如果都售后成功，则需要取消订单
                : TradeOrderRefundStatusEnum.PART.getStatus();
        tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId())
                .setRefundStatus(refundStatus)
                .setRefundPrice(orderRefundPrice).setRefundPoint(orderRefundPoint));
        // Cancellation handlers must see the aggregate just persisted, not the pre-refund object.
        order.setRefundStatus(refundStatus).setRefundPrice(orderRefundPrice).setRefundPoint(orderRefundPoint);
        // 2.2 如果全部退款，则进行取消订单
        getSelf().cancelOrderByAfterSale(order, orderRefundPrice);
    }

    @Override
    public void updateOrderItemWhenAfterSaleCancel(Long id) {
        // 更新订单项
        updateOrderItemAfterSaleStatus(id, TradeOrderItemAfterSaleStatusEnum.APPLY.getStatus(),
                TradeOrderItemAfterSaleStatusEnum.NONE.getStatus(), null);
    }

    private void updateOrderItemAfterSaleStatus(Long id, Integer oldAfterSaleStatus, Integer newAfterSaleStatus,
                                                Long afterSaleId) {
        // 更新订单项
        int updateCount = tradeOrderItemMapper.updateAfterSaleStatus(id, oldAfterSaleStatus, newAfterSaleStatus, afterSaleId);
        if (updateCount <= 0) {
            throw exception(ORDER_ITEM_UPDATE_AFTER_SALE_STATUS_FAIL);
        }

    }

    /**
     * 判断指定订单的所有订单项，是不是都售后成功
     *
     * @param id 订单编号
     * @return 是否都售后成功
     */
    private boolean isAllOrderItemAfterSaleSuccess(Long id) {
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderId(id);
        return orderItems.stream().allMatch(orderItem -> Objects.equals(orderItem.getAfterSaleStatus(),
                TradeOrderItemAfterSaleStatusEnum.SUCCESS.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.MEMBER_COMMENT)
    public Long createOrderItemCommentByMember(Long userId, AppTradeOrderItemCommentCreateReqVO createReqVO) {
        // 1.1 先通过订单项 ID，查询订单项是否存在
        TradeOrderItemDO orderItem = tradeOrderItemMapper.selectByIdAndUserId(createReqVO.getOrderItemId(), userId);
        if (orderItem == null) {
            throw exception(ORDER_ITEM_NOT_FOUND);
        }
        // 1.2 校验订单相关状态
        TradeOrderDO order = tradeOrderMapper.selectOrderByIdAndUserId(orderItem.getOrderId(), userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (ObjectUtil.notEqual(order.getStatus(), TradeOrderStatusEnum.COMPLETED.getStatus())) {
            throw exception(ORDER_COMMENT_FAIL_STATUS_NOT_COMPLETED);
        }
        if (ObjectUtil.notEqual(order.getCommentStatus(), Boolean.FALSE)) {
            throw exception(ORDER_COMMENT_STATUS_NOT_FALSE);
        }

        // 2. 创建评价
        Long commentId = createOrderItemComment0(orderItem, createReqVO);
        workbenchChanged(order.getId());

        // 3. 如果订单项都评论了，则更新订单评价状态
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderId(order.getId());
        if (!anyMatch(orderItems, item -> Objects.equals(item.getCommentStatus(), Boolean.FALSE))) {
            tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId()).setCommentStatus(Boolean.TRUE)
                    .setFinishTime(LocalDateTime.now()));
            // 增加订单日志。注意：只有在所有订单项都评价后，才会增加
            TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus());
        }
        return commentId;
    }

    @Override
    public int createOrderItemCommentBySystem() {
        // 1. 查询过期的待支付订单
        LocalDateTime expireTime = minusTime(tradeOrderProperties.getCommentExpireTime());
        List<TradeOrderDO> orders = tradeOrderMapper.selectListByStatusAndReceiveTimeLt(
                TradeOrderStatusEnum.COMPLETED.getStatus(), expireTime, false);
        if (CollUtil.isEmpty(orders)) {
            return 0;
        }

        // 2. 遍历执行，逐个取消
        int count = 0;
        for (TradeOrderDO order : orders) {
            try {
                getSelf().createOrderItemCommentBySystemBySystem(order);
                count++;
            } catch (Throwable e) {
                log.error("[createOrderItemCommentBySystem][order({}) 过期订单异常]", order.getId(), e);
            }
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderCombinationInfo(Long orderId, Long activityId, Long combinationRecordId, Long headId) {
        tradeOrderMapper.updateById(
                new TradeOrderDO().setId(orderId).setCombinationActivityId(activityId)
                        .setCombinationRecordId(combinationRecordId).setCombinationHeadId(headId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelPaidOrder(Long userId, Long orderId, Integer cancelType) {
        // 1.1 这里校验下 cancelType 只允许拼团关闭；
        if (ObjUtil.notEqual(TradeOrderCancelTypeEnum.COMBINATION_CLOSE.getType(), cancelType)) {
            return;
        }
        // 1.2 检验订单存在
        TradeOrderDO order = tradeOrderMapper.selectOrderByIdAndUserId(orderId, userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // 1.3 校验订单是否支付
        if (!order.getPayStatus()) {
            throw exception(ORDER_CANCEL_PAID_FAIL, "已支付");
        }
        // 1.4 校验订单是否未退款
        if (ObjUtil.notEqual(TradeOrderRefundStatusEnum.NONE.getStatus(), order.getRefundStatus())) {
            throw exception(ORDER_CANCEL_PAID_FAIL, "未退款");
        }

        // 2.1 取消订单
        cancelOrder0(order, TradeOrderCancelTypeEnum.COMBINATION_CLOSE);
        // 2.2 创建退款单
        payRefundApi.createRefund(new PayRefundCreateReqDTO()
                .setAppKey(tradeOrderProperties.getPayAppKey())  // 支付应用
                .setUserIp(NetUtil.getLocalhostStr()) // 使用本机 IP，因为是服务器发起退款的
                .setUserId(order.getUserId()).setUserType(UserTypeEnum.MEMBER.getValue()) // 用户信息
                .setMerchantOrderId(String.valueOf(order.getId())) // 支付单号
                // 特殊：因为订单支持 AfterSale 单个售后退款，也支持整单退款，所以需要通过 order- 进行下区分
                //      具体可见 AfterSaleController 的 updateAfterSaleRefunded 方法
                .setMerchantRefundId("order-" + order.getId())
                .setReason(TradeOrderCancelTypeEnum.COMBINATION_CLOSE.getName()).setPrice(order.getPayPrice())).checkError(); // 价格信息
    }

    @Override
    public void updatePaidOrderRefunded(Long id, Long payRefundId) {
        PayRefundRespDTO payRefund = payRefundApi.getRefund(payRefundId).getCheckedData();
        if (payRefund == null) {
            throw exception(ORDER_UPDATE_PAID_ORDER_REFUNDED_FAIL_REFUND_NOT_FOUND);
        }
        // 特殊：因为在 cancelPaidOrder 已经进行订单的取消，所以这里必须退款成功！！！
        if (!PayRefundStatusEnum.isSuccess(payRefund.getStatus())) {
            throw exception(ORDER_UPDATE_PAID_ORDER_REFUNDED_FAIL_REFUND_STATUS_NOT_SUCCESS);
        }
    }

    @Override
    public void updateOrderGiveCouponIds(Long userId, Long orderId, List<Long> giveCouponIds) {
        // 1. 检验订单存在
        TradeOrderDO order = tradeOrderMapper.selectOrderByIdAndUserId(orderId, userId);
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }

        // 2. 更新订单赠送的优惠券编号列表
        tradeOrderMapper.updateById(new TradeOrderDO().setId(orderId).setGiveCouponIds(giveCouponIds));
    }

    /**
     * 创建单个订单的评论
     *
     * @param order 订单
     */
    @Transactional(rollbackFor = Exception.class)
    @TradeOrderLog(operateType = TradeOrderOperateTypeEnum.SYSTEM_COMMENT)
    public void createOrderItemCommentBySystemBySystem(TradeOrderDO order) {
        workbenchChanged(order.getId());
        // 1. 查询未评论的订单项
        List<TradeOrderItemDO> orderItems = tradeOrderItemMapper.selectListByOrderIdAndCommentStatus(
                order.getId(), Boolean.FALSE);
        if (CollUtil.isEmpty(orderItems)) {
            return;
        }

        // 2. 逐个评论
        for (TradeOrderItemDO orderItem : orderItems) {
            // 2.1 创建评价
            AppTradeOrderItemCommentCreateReqVO commentCreateReqVO = new AppTradeOrderItemCommentCreateReqVO()
                    .setOrderItemId(orderItem.getId()).setAnonymous(false).setContent("")
                    .setBenefitScores(5).setDescriptionScores(5);
            createOrderItemComment0(orderItem, commentCreateReqVO);

            // 2.2 更新订单项评价状态
            tradeOrderItemMapper.updateById(new TradeOrderItemDO().setId(orderItem.getId()).setCommentStatus(Boolean.TRUE));
        }

        // 3. 所有订单项都评论了，则更新订单评价状态
        tradeOrderMapper.updateById(new TradeOrderDO().setId(order.getId()).setCommentStatus(Boolean.TRUE)
                .setFinishTime(LocalDateTime.now()));
        // 增加订单日志。注意：只有在所有订单项都评价后，才会增加
        TradeOrderLogUtils.setOrderInfo(order.getId(), order.getStatus(), order.getStatus());
    }

    /**
     * 创建订单项的评论的核心实现
     *
     * @param orderItem   订单项
     * @param createReqVO 评论内容
     * @return 评论编号
     */
    private Long createOrderItemComment0(TradeOrderItemDO orderItem, AppTradeOrderItemCommentCreateReqVO createReqVO) {
        // 1. 创建评价
        ProductCommentCreateReqDTO productCommentCreateReqDTO = TradeOrderConvert.INSTANCE.convert04(createReqVO, orderItem);
        Long commentId = productCommentApi.createComment(productCommentCreateReqDTO).getCheckedData();

        // 2. 更新订单项评价状态
        tradeOrderItemMapper.updateById(new TradeOrderItemDO().setId(orderItem.getId()).setCommentStatus(Boolean.TRUE));
        return commentId;
    }

    // =================== 营销相关的操作 ===================

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     *
     * @return 自己
     */
    private TradeOrderUpdateServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

}
