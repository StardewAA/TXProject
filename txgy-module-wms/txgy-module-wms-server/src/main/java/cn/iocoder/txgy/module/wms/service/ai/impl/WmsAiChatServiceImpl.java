package cn.iocoder.txgy.module.wms.service.ai.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.wms.dal.dataobject.inventory.WmsInventoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.inventory.WmsInventoryHistoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemBrandDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryHistoryMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemBrandMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemCategoryMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemQualityReportMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsItemQualityStatusEnum;
import cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderTypeConstants;
import cn.iocoder.txgy.module.wms.service.ai.ChatContext;
import cn.iocoder.txgy.module.wms.service.ai.PendingConfirm;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiChatService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiDeleteResult;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataCreateService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataDeleteService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataStatusService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * WMS AI 全域问答 Service 实现
 *
 * <p>实现策略：
 * <ol>
 *   <li>用大模型把用户提问路由到数据域（item/category/brand/merchant/warehouse/
 *       inventory/flow/stats/greet/other），并抽取商品关键词、仓库限定、低库存阈值等；</li>
 *   <li>由 Java 调用各 Mapper 真实查询数据库；</li>
 *   <li>由 Java 把查询结果拼装成自然语言回复，数据全部来自真实库表，绝不编造。</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class WmsAiChatServiceImpl implements WmsAiChatService {

    @Value("${txgy.ai.siliconflow.api-key:}")
    private String apiKey;

    @Value("${txgy.ai.siliconflow.base-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String baseUrl;

    @Value("${txgy.ai.siliconflow.model:deepseek-ai/DeepSeek-V4-Flash}")
    private String model;

    private static final int LIST_DEFAULT_LIMIT = 10; // 列表类默认展示条数
    private static final int LIST_MAX_LIMIT = 30;     // 列表类展示上限

    /** 供应商类型 */
    private static final List<Integer> SUPPLIER_TYPES = Arrays.asList(
            WmsMerchantTypeEnum.SUPPLIER.getType(),
            WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType());
    /** 客户类型 */
    private static final List<Integer> CUSTOMER_TYPES = Arrays.asList(
            WmsMerchantTypeEnum.CUSTOMER.getType(),
            WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType());

    private static final Map<Integer, String> ORDER_TYPE_NAMES = new HashMap<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    static {
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_PRODUCTION, "生产入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_PURCHASE, "采购入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_RETURN, "退货入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_GIVE_BACK, "归还入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.SHIPMENT_RETURN, "退货出库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.SHIPMENT_SALE, "销售出库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.SHIPMENT_PRODUCTION, "生产出库");
    }

    @Resource
    private WmsItemMapper itemMapper;
    @Resource
    private WmsItemSkuMapper itemSkuMapper;
    @Resource
    private WmsItemCategoryMapper itemCategoryMapper;
    @Resource
    private WmsItemBrandMapper itemBrandMapper;
    @Resource
    private WmsMerchantMapper merchantMapper;
    @Resource
    private WmsWarehouseMapper warehouseMapper;
    @Resource
    private WmsInventoryMapper inventoryMapper;
    @Resource
    private WmsInventoryHistoryMapper inventoryHistoryMapper;
    @Resource
    private WmsItemQualityReportMapper itemQualityReportMapper;
    @Resource
    private WmsAiMasterDataCreateService masterDataCreateService;
    @Resource
    private WmsAiMasterDataDeleteService masterDataDeleteService;
    @Resource
    private WmsAiMasterDataStatusService masterDataStatusService;

    /** 权限校验服务（等价于 @PreAuthorize 中的 @ss），用于 AI 问答的数据域权限校验 */
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    /** 单用户会话上下文（用于指代消解），key 为调用方传入的 sessionId（暂无则用固定值） */
    private static final Map<String, ChatContext> CHAT_CONTEXTS = new ConcurrentHashMap<>();
    /** 删除待确认上下文 */
    private static final Map<String, PendingConfirm> PENDING_CONFIRMS = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * 意图类型 → 所需查询权限点。
     * <p>AI 问答只是"自然语言 → 既有查询接口"的等价转换，因此必须与直接调用对应接口
     * 保持同样的权限门槛，避免成为越权读取数据的后门。
     */
    private static final Map<String, String> TYPE_PERMISSIONS = new HashMap<>();

    static {
        TYPE_PERMISSIONS.put("item", "wms:item:query");
        TYPE_PERMISSIONS.put("category", "wms:item-category:query");
        TYPE_PERMISSIONS.put("category-items", "wms:item:query");
        TYPE_PERMISSIONS.put("brand", "wms:item-brand:query");
        TYPE_PERMISSIONS.put("merchant", "wms:merchant:query");
        TYPE_PERMISSIONS.put("warehouse", "wms:warehouse:query");
        TYPE_PERMISSIONS.put("inventory", "wms:inventory:query");
        TYPE_PERMISSIONS.put("flow", "wms:inventory-history:query");
        TYPE_PERMISSIONS.put("stats", "wms:inventory:query");
        TYPE_PERMISSIONS.put("quality", "wms:item-quality-report:query");
    }

    /**
     * 校验当前登录用户是否有权查询该意图对应的数据域。
     *
     * @param type 意图类型
     * @return 无权限时返回友好提示文案；有权限（或该意图无需数据域权限）时返回 null
     */
    private String checkChatPermission(String type) {
        if (StrUtil.isBlank(type)) {
            return null;
        }
        String required = TYPE_PERMISSIONS.get(type);
        if (required == null) {
            // greet / knowledge / other / 建档 / 删除等不读取受限数据，无需在此拦截
            return null;
        }
        if (SecurityFrameworkUtils.getLoginUser() == null) {
            return "登录状态已失效，请重新登录后再使用 AI 助手。";
        }
        if (!securityFrameworkService.hasPermission(required)) {
            log.warn("[chat] 权限不足，type={}, required={}", type, required);
            return "抱歉，您没有查询该数据的权限（需要权限：" + required + "）。请联系管理员为您的角色开通后再试。";
        }
        return null;
    }

    @Override
    public String chat(String text) {
        return chat(text, null);
    }

    @Override
    public String chat(String text, String sessionId) {
        String raw = StrUtil.trim(text);
        if (StrUtil.isBlank(raw)) {
            return capabilityIntro();
        }
        if (StrUtil.isBlank(apiKey)) {
            return "系统尚未配置大模型 API Key（txgy.ai.siliconflow.api-key），请联系管理员配置后再使用。";
        }
        // 1. 删除待确认处理：上一步要求确认删除时，优先识别用户是否确认/取消
        String sessionKey = resolveSessionKey(sessionId);
        PendingConfirm pendingConfirm = PENDING_CONFIRMS.get(sessionKey);
        if (pendingConfirm != null && !pendingConfirm.isExpired()) {
            String confirmReply = tryHandlePendingConfirm(raw, pendingConfirm, sessionKey);
            if (confirmReply != null) {
                return confirmReply;
            }
        }

        // 2. 路由 + 抽参（带上该会话的历史消息，让模型自己理解"它/那个"指的是什么）
        List<JSONObject> historyMessages = loadHistoryMessages(sessionKey);
        JSONObject intent;
        try {
            intent = parseIntent(raw, historyMessages);
        } catch (Exception ex) {
            log.warn("[chat] 意图解析失败，text={}", raw, ex);
            return capabilityIntro();
        }
        // 本地建档/删除/状态兜底：大模型对"帮我建档商品/删除分类/启用分类"这类口语化命令容易误判，
        // 优先用本地关键词规则抽取，命中则覆盖大模型结果
        JSONObject createItemFallback = createItemRouteFallback(raw);
        if (createItemFallback != null) {
            intent = createItemFallback;
        }
        String type = intent == null ? "" : StrUtil.nullToEmpty(intent.getStr("type")).trim();
        // 大模型偶尔会把库存/仓库/删除/分类/状态类问题误判成寒暄，此时用本地关键词规则补救，避免答非所问
        if ("".equals(type) || "greet".equals(type) || "other".equals(type)) {
            JSONObject fallback = keywordRouteFallback(raw);
            if (fallback != null) {
                intent = fallback;
                type = StrUtil.nullToEmpty(intent.getStr("type")).trim();
            }
        }
        // 3. 质检单/知识类问句拦截：避免"有哪些商品没有质检单"被当商品名落库
        if ("create-item".equals(type)) {
            String guardReply = querySentenceGuard(raw);
            if (guardReply != null) {
                return guardReply;
            }
        }
        // 4. 指代消解：上一条聊了某个主体，这条说"它/那个分类"时补全名称
        if (StrUtil.isNotBlank(type) && !"greet".equals(type) && !"other".equals(type)) {
            JSONObject resolved = resolveAnaphora(intent, type, sessionKey);
            if (resolved != null) {
                intent = resolved;
            }
        }
        // 3.5 权限校验：AI 问答不能成为绕过权限读取 WMS 数据的后门。
        // 按意图命中的数据域，要求当前登录用户具备该域对应的查询权限；
        // AI 只做"自然语言 → 既有查询"的等价转换，权限与直接调对应接口时完全一致。
        String deniedReply = checkChatPermission(type);
        if (deniedReply != null) {
            return deniedReply;
        }
        List<String> names = extractNames(intent);
        String warehouseWord = intent == null ? "" : StrUtil.nullToEmpty(intent.getStr("warehouse")).trim();
        BigDecimal threshold = parseDecimal(intent == null ? null : intent.get("threshold"));
        int limit = parseLimit(intent == null ? null : intent.get("limit"));

        try {
            String reply;
            switch (type) {
                case "item":
                    reply = buildItemReply(names, limit);
                    break;
                case "category":
                    reply = buildCategoryReply(names, limit);
                    break;
                case "category-items":
                    reply = buildCategoryItemsReply(names, limit);
                    break;
                case "brand":
                    reply = buildBrandReply(names, limit);
                    break;
                case "merchant":
                    reply = buildMerchantReply(names, intent == null ? "all" : StrUtil.nullToEmpty(intent.getStr("kind")).trim(), limit);
                    break;
                case "warehouse":
                    reply = buildWarehouseReply(names, limit);
                    break;
                case "inventory":
                    reply = buildInventoryReply(names, warehouseWord, threshold, limit);
                    break;
                case "flow":
                    reply = buildFlowReply(names, warehouseWord, limit);
                    break;
                case "stats":
                    reply = buildStatsReply();
                    break;
                case "quality":
                    reply = buildQualityReply(names, limit);
                    break;
                case "knowledge":
                    reply = buildKnowledgeReply();
                    break;
                case "create-category":
                case "create-brand":
                case "create-merchant":
                case "create-warehouse":
                case "create-item":
                    reply = masterDataCreateService.create(
                            type.substring("create-".length()),
                            intent == null ? new JSONObject() : intent);
                    break;
                case "delete-category":
                case "delete-brand":
                case "delete-merchant":
                case "delete-warehouse":
                case "delete-item": {
                    String domain = type.substring("delete-".length());
                    WmsAiDeleteResult deleteResult = masterDataDeleteService.tryDelete(
                            domain, intent == null ? new JSONObject() : intent);
                    if (deleteResult.isNeedConfirm()) {
                        PENDING_CONFIRMS.put(sessionKey, PendingConfirm.of(domain, deleteResult));
                    }
                    reply = deleteResult.getMessage();
                    break;
                }
                case "enable-category":
                    reply = masterDataStatusService.changeStatus("category", intent, true);
                    break;
                case "disable-category":
                    reply = masterDataStatusService.changeStatus("category", intent, false);
                    break;
                default:
                    reply = capabilityIntro();
            }
            // 5. 保存会话上下文（供下一轮指代消解）
            saveChatContext(sessionKey, raw, reply, type, names);
            return reply;
        } catch (Exception ex) {
            log.error("[chat] 问答处理异常，type={}，text={}", type, raw, ex);
            return "抱歉，查询 WMS 数据时出了点问题，请稍后再试。";
        }
    }

    // ==================== 数据域问答 ====================

    private String buildItemReply(List<String> names, int limit) {
        List<WmsItemDO> items = loadItems();
        List<WmsItemSkuDO> allSkus = loadSkusByItemIds(items.stream().map(WmsItemDO::getId).collect(Collectors.toList()));
        Map<Long, List<WmsItemSkuDO>> skusByItem = allSkus.stream()
                .collect(Collectors.groupingBy(WmsItemSkuDO::getItemId));
        Map<Long, String> categoryNames = loadCategoryNames();
        Map<Long, String> brandNames = loadBrandNames();

        List<WmsItemDO> matched = filterByNames(items, names, i -> i.getName() + " " + StrUtil.nullToEmpty(i.getCode()),
                limit, false);
        if (matched.isEmpty()) {
            return names.isEmpty() ? "商品管理中暂时还没有商品档案。"
                    : "【商品管理】中未找到与「" + String.join("、", names) + "」相关的商品。可换个说法，或告诉我你想做入库/出库，我先帮你建档再操作。";
        }
        List<String> lines = new ArrayList<>();
        lines.add("在【商品管理】中找到 " + matched.size() + " 个相关商品：");
        int index = 0;
        for (WmsItemDO item : matched) {
            index++;
            StringBuilder line = new StringBuilder("• ").append(item.getName());
            if (StrUtil.isNotBlank(item.getCode())) {
                line.append("（编码 ").append(item.getCode()).append("）");
            }
            if (item.getCategoryId() != null) {
                String cat = categoryNames.get(item.getCategoryId());
                if (StrUtil.isNotBlank(cat)) {
                    line.append("｜分类：").append(cat);
                }
            }
            if (item.getBrandId() != null) {
                String brand = brandNames.get(item.getBrandId());
                if (StrUtil.isNotBlank(brand)) {
                    line.append("｜品牌：").append(brand);
                }
            }
            if (StrUtil.isNotBlank(item.getUnit())) {
                line.append("｜单位：").append(item.getUnit());
            }
            List<WmsItemSkuDO> skus = skusByItem.getOrDefault(item.getId(), Collections.emptyList());
            if (!skus.isEmpty()) {
                String spec = skus.stream()
                        .map(s -> StrUtil.isNotBlank(s.getName()) ? s.getName()
                                : (StrUtil.isNotBlank(s.getCode()) ? s.getCode() : "SKU"))
                        .distinct()
                        .limit(3)
                        .collect(Collectors.joining("、"));
                line.append("｜规格：").append(spec);
                if (skus.size() > 3) {
                    line.append(" 等 ").append(skus.size()).append(" 种");
                }
            }
            lines.add(line.toString());
            if (index >= limit) {
                break;
            }
        }
        if (matched.size() > limit) {
            lines.add("…仅展示前 " + limit + " 条，可描述更具体名称缩小范围。");
        }
        return String.join("\n", lines);
    }

    private String buildCategoryReply(List<String> names, int limit) {
        List<WmsItemCategoryDO> cats = loadCategories();
        Map<Long, List<WmsItemCategoryDO>> byParent = cats.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        Map<Long, Long> childCount = cats.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId(),
                        Collectors.counting()));
        List<WmsItemDO> items = loadItems();
        Map<Long, Long> itemCountByCat = items.stream()
                .filter(i -> i.getCategoryId() != null)
                .collect(Collectors.groupingBy(WmsItemDO::getCategoryId, Collectors.counting()));

        if (names.isEmpty()) {
            List<WmsItemCategoryDO> roots = byParent.getOrDefault(0L, Collections.emptyList());
            if (roots.isEmpty()) {
                return "商品分类还没有建立任何一级分类。";
            }
            List<String> lines = new ArrayList<>();
            lines.add("商品分类目前共有 " + cats.size() + " 个，一级分类如下：");
            int index = 0;
            for (WmsItemCategoryDO c : roots) {
                if (index++ >= limit) {
                    break;
                }
                Long sub = childCount.getOrDefault(c.getId(), 0L);
                Long cnt = itemCountByCat.getOrDefault(c.getId(), 0L);
                lines.add("• " + c.getName() + (sub > 0 ? "（含 " + sub + " 个子类）" : "")
                        + (cnt > 0 ? "｜直接挂靠商品 " + cnt + " 个" : ""));
            }
            return String.join("\n", lines);
        }
        List<WmsItemCategoryDO> matched = cats.stream()
                .filter(c -> containsAny(c.getName(), names) || containsAny(StrUtil.nullToEmpty(c.getCode()), names))
                .collect(Collectors.toList());
        if (matched.isEmpty()) {
            return "未找到与「" + String.join("、", names) + "」相关的商品分类。";
        }
        List<String> lines = new ArrayList<>();
        lines.add("找到 " + matched.size() + " 个相关分类：");
        int index = 0;
        for (WmsItemCategoryDO c : matched) {
            if (index++ >= limit) {
                break;
            }
            String status = CommonStatusEnum.isEnable(c.getStatus()) ? "启用" : "停用";
            Long sub = childCount.getOrDefault(c.getId(), 0L);
            Long cnt = itemCountByCat.getOrDefault(c.getId(), 0L);
            lines.add("• " + c.getName() + "（" + status + "）"
                    + (sub > 0 ? "，含 " + sub + " 个子类" : "")
                    + (cnt > 0 ? "，直接挂靠商品 " + cnt + " 个" : ""));
        }
        return String.join("\n", lines);
    }

    private String buildBrandReply(List<String> names, int limit) {
        List<WmsItemBrandDO> brands = loadBrands();
        List<WmsItemDO> items = loadItems();
        Map<Long, Long> itemCount = items.stream()
                .filter(i -> i.getBrandId() != null)
                .collect(Collectors.groupingBy(WmsItemDO::getBrandId, Collectors.counting()));
        List<WmsItemBrandDO> matched = filterByNames(brands, names, b -> b.getName() + " " + StrUtil.nullToEmpty(b.getCode()),
                limit, false);
        if (matched.isEmpty()) {
            return names.isEmpty() ? "商品品牌库目前还没有品牌。"
                    : "未找到与「" + String.join("、", names) + "」相关的商品品牌。";
        }
        List<String> lines = new ArrayList<>();
        lines.add("共找到 " + matched.size() + " 个相关品牌：");
        int index = 0;
        for (WmsItemBrandDO b : matched) {
            if (index++ >= limit) {
                break;
            }
            Long cnt = itemCount.getOrDefault(b.getId(), 0L);
            lines.add("• " + b.getName() + (cnt > 0 ? "（下挂 " + cnt + " 个商品）" : ""));
        }
        return String.join("\n", lines);
    }

    private String buildMerchantReply(List<String> names, String kind, int limit) {
        List<Integer> types;
        String typeName;
        if ("supplier".equalsIgnoreCase(kind)) {
            types = SUPPLIER_TYPES;
            typeName = "供应商";
        } else if ("customer".equalsIgnoreCase(kind)) {
            types = CUSTOMER_TYPES;
            typeName = "客户";
        } else {
            types = null;
            typeName = "往来企业";
        }
        List<WmsMerchantDO> all = loadMerchants();
        List<WmsMerchantDO> pool = types == null ? all : all.stream()
                .filter(m -> types.contains(m.getType()))
                .collect(Collectors.toList());
        List<WmsMerchantDO> matched = filterByNames(pool, names,
                m -> m.getName() + " " + StrUtil.nullToEmpty(m.getCode()), limit, false);
        if (matched.isEmpty()) {
            return names.isEmpty() ? (typeName + "档案中暂无数据。")
                    : "未找到与「" + String.join("、", names) + "」相关的" + typeName + "。";
        }
        List<String> lines = new ArrayList<>();
        lines.add("找到 " + matched.size() + " 家相关" + typeName + "：");
        int index = 0;
        for (WmsMerchantDO m : matched) {
            if (index++ >= limit) {
                break;
            }
            StringBuilder line = new StringBuilder("• ").append(m.getName());
            if (StrUtil.isNotBlank(m.getCode())) {
                line.append("（").append(m.getCode()).append("）");
            }
            line.append("｜类型：").append(merchantTypeName(m.getType()));
            if (StrUtil.isNotBlank(m.getContact())) {
                line.append("｜联系人：").append(m.getContact());
            }
            if (StrUtil.isNotBlank(m.getMobile())) {
                line.append("｜手机：").append(m.getMobile());
            } else if (StrUtil.isNotBlank(m.getTelephone())) {
                line.append("｜电话：").append(m.getTelephone());
            }
            if (StrUtil.isNotBlank(m.getAddress())) {
                line.append("｜地址：").append(m.getAddress());
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    private String buildWarehouseReply(List<String> names, int limit) {
        List<WmsWarehouseDO> warehouses = loadWarehouses();
        List<WmsWarehouseDO> matched = filterByNames(warehouses, names,
                w -> w.getName() + " " + StrUtil.nullToEmpty(w.getCode()), limit, false);
        if (matched.isEmpty()) {
            return names.isEmpty() ? "仓库档案中暂无数据。"
                    : "未找到与「" + String.join("、", names) + "」相关的仓库。";
        }
        Map<Long, BigDecimal> whQuantity = totalQuantityByWarehouse();
        List<String> lines = new ArrayList<>();
        lines.add("找到 " + matched.size() + " 个仓库：");
        int index = 0;
        for (WmsWarehouseDO w : matched) {
            if (index++ >= limit) {
                break;
            }
            String qty = "";
            BigDecimal total = whQuantity.get(w.getId());
            if (total != null) {
                qty = "｜当前在库合计 " + num(total);
            }
            lines.add("• " + w.getName() + "（" + w.getCode() + "）" + qty);
        }
        return String.join("\n", lines);
    }

    /**
     * 库存问答：支持按商品名 / 按仓库 / 低库存预警（threshold）
     */
    private String buildInventoryReply(List<String> names, String warehouseWord, BigDecimal threshold, int limit) {
        List<WmsWarehouseDO> warehouses = loadWarehouses();

        // 1. 仓库限定
        WmsWarehouseDO wh = null;
        if (StrUtil.isNotBlank(warehouseWord)) {
            wh = fuzzyFirst(warehouses, warehouseWord, w -> w.getName() + " " + StrUtil.nullToEmpty(w.getCode()));
            if (wh == null) {
                return "未找到与「" + warehouseWord + "」匹配的仓库，请核对仓库名称。";
            }
        }

        // 2. 未指定商品时：仓库库存汇总 / 全局低库存预警 / 全局统计
        if (CollUtil.isEmpty(names)) {
            if (threshold != null) {
                return buildLowStockReply(wh, threshold, limit);
            }
            if (wh != null) {
                return buildWarehouseInventoryReply(wh, limit);
            }
            return buildStatsReply();
        }

        // 3. 解析商品 -> SKU
        List<WmsItemSkuDO> matchedSkus = findSkus(names);
        List<WmsItemDO> items = loadItems();
        Map<Long, WmsItemDO> itemMap = items.stream().collect(Collectors.toMap(WmsItemDO::getId, i -> i, (a, b) -> a));
        Map<Long, String> whNames = warehouses.stream()
                .collect(Collectors.toMap(WmsWarehouseDO::getId, WmsWarehouseDO::getName, (a, b) -> a));

        if (matchedSkus.isEmpty()) {
            return "【商品管理】中没有「" + String.join("、", names) + "」对应的商品档案，因此查不到库存。"
                    + "需要我帮你先建档再入库/出库吗？";
        }
        Map<Long, List<WmsItemSkuDO>> skusByItem = matchedSkus.stream()
                .collect(Collectors.groupingBy(WmsItemSkuDO::getItemId));
        Map<Long, List<WmsInventoryDO>> bySku = queryInventoryBySkus(matchedSkus.stream()
                .map(WmsItemSkuDO::getId).collect(Collectors.toList()), wh == null ? null : wh.getId())
                .stream().collect(Collectors.groupingBy(WmsInventoryDO::getSkuId));

        boolean lowMode = threshold != null;
        boolean needAllSku = names.size() <= 2; // 具体查某几个商品时，无库存也展示"0"
        List<String> lines = new ArrayList<>();
        String scopeText = wh == null ? "全部仓库" : wh.getName();
        if (lowMode) {
            lines.add("按「库存 ≤ " + num(threshold) + "」预警检查（范围：" + scopeText + "）：");
        } else if (StrUtil.isNotBlank(warehouseWord)) {
            lines.add("「" + scopeText + "」相关库存如下：");
        }

        int shown = 0;
        List<String> warned = new ArrayList<>();
        for (Map.Entry<Long, List<WmsItemSkuDO>> e : skusByItem.entrySet()) {
            WmsItemDO item = itemMap.get(e.getKey());
            for (WmsItemSkuDO sku : e.getValue()) {
                List<WmsInventoryDO> rows = bySku.getOrDefault(sku.getId(), Collections.emptyList());
                List<WmsInventoryDO> visible = rows;
                if (lowMode) {
                    visible = rows.stream()
                            .filter(r -> r.getQuantity().compareTo(threshold) <= 0)
                            .collect(Collectors.toList());
                } else {
                    visible = rows.stream()
                            .filter(r -> r.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                            .collect(Collectors.toList());
                }
                String label = itemName(item, sku);
                if (visible.isEmpty() && needAllSku && !lowMode) {
                    // 完全无库存
                    if (shown < limit) {
                        lines.add("• " + label + "：暂无库存");
                        shown++;
                    }
                    continue;
                }
                if (visible.isEmpty() && lowMode) {
                    // 预警模式：若该 SKU 有库存记录但都 > 阈值，不提示；无任何记录则提示为 0
                    if (rows.isEmpty()) {
                        lines.add("• " + label + "：无任何库存记录（视为 0）");
                        shown++;
                    }
                    continue;
                }
                if (visible.isEmpty()) {
                    continue;
                }
                String detail = visible.stream()
                        .map(r -> whNames.getOrDefault(r.getWarehouseId(), "仓" + r.getWarehouseId())
                                + " " + num(r.getQuantity()))
                        .collect(Collectors.joining("；"));
                String line = "• " + label + "：" + detail;
                if (lowMode && shown < limit) {
                    warned.add(line);
                } else if (shown < limit) {
                    lines.add(line);
                }
                shown++;
            }
        }
        if (lowMode && !warned.isEmpty()) {
            lines.addAll(warned.stream().limit(limit).collect(Collectors.toList()));
        }
        if (lowMode && warned.isEmpty() && lines.size() <= 1) {
            return "目前「" + String.join("、", names) + "」在" + scopeText + "没有发现库存低于 "
                    + num(threshold) + " 的规格，库存都还充足。";
        }
        if (!lowMode && shown == 0) {
            return "「" + String.join("、", names) + "」在" + scopeText + "当前均无在库（0 库存）。";
        }
        return String.join("\n", lines);
    }

    /**
     * 未指定商品时的全局/单仓低库存预警
     */
    private String buildLowStockReply(WmsWarehouseDO wh, BigDecimal threshold, int limit) {
        String scope = wh == null ? "全部仓库" : wh.getName();
        List<WmsInventoryDO> rows = queryInventoryBySkus(null, wh == null ? null : wh.getId());
        List<WmsInventoryDO> low = rows.stream()
                .filter(r -> r.getQuantity() != null && r.getQuantity().compareTo(threshold) <= 0)
                .sorted(Comparator.comparing(WmsInventoryDO::getQuantity))
                .collect(Collectors.toList());
        if (low.isEmpty()) {
            return "目前" + scope + "没有库存 ≤ " + num(threshold) + " 的在库记录，库存都还充足。";
        }
        List<WmsItemDO> items = loadItems();
        Map<Long, WmsItemDO> itemMap = items.stream()
                .collect(Collectors.toMap(WmsItemDO::getId, i -> i, (a, b) -> a));
        Map<Long, WmsItemSkuDO> skuMap = loadSkusByItemIds(items.stream().map(WmsItemDO::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(WmsItemSkuDO::getId, s -> s, (a, b) -> a));
        Map<Long, String> whNames = loadWarehouses().stream()
                .collect(Collectors.toMap(WmsWarehouseDO::getId, WmsWarehouseDO::getName, (a, b) -> a));

        List<String> lines = new ArrayList<>();
        lines.add("按「库存 ≤ " + num(threshold) + "」预警检查（范围：" + scope + "），共 "
                + low.size() + " 条记录，数量从低到高：");
        int shown = 0;
        for (WmsInventoryDO r : low) {
            if (shown >= limit) {
                break;
            }
            WmsItemSkuDO sku = skuMap.get(r.getSkuId());
            WmsItemDO item = sku == null ? null : itemMap.get(sku.getItemId());
            lines.add("• " + itemName(item, sku)
                    + "（" + whNames.getOrDefault(r.getWarehouseId(), "仓" + r.getWarehouseId())
                    + "）：" + num(r.getQuantity()));
            shown++;
        }
        if (low.size() > limit) {
            lines.add("…仅展示前 " + limit + " 条，可告诉我具体商品名继续查询。");
        }
        return String.join("\n", lines);
    }

    /**
     * 指定仓库的全部库存汇总（不指定商品名时）
     */
    private String buildWarehouseInventoryReply(WmsWarehouseDO wh, int limit) {
        List<WmsInventoryDO> rows = inventoryMapper.selectList(new LambdaQueryWrapperX<WmsInventoryDO>()
                .eq(WmsInventoryDO::getWarehouseId, wh.getId())
                .orderByDesc(WmsInventoryDO::getQuantity));
        if (CollUtil.isEmpty(rows)) {
            return "「" + wh.getName() + "」当前没有任何在库库存。";
        }
        rows = rows.stream().filter(r -> r.getQuantity() != null && r.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            return "「" + wh.getName() + "」当前没有任何在库库存。";
        }

        // SKU 表以 itemId 关联商品，先把全部商品+SKU 载入，再按 skuId 建索引
        List<WmsItemDO> items = loadItems();
        Map<Long, WmsItemDO> itemMap = items.stream()
                .collect(Collectors.toMap(WmsItemDO::getId, i -> i, (a, b) -> a));
        Map<Long, WmsItemSkuDO> skuMap = loadSkusByItemIds(items.stream().map(WmsItemDO::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(WmsItemSkuDO::getId, s -> s, (a, b) -> a));

        BigDecimal totalQty = rows.stream()
                .map(WmsInventoryDO::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> lines = new ArrayList<>();
        lines.add("「" + wh.getName() + "」共有 " + rows.size() + " 条在库记录，数量合计 " + num(totalQty) + "：");
        int shown = 0;
        for (WmsInventoryDO row : rows) {
            if (shown >= limit) {
                break;
            }
            WmsItemSkuDO sku = skuMap.get(row.getSkuId());
            WmsItemDO item = sku == null ? null : itemMap.get(sku.getItemId());
            lines.add("• " + itemName(item, sku) + "：" + num(row.getQuantity()));
            shown++;
        }
        if (rows.size() > limit) {
            lines.add("…仅展示前 " + limit + " 条，可告诉我具体商品名继续查询。");
        }
        return String.join("\n", lines);
    }

    /**
     * 出入库流水问答：某商品最近 N 笔入出库记录
     */
    private String buildFlowReply(List<String> names, String warehouseWord, int limit) {
        List<WmsInventoryHistoryDO> history = new ArrayList<>();
        List<WmsItemSkuDO> matchedSkus = findSkus(names);
        Map<Long, WmsItemDO> itemMap = loadItems().stream()
                .collect(Collectors.toMap(WmsItemDO::getId, i -> i, (a, b) -> a));
        Map<Long, String> skuCodeName = new HashMap<>();
        matchedSkus.forEach(s -> {
            WmsItemDO item = itemMap.get(s.getItemId());
            skuCodeName.put(s.getId(), itemName(item, s));
        });

        LambdaQueryWrapperX<WmsInventoryHistoryDO> query = new LambdaQueryWrapperX<WmsInventoryHistoryDO>();
        if (CollUtil.isNotEmpty(matchedSkus)) {
            query.in(WmsInventoryHistoryDO::getSkuId,
                    matchedSkus.stream().map(WmsItemSkuDO::getId).collect(Collectors.toList()));
        }
        WmsWarehouseDO wh = null;
        if (StrUtil.isNotBlank(warehouseWord)) {
            wh = fuzzyFirst(loadWarehouses(), warehouseWord, w -> w.getName() + " " + StrUtil.nullToEmpty(w.getCode()));
            if (wh == null) {
                return "未找到与「" + warehouseWord + "」匹配的仓库，请核对仓库名称。";
            }
            query.eq(WmsInventoryHistoryDO::getWarehouseId, wh.getId());
        }
        query.orderByDesc(WmsInventoryHistoryDO::getId).last("LIMIT " + Math.min(limit, 50));
        List<WmsInventoryHistoryDO> rows = inventoryHistoryMapper.selectList(query);
        if (CollUtil.isEmpty(rows)) {
            return (CollUtil.isEmpty(names) ? "暂无可查询" : "「" + String.join("、", names) + "」")
                    + "的出入库流水（近 30 天暂无记录，或该商品尚无单据）。";
        }
        Map<Long, String> whNameMap = loadWarehouses().stream()
                .collect(Collectors.toMap(WmsWarehouseDO::getId, WmsWarehouseDO::getName, (a, b) -> a));
        List<String> lines = new ArrayList<>();
        lines.add("最近 " + rows.size() + " 笔相关出入库记录：");
        int index = 0;
        for (WmsInventoryHistoryDO h : rows) {
            if (index++ >= limit) {
                break;
            }
            String typeName = ORDER_TYPE_NAMES.getOrDefault(h.getOrderType(), "库存调整");
            String sign = h.getQuantity() != null && h.getQuantity().compareTo(BigDecimal.ZERO) < 0 ? "" : "+";
            String skuText = skuCodeName.get(h.getSkuId());
            if (skuText == null) {
                skuText = "商品#" + h.getSkuId();
            }
            lines.add("• " + h.getOrderNo() + "（" + typeName + "）"
                    + (sign + (h.getQuantity() == null ? "0" : num(h.getQuantity()))) + " "
                    + skuText + " → "
                    + whNameMap.getOrDefault(h.getWarehouseId(), "仓" + h.getWarehouseId())
                    + (h.getCreateTime() == null ? "" : "　" + h.getCreateTime().format(TIME_FMT)));
        }
        return String.join("\n", lines);
    }

    private String buildStatsReply() {
        List<WmsWarehouseDO> warehouses = loadWarehouses();
        Map<Long, String> whNames = warehouses.stream()
                .collect(Collectors.toMap(WmsWarehouseDO::getId, WmsWarehouseDO::getName, (a, b) -> a));
        List<WmsInventoryDO> all = queryInventoryBySkus(null, null);
        if (all.isEmpty()) {
            return "当前系统没有任何库存数据（仓库都还是空的）。";
        }
        // 按仓库汇总行数
        Map<Long, List<WmsInventoryDO>> byWh = all.stream()
                .collect(Collectors.groupingBy(WmsInventoryDO::getWarehouseId));
        List<String> lines = new ArrayList<>();
        lines.add("系统共 " + all.size() + " 条在库记录（SKU×仓库），分布在 "
                + byWh.size() + " 个仓库：");
        List<Map.Entry<Long, List<WmsInventoryDO>>> sorted = new ArrayList<>(byWh.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().size(), a.getValue().size()));
        for (Map.Entry<Long, List<WmsInventoryDO>> e : sorted) {
            BigDecimal sum = e.getValue().stream()
                    .map(WmsInventoryDO::getQuantity)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lines.add("• " + whNames.getOrDefault(e.getKey(), "仓" + e.getKey())
                    + "：在库 SKU " + e.getValue().size() + " 个，数量合计 " + num(sum));
        }
        // 数量排行 TOP 商品
        Map<Long, BigDecimal> qtyBySku = all.stream().filter(r -> r.getQuantity() != null)
                .collect(Collectors.groupingBy(WmsInventoryDO::getSkuId,
                        Collectors.reducing(BigDecimal.ZERO, WmsInventoryDO::getQuantity, BigDecimal::add)));
        List<Map.Entry<Long, BigDecimal>> top = new ArrayList<>(qtyBySku.entrySet());
        top.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (!top.isEmpty()) {
            lines.add("数量最多的 SKU TOP" + Math.min(top.size(), 5) + "：");
            List<WmsItemSkuDO> skus = loadSkusByItemIds(loadItems().stream()
                    .map(WmsItemDO::getId).collect(Collectors.toList()));
            Map<Long, String> skuNameMap = skus.stream()
                    .collect(Collectors.toMap(WmsItemSkuDO::getId, s -> s.getName(), (a, b) -> a));
            for (int i = 0; i < Math.min(top.size(), 5); i++) {
                Map.Entry<Long, BigDecimal> e = top.get(i);
                lines.add("• " + skuNameMap.getOrDefault(e.getKey(), "SKU#" + e.getKey()) + "：" + num(e.getValue()));
            }
        }
        return String.join("\n", lines);
    }

    private String capabilityIntro() {
        return "你好，我是【天信管业 WMS】智能助手。我可以帮你查系统里的数据，例如：\n"
                + "• 商品档案：查某类商品/规格/条码，如「有没有鸡爪这个商品」\n"
                + "• 分类 / 品牌：如「商品分类有哪些」「XX 品牌下有什么商品」\n"
                + "• 往来企业：如「供应商有哪些」「查一下 XX 公司的电话」\n"
                + "• 仓库与库存：如「鸡爪库存还有多少」「低于 20 的库存预警」「金岩库都有什么」\n"
                + "• 出入库流水：如「鸡爪最近进过货吗」\n"
                + "也可以直接把「入库 / 出库 / 移库」的清单（文字或图片）发我建单。";
    }

    // ==================== 意图解析（大模型） ====================

    private JSONObject parseIntent(String text, List<JSONObject> historyMessages) throws Exception {
        String content = callModel(buildRouterPrompt(), text, historyMessages);
        if (StrUtil.isBlank(content)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(extractJson(content));
        } catch (Exception ex) {
            log.warn("[chat] 路由返回非 JSON：{}", content);
            return null;
        }
    }

    private String buildRouterPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是【天信管业 WMS 仓储系统】的智能路由。用户可能询问 WMS 数据，也可能要求新增/建立主数据档案（建档）。");
        prompt.append("请把用户意图归类并抽取参数，仅输出一个 JSON 对象，不要任何解释：\n");
        prompt.append("{\"type\":\"...\",\"names\":[\"...\"],\"name\":\"\",\"kind\":\"\",\"warehouse\":\"\",\"threshold\":null,\"limit\":null,"
                + "\"categoryName\":\"\",\"brandName\":\"\",\"unit\":\"\",\"specs\":[],\"barCode\":\"\",\"contact\":\"\",\"mobile\":\"\","
                + "\"telephone\":\"\",\"address\":\"\",\"email\":\"\",\"remark\":\"\"}\n");
        prompt.append("type 取值分两大类：\n");
        prompt.append("【查询类】type 取值：\n");
        prompt.append("- item：问商品档案（有哪些商品/有没有XX/规格/条码/编码）。names 填商品关键词；问“有哪些商品/全部”时 names=[]。\n");
        prompt.append("- category：问商品分类/类别/类目。names 填分类关键词；问“有哪些分类”时 names=[]。\n");
        prompt.append("- brand：问商品品牌。names 填品牌关键词。\n");
        prompt.append("- merchant：问往来企业/供应商/客户。kind 填 supplier=供应商、customer=客户、all=不明确；names 填企业名称关键词。\n");
        prompt.append("- warehouse：问仓库有哪些/仓库信息。names 填仓库关键词。\n");
        prompt.append("- inventory：问库存/还有多少/缺货/低库存预警。names 填商品关键词；warehouse 填仓库限定词（没有填空串）；threshold 仅在“低于/不足/少于/小于 N”时填数字否则 null。\n");
        prompt.append("- flow：问出入库流水/最近进货/出货。names 填商品关键词。\n");
        prompt.append("- stats：汇总统计（总库存/哪个仓最多/库存排行）。\n");
        prompt.append("- quality：问商品质检/质检单/质检报告（有没有质检单/哪些商品没质检/质检情况）。names 填商品关键词；问整体质检情况时 names=[]。\n");
        prompt.append("- category-items：问某个分类下有哪些商品。names 填分类名。\n");
        prompt.append("- knowledge：问这个助手能做什么/系统说明。\n");
        prompt.append("【建档类】仅当用户是明确的“新增/建档/建立/添加/录入”命令式请求时使用，注意区分“查询有没有XX”与“帮我新建XX”是两回事：\n");
        prompt.append("- create-item：新建商品档案。必须抽取 name=商品名（填到 name 字段，不要只放到 names 数组）；categoryName=用户说的分类名（没说留空）；unit=单位；brandName=品牌；specs=规格名数组（如 [\"DN200\",\"DN400\"]，用户给了才填，否则 []）；barCode/remark 若有则填。\n");
        prompt.append("- create-category：新建商品分类。抽取 name=分类名。\n");
        prompt.append("- create-brand：新建品牌。抽取 name=品牌名。\n");
        prompt.append("- create-warehouse：新建仓库。抽取 name=仓库名。\n");
        prompt.append("- create-merchant：新建往来企业。抽取 name=企业名；kind=supplier/customer/both；contact=联系人；mobile=手机；telephone=电话；address=地址；email/remark 若有则填。\n");
        prompt.append("其余情况 type=greet（寒暄）或 other（与 WMS 无关或无法归类）。\n");
        prompt.append("【多轮上下文】如果对话历史里已经聊过某个商品/分类/仓库，而本轮用户用了“它/这个/那个/还有呢/哪几个”等指代，"
                + "请结合历史把对应名称补进 names，不要反问用户。\n");
        prompt.append("【重要】判断“建档”还是“查询”：只要用户在疑问（有哪些/有没有/多少/是不是/吗），一律归入查询类，"
                + "绝不可归为 create-item。只有明确的命令式（新建/建档/添加/录入）才是建档。\n");
        prompt.append("规则：字段只能从用户原话或上下文抽取，没提到的字段一律留空字符串或 null，严禁编造。"
                + "names 保留用户原文中的实质名称词，不加量词。查询与建档都不得编造系统数据。\n");
        return prompt.toString();
    }

    /**
     * 本地关键词路由兜底：当大模型把库存/仓库/流水类问题误判为寒暄时使用，
     * 保证「查仓库库存」「低库存预警」这类问题至少有正确的结果入口。
     */
    private JSONObject keywordRouteFallback(String text) {
        String t = StrUtil.nullToEmpty(text);
        if (StrUtil.isBlank(t)) {
            return null;
        }
        // 1. 商品建档兜底：大模型有时把"帮我建档商品"误判为寒暄，直接用本地规则抽取
        JSONObject createItem = createItemRouteFallback(t);
        if (createItem != null) {
            return createItem;
        }
        // 2. 质检问句兜底：「哪些商品没有质检单」「质检情况」→ quality
        if (t.contains("质检")) {
            JSONObject q = new JSONObject(true);
            q.set("type", "quality");
            return q;
        }
        boolean stockWord = t.contains("库存") || t.contains("现货") || t.contains("还有多少")
                || t.contains("剩余") || t.contains("缺货") || t.contains("预警");
        boolean flowWord = t.contains("流水") || t.contains("进过货") || t.contains("出入库") || t.contains("进出");
        if (!stockWord && !flowWord) {
            return null;
        }

        // 1. 命中仓库名
        String warehouse = "";
        for (WmsWarehouseDO wh : loadWarehouses()) {
            if (StrUtil.isNotBlank(wh.getName()) && t.contains(wh.getName())) {
                warehouse = wh.getName();
                break;
            }
        }

        // 2. 阈值：低于/不足/少于/小于 N
        BigDecimal threshold = null;
        int ti = -1;
        for (String kw : new String[]{"低于", "不足", "少于", "小于"}) {
            int idx = t.indexOf(kw);
            if (idx >= 0 && (ti < 0 || idx < ti)) {
                ti = idx;
            }
        }
        if (ti >= 0) {
            String rest = t.substring(ti + 2);
            StringBuilder numSb = new StringBuilder();
            for (char c : rest.toCharArray()) {
                if (Character.isDigit(c)) {
                    numSb.append(c);
                } else if (numSb.length() > 0) {
                    break;
                }
            }
            if (numSb.length() > 0) {
                threshold = new BigDecimal(numSb.toString());
            }
        }

        // 3. 命中商品名/SKU 名作为商品关键词
        List<String> names = new ArrayList<>();
        if (!t.contains("仓库") || t.contains("库存")) {
            List<WmsItemDO> items = loadItems();
            for (WmsItemDO it : items) {
                if (StrUtil.isNotBlank(it.getName()) && t.contains(it.getName())) {
                    names.add(it.getName());
                    if (names.size() >= 2) {
                        break;
                    }
                }
            }
            if (names.isEmpty()) {
                List<WmsItemSkuDO> skus = loadSkusByItemIds(
                        items.stream().map(WmsItemDO::getId).collect(Collectors.toList()));
                for (WmsItemSkuDO sku : skus) {
                    if (StrUtil.isNotBlank(sku.getName()) && t.contains(sku.getName())) {
                        names.add(sku.getName());
                        if (names.size() >= 2) {
                            break;
                        }
                    }
                }
            }
        }

        JSONObject fb = new JSONObject(true);
        fb.set("type", flowWord && !stockWord ? "flow" : "inventory");
        fb.set("names", names);
        fb.set("warehouse", warehouse);
        fb.set("threshold", threshold);
        return fb;
    }

    /**
     * 本地商品建档路由兜底：识别"建档/新建/新增/创建商品"等关键词，
     * 并从用户原话中抽取名称、分类、单位、品牌等字段。
     */
    private JSONObject createItemRouteFallback(String text) {
        List<String> createWords = Arrays.asList("建档商品", "新建商品", "新增商品", "创建商品", "建商品", "加商品");
        if (!containsAny(text, createWords)) {
            return null;
        }
        JSONObject fb = new JSONObject(true);
        fb.set("type", "create-item");
        fb.set("name", extractCreateItemName(text));
        fb.set("categoryName", extractFieldAfterKey(text, "分类"));
        fb.set("unit", extractFieldAfterKey(text, "单位"));
        fb.set("brandName", extractFieldAfterKey(text, "品牌"));
        fb.set("specs", new JSONArray());
        fb.set("names", new JSONArray());
        return fb;
    }

    private String extractCreateItemName(String text) {
        String name = extractFieldAfterKey(text, "商品");
        if (StrUtil.isBlank(name)) {
            name = extractFieldAfterKey(text, "商品名称");
        }
        if (StrUtil.isBlank(name)) {
            name = extractFieldAfterKey(text, "补充");
        }
        return name;
    }

    /**
     * 从 text 中抽取 "key=值" / "key：值" / "key=值" 形式的值，
     * 遇到逗号、分号、空格或行尾结束。
     */
    private String extractFieldAfterKey(String text, String key) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(key)) {
            return "";
        }
        int idx = text.indexOf(key);
        if (idx < 0) {
            return "";
        }
        int sepIdx = -1;
        for (int i = idx + key.length(); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '=' || c == ':' || c == '：') {
                sepIdx = i;
                break;
            }
        }
        if (sepIdx < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = sepIdx + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ',' || c == '，' || c == ';' || c == '；' || c == '\n' || c == '\r') {
                break;
            }
            if (c == ' ') {
                if (sb.length() > 0) {
                    break;
                }
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    // ==================== 查询工具 ====================

    private List<WmsItemDO> loadItems() {
        return itemMapper.selectList(new LambdaQueryWrapperX<WmsItemDO>()
                .orderByDesc(WmsItemDO::getId).last("LIMIT 500"));
    }

    private List<WmsItemSkuDO> loadSkusByItemIds(Collection<Long> itemIds) {
        if (CollUtil.isEmpty(itemIds)) {
            return Collections.emptyList();
        }
        return itemSkuMapper.selectList(new LambdaQueryWrapperX<WmsItemSkuDO>()
                .in(WmsItemSkuDO::getItemId, itemIds)
                .orderByAsc(WmsItemSkuDO::getId));
    }

    private List<WmsItemCategoryDO> loadCategories() {
        return itemCategoryMapper.selectList(new LambdaQueryWrapperX<WmsItemCategoryDO>()
                .orderByAsc(WmsItemCategoryDO::getSort).orderByAsc(WmsItemCategoryDO::getId));
    }

    private List<WmsItemBrandDO> loadBrands() {
        return itemBrandMapper.selectList(new LambdaQueryWrapperX<WmsItemBrandDO>()
                .orderByDesc(WmsItemBrandDO::getId));
    }

    private List<WmsMerchantDO> loadMerchants() {
        return merchantMapper.selectList(new LambdaQueryWrapperX<WmsMerchantDO>()
                .orderByDesc(WmsMerchantDO::getId).last("LIMIT 500"));
    }

    private List<WmsWarehouseDO> loadWarehouses() {
        return warehouseMapper.selectSimpleList();
    }

    private Map<Long, String> loadCategoryNames() {
        List<WmsItemCategoryDO> cats = loadCategories();
        Map<Long, WmsItemCategoryDO> byId = cats.stream()
                .collect(Collectors.toMap(WmsItemCategoryDO::getId, c -> c, (a, b) -> a));
        Map<Long, String> result = new HashMap<>();
        for (WmsItemCategoryDO c : cats) {
            String name = c.getName();
            WmsItemCategoryDO parent = c.getParentId() == null ? null : byId.get(c.getParentId());
            if (parent != null) {
                name = parent.getName() + " / " + name;
            }
            result.put(c.getId(), name);
        }
        return result;
    }

    private Map<Long, String> loadBrandNames() {
        return loadBrands().stream()
                .collect(Collectors.toMap(WmsItemBrandDO::getId, WmsItemBrandDO::getName, (a, b) -> a));
    }

    /**
     * 商品关键词 -> 匹配到的 SKU 列表（SKU 名/条码 或 商品名/编码）
     */
    private List<WmsItemSkuDO> findSkus(List<String> names) {
        if (CollUtil.isEmpty(names)) {
            return Collections.emptyList();
        }
        List<WmsItemDO> items = loadItems();
        List<WmsItemSkuDO> allSkus = loadSkusByItemIds(items.stream().map(WmsItemDO::getId).collect(Collectors.toList()));
        Map<Long, WmsItemDO> itemMap = items.stream()
                .collect(Collectors.toMap(WmsItemDO::getId, i -> i, (a, b) -> a));
        Set<Long> matched = new LinkedHashSet<>();
        for (WmsItemSkuDO sku : allSkus) {
            WmsItemDO item = itemMap.get(sku.getItemId());
            String hay = StrUtil.nullToEmpty(sku.getName()) + " " + StrUtil.nullToEmpty(sku.getCode())
                    + " " + StrUtil.nullToEmpty(sku.getBarCode())
                    + " " + (item == null ? "" : (StrUtil.nullToEmpty(item.getName())
                    + " " + StrUtil.nullToEmpty(item.getCode())));
            if (containsAny(hay, names)) {
                matched.add(sku.getId());
            }
        }
        if (matched.isEmpty()) {
            return Collections.emptyList();
        }
        return allSkus.stream().filter(s -> matched.contains(s.getId())).collect(Collectors.toList());
    }

    private List<WmsInventoryDO> queryInventoryBySkus(Collection<Long> skuIds, Long warehouseId) {
        LambdaQueryWrapperX<WmsInventoryDO> query = new LambdaQueryWrapperX<WmsInventoryDO>()
                .inIfPresent(WmsInventoryDO::getSkuId, skuIds)
                .eqIfPresent(WmsInventoryDO::getWarehouseId, warehouseId);
        query.orderByAsc(WmsInventoryDO::getWarehouseId).orderByAsc(WmsInventoryDO::getSkuId);
        if (CollUtil.isNotEmpty(skuIds) || warehouseId != null) {
            return inventoryMapper.selectList(query);
        }
        // 全量（用于统计），最多取 2 万行
        return inventoryMapper.selectList(query.last("LIMIT 20000"));
    }

    private Map<Long, BigDecimal> totalQuantityByWarehouse() {
        List<WmsInventoryDO> all = queryInventoryBySkus(null, null);
        return all.stream()
                .filter(r -> r.getQuantity() != null)
                .collect(Collectors.groupingBy(WmsInventoryDO::getWarehouseId,
                        Collectors.reducing(BigDecimal.ZERO, WmsInventoryDO::getQuantity, BigDecimal::add)));
    }

    /**
     * 内存模糊筛选：关键词为空返回前 limit 个；否则任一关键词包含即命中
     */
    private <T> List<T> filterByNames(List<T> source, List<String> names,
                                      Function<T, String> textFn, int limit, boolean newestFirst) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (CollUtil.isEmpty(names)) {
            return source.stream().limit(limit).collect(Collectors.toList());
        }
        return source.stream()
                .filter(t -> containsAny(StrUtil.nullToEmpty(textFn.apply(t)), names))
                .limit(Math.max(limit, 1))
                .collect(Collectors.toList());
    }

    private <T> T fuzzyFirst(List<T> source, String keyword, Function<T, String> textFn) {
        if (source == null || StrUtil.isBlank(keyword)) {
            return null;
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        for (T t : source) {
            String txt = StrUtil.nullToEmpty(textFn.apply(t)).toLowerCase(Locale.ROOT);
            if (txt.contains(kw) || kw.contains(txt)) {
                return t;
            }
        }
        // 再宽容一次：按逐字符是否全部命中（容忍个别错字）
        for (T t : source) {
            String txt = StrUtil.nullToEmpty(textFn.apply(t)).toLowerCase(Locale.ROOT);
            boolean all = kw.chars().allMatch(ch -> txt.indexOf((char) ch) >= 0);
            if (all) {
                return t;
            }
        }
        return null;
    }

    private boolean containsAny(String text, List<String> names) {
        if (StrUtil.isBlank(text) || CollUtil.isEmpty(names)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String n : names) {
            if (StrUtil.isNotBlank(n) && lower.contains(n.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractNames(JSONObject intent) {
        List<String> names = new ArrayList<>();
        if (intent == null) {
            return names;
        }
        JSONArray arr = intent.getJSONArray("names");
        if (arr != null) {
            for (Object o : arr) {
                String s = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(o)));
                if (StrUtil.isNotBlank(s)) {
                    names.add(s);
                }
            }
        }
        return names;
    }

    private int parseLimit(Object v) {
        BigDecimal d = parseDecimal(v);
        if (d == null) {
            return LIST_DEFAULT_LIMIT;
        }
        int n = Math.max(1, Math.min(d.intValue(), LIST_MAX_LIMIT));
        return n;
    }

    private BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return null;
        }
        String s = StrUtil.trim(v.toString());
        if (StrUtil.isBlank(s) || "null".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private String itemName(WmsItemDO item, WmsItemSkuDO sku) {
        StringBuilder sb = new StringBuilder();
        if (item != null && StrUtil.isNotBlank(item.getName())) {
            sb.append(item.getName());
        }
        if (sku != null && StrUtil.isNotBlank(sku.getName())) {
            if (sb.length() > 0) {
                sb.append('·');
            }
            sb.append(sku.getName());
        }
        if (sb.length() == 0) {
            sb.append("SKU").append(sku == null ? "?" : sku.getId());
        }
        if (item != null && StrUtil.isNotBlank(item.getUnit())) {
            sb.append('(').append(item.getUnit()).append(')');
        }
        return sb.toString();
    }

    private String merchantTypeName(Integer type) {
        if (type == null) {
            return "未设置";
        }
        return Arrays.stream(WmsMerchantTypeEnum.values())
                .filter(e -> Objects.equals(e.getType(), type))
                .map(WmsMerchantTypeEnum::getName)
                .findFirst()
                .orElse(String.valueOf(type));
    }

    private String num(BigDecimal d) {
        if (d == null) {
            return "0";
        }
        return d.stripTrailingZeros().toPlainString();
    }

    // ==================== 会话上下文（多轮对话 + 指代消解） ====================

    /**
     * 解析会话 key：优先使用调用方传入的 sessionId，保证不同用户/不同会话互不干扰；
     * 未传时按当前登录用户隔离，避免所有人共享同一份上下文互相串话。
     */
    private String resolveSessionKey(String sessionId) {
        if (StrUtil.isNotBlank(sessionId)) {
            return StrUtil.trim(sessionId);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return userId == null ? "anonymous" : "user-" + userId;
    }

    /**
     * 取该会话的历史消息（用于多轮上下文），同时清理已过期会话。
     */
    private List<JSONObject> loadHistoryMessages(String sessionKey) {
        purgeExpiredContexts();
        ChatContext ctx = CHAT_CONTEXTS.get(sessionKey);
        if (ctx == null || ctx.isExpired()) {
            return Collections.emptyList();
        }
        List<JSONObject> history = ctx.historyMessages();
        return history == null ? Collections.emptyList() : new ArrayList<>(history);
    }

    /**
     * 保存本轮上下文：既记录指代消解所需的"上一条主体"，
     * 也把这一轮问答追加进历史消息，供下一轮大模型直接理解上下文。
     */
    private void saveChatContext(String sessionKey, String raw, String reply, String type, List<String> names) {
        if (StrUtil.isBlank(sessionKey)) {
            return;
        }
        ChatContext ctx = CHAT_CONTEXTS.get(sessionKey);
        if (ctx == null || ctx.isExpired()) {
            ctx = ChatContext.of(raw, reply, type, names);
        } else {
            ctx.setLastText(raw);
            ctx.setLastType(type);
            ctx.setLastNames(names);
        }
        ctx.appendTurn(raw, reply);
        CHAT_CONTEXTS.put(sessionKey, ctx);
    }

    /** 清理过期会话，防止内存无限增长 */
    private void purgeExpiredContexts() {
        if (CHAT_CONTEXTS.size() < 64) {
            return;
        }
        CHAT_CONTEXTS.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isExpired());
        PENDING_CONFIRMS.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isExpired());
    }

    /**
     * 指代消解：用户说"它/这个/那个/哪几个"时，把意图类型与主体名称补全为上一条的内容。
     *
     * @return 补全后的 intent（原地修改为主，返回同一对象）；无法消解时返回 null
     */
    private JSONObject resolveAnaphora(JSONObject intent, String type, String sessionKey) {
        if (intent == null) {
            return null;
        }
        ChatContext ctx = CHAT_CONTEXTS.get(sessionKey);
        if (ctx == null || ctx.isExpired()) {
            return null;
        }
        // 主体名称缺失时，继承上一轮的主体，支撑"它的库存呢/这个分类下有哪些商品"
        List<String> names = extractNames(intent);
        if (names.isEmpty() && CollUtil.isNotEmpty(ctx.getLastNames())) {
            intent.set("names", new JSONArray(ctx.getLastNames()));
        }
        return intent;
    }

    // ==================== 删除二次确认 ====================

    /**
     * 处理删除二次确认：用户回复"确认/可以/删吧"时执行强制删除。
     *
     * @return 处理结果文案；不是确认语义时返回 null（交回正常流程）
     */
    private String tryHandlePendingConfirm(String raw, PendingConfirm pendingConfirm, String sessionKey) {
        if (pendingConfirm == null || !isConfirmWord(raw)) {
            return null;
        }
        PENDING_CONFIRMS.remove(sessionKey);
        JSONObject params = new JSONObject(true);
        params.set("name", pendingConfirm.getTargetName());
        try {
            String reply = masterDataDeleteService.forceDelete(pendingConfirm.getDomain(), params);
            saveChatContext(sessionKey, raw, reply, "delete-" + pendingConfirm.getDomain(), Collections.emptyList());
            return reply;
        } catch (Exception ex) {
            log.error("[chat] 强制删除失败，domain={}，name={}", pendingConfirm.getDomain(),
                    pendingConfirm.getTargetName(), ex);
            return "删除「" + pendingConfirm.getTargetName() + "」时出错了，请稍后在对应管理页面手动处理。";
        }
    }

    /** 是否属于肯定/确认语义 */
    private boolean isConfirmWord(String text) {
        String s = StrUtil.trim(StrUtil.nullToEmpty(text));
        if (StrUtil.isBlank(s)) {
            return false;
        }
        for (String w : new String[]{"确认删除", "确认", "确定", "是的", "同意", "删吧", "删除", "可以", "好的", "好", "行"}) {
            if (s.equals(w) || s.startsWith(w)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 问句拦截 ====================

    /**
     * 查询句式强拦截：防止"咱们现在的商品有哪些没有质检单"这类疑问句
     * 被大模型误判成"新建商品"命令，把整句话当商品名落库。
     *
     * @return 命中时返回引导文案（并顺带回答质检情况）；未命中返回 null
     */
    private String querySentenceGuard(String raw) {
        String s = StrUtil.trim(StrUtil.nullToEmpty(raw));
        if (StrUtil.isBlank(s)) {
            return null;
        }
        boolean questionWord = s.contains("哪些") || s.contains("哪个") || s.contains("有没有") || s.contains("没有")
                || s.contains("是什么") || s.contains("有多少") || s.contains("吗？") || s.endsWith("吗")
                || s.contains("怎么样") || s.contains("如何") || s.contains("什么");
        boolean businessWord = s.contains("商品") || s.contains("质检") || s.contains("库存") || s.contains("分类")
                || s.contains("品牌") || s.contains("仓库") || s.contains("供应商") || s.contains("客户");
        // 疑问句 + 业务对象词 = 查询意图，绝不是建档
        if (!questionWord || !businessWord) {
            return null;
        }
        log.warn("[chat] 查询句式拦截，避免误落库：{}", s);
        if (s.contains("质检")) {
            return buildQualityReply(new ArrayList<>(), LIST_DEFAULT_LIMIT);
        }
        return "这看起来是一个查询问题，而不是建档命令。你可以这样问我：\n"
                + "• 「商品分类有哪些」「有没有鸡爪这个商品」\n"
                + "• 「鸡爪库存还有多少」「低于 20 的库存预警」\n"
                + "如果你确实要新建档案，请用「新建XX：名称」的句式告诉我。";
    }

    // ==================== 数据域问答（补充） ====================

    /**
     * 分类下商品问答：用于"这个分类下有哪些商品"这类追问。
     */
    private String buildCategoryItemsReply(List<String> names, int limit) {
        List<WmsItemCategoryDO> cats = loadCategories();
        if (CollUtil.isEmpty(cats)) {
            return "商品分类还没有建立。";
        }
        List<WmsItemCategoryDO> matched = filterByNames(cats, names,
                c -> c.getName() + " " + StrUtil.nullToEmpty(c.getCode()), limit, false);
        if (matched.isEmpty()) {
            return "未找到与「" + String.join("、", names) + "」相关的分类，无法列出其下商品。";
        }
        List<WmsItemDO> items = loadItems();
        List<String> lines = new ArrayList<>();
        for (WmsItemCategoryDO cat : matched) {
            List<WmsItemDO> children = items.stream()
                    .filter(i -> Objects.equals(i.getCategoryId(), cat.getId()))
                    .limit(limit)
                    .collect(Collectors.toList());
            lines.add("分类「" + cat.getName() + "」下共有 "
                    + items.stream().filter(i -> Objects.equals(i.getCategoryId(), cat.getId())).count() + " 个商品：");
            if (children.isEmpty()) {
                lines.add("• 暂无商品挂靠在该分类下。");
            } else {
                for (WmsItemDO it : children) {
                    lines.add("• " + it.getName()
                            + (StrUtil.isNotBlank(it.getCode()) ? "（编码 " + it.getCode() + "）" : ""));
                }
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 商品质检情况问答：列出商品是否已有质检报告，用于
     * "哪些商品没有质检单" 这类问题。
     */
    private String buildQualityReply(List<String> names, int limit) {
        List<WmsItemDO> items = loadItems();
        List<WmsItemDO> matched = filterByNames(items, names,
                i -> i.getName() + " " + StrUtil.nullToEmpty(i.getCode()), limit, false);
        if (matched.isEmpty()) {
            return CollUtil.isEmpty(names)
                    ? "商品管理中暂时还没有商品档案，因此也没有质检记录。"
                    : "【商品管理】中未找到与「" + String.join("、", names) + "」相关的商品，无法查询质检情况。";
        }
        // 批量取出涉及的质检报告
        Set<Long> reportIds = matched.stream()
                .map(WmsItemDO::getCurrentQualityReportId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, WmsItemQualityReportDO> reportMap = new HashMap<>();
        if (CollUtil.isNotEmpty(reportIds)) {
            for (WmsItemQualityReportDO r : itemQualityReportMapper.selectListByIds(reportIds)) {
                reportMap.put(r.getId(), r);
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("商品质检情况（共 " + matched.size() + " 个相关商品）：");
        int index = 0;
        int noReportCount = 0;
        for (WmsItemDO item : matched) {
            if (index++ >= limit) {
                break;
            }
            String itemName = StrUtil.isNotBlank(item.getName()) ? item.getName() : "商品#" + item.getId();
            if (item.getCurrentQualityReportId() == null) {
                lines.add("• " + itemName + "：尚无质检报告（未上传过质检凭证）");
                noReportCount++;
                continue;
            }
            WmsItemQualityReportDO report = reportMap.get(item.getCurrentQualityReportId());
            if (report == null) {
                lines.add("• " + itemName + "：关联的质检报告已不存在，建议到【商品管理】重新上传");
                continue;
            }
            StringBuilder sb = new StringBuilder("• ").append(itemName).append("：")
                    .append(WmsItemQualityStatusEnum.nameOf(report.getStatus()));
            int imgCount = countQualityImages(report.getImageUrls());
            sb.append("｜凭证图 ").append(imgCount).append(" 张");
            if (StrUtil.isNotBlank(report.getRemark())) {
                sb.append("｜备注：").append(report.getRemark());
            }
            lines.add(sb.toString());
        }
        if (noReportCount > 0 && CollUtil.isNotEmpty(names)) {
            lines.add("提示：标记「尚无质检报告」的商品，可在【商品管理】里上传质检凭证。");
        }
        if (matched.size() > limit) {
            lines.add("…仅展示前 " + limit + " 条，可说出具体商品名精确查询。");
        }
        return String.join("\n", lines);
    }

    /** 统计质检凭证图片张数（imageUrls 为 JSON 数组字符串） */
    private int countQualityImages(String imageUrls) {
        if (StrUtil.isBlank(imageUrls)) {
            return 0;
        }
        try {
            JSONArray arr = JSONUtil.parseArray(imageUrls);
            return arr == null ? 0 : arr.size();
        } catch (Exception ex) {
            // 兼容逗号分隔的纯字符串写法
            return imageUrls.split(",").length;
        }
    }

    /**
     * 系统知识问答：说明 AI 助手能做什么、数据从哪来。
     */
    private String buildKnowledgeReply() {
        return "关于本助手的说明：\n"
                + "• 我能查的是【天信管业 WMS】系统里的真实数据（商品、分类、品牌、往来企业、仓库、库存、出入库流水等）；\n"
                + "• 查询结果全部来自数据库实时读取，不会凭空编造；\n"
                + "• 我也能帮你建档（商品/分类/品牌/仓库/往来企业）和删除主数据，删除会先请你二次确认；\n"
                + "• 直接发「入库/出库/移库」清单（文字或图片）给我，可以生成建单草稿。\n"
                + "想查什么直接说，例如「鸡爪库存还有多少」。";
    }

    // ==================== 大模型调用 ====================

    private String callModel(String systemPrompt, String userContent) {
        return callModel(systemPrompt, userContent, (List<JSONObject>) null);
    }

    /**
     * 调用大模型。messages 结构为：system + 历史若干轮（user/assistant）+ 本轮 user。
     *
     * @param systemPrompt    系统提示词（角色/规则）
     * @param userContent     本轮用户输入
     * @param historyMessages 该会话的历史消息（可为空），用于多轮上下文理解
     */
    private String callModel(String systemPrompt, String userContent, List<JSONObject> historyMessages) {
        try {
            JSONObject body = new JSONObject(true);
            body.set("model", model);
            body.set("temperature", 0.0);
            body.set("max_tokens", 2000);
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.set("role", "system");
            sys.set("content", systemPrompt);
            messages.add(sys);
            // 多轮上下文：把历史对话原样回传，让模型自己完成指代消解与追问衔接
            if (CollUtil.isNotEmpty(historyMessages)) {
                for (JSONObject his : historyMessages) {
                    if (his != null && StrUtil.isNotBlank(his.getStr("content"))) {
                        messages.add(his);
                    }
                }
            }
            JSONObject user = new JSONObject();
            user.set("role", "user");
            user.set("content", StrUtil.blankToDefault(userContent, "开始"));
            messages.add(user);
            body.set("messages", messages);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(240))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[chat] 大模型调用失败，status={}，body={}", response.statusCode(), response.body());
                return null;
            }
            JSONObject root = JSONUtil.parseObj(response.body());
            JSONArray choices = root.getJSONArray("choices");
            if (choices != null && !choices.isEmpty() && choices.get(0) instanceof JSONObject) {
                JSONObject first = (JSONObject) choices.get(0);
                JSONObject message = first.getJSONObject("message");
                if (message != null) {
                    return message.getStr("content");
                }
            }
            return null;
        } catch (Exception ex) {
            log.error("[chat] 大模型调用异常", ex);
            return null;
        }
    }

    private String extractJson(String content) {
        String s = content.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) {
                s = s.substring(start + 1, end).trim();
            }
        }
        return s;
    }

}
