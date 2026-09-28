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
import cn.iocoder.txgy.module.ai.api.knowledge.AiKnowledgeApi;
import cn.iocoder.txgy.module.ai.api.knowledge.AiKnowledgeSegmentDTO;
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
import cn.iocoder.txgy.module.wms.service.ai.WmsAiConfigService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiDeleteResult;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataCreateService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataDeleteService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataStatusService;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiRuntimeConfig;
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

    private static final int LIST_DEFAULT_LIMIT = 20; // 列表类默认展示条数（用户没指定时）
    private static final int LIST_MAX_LIMIT = 200;    // 列表类展示上限（「全部/所有」时的天花板）

    /** 知识库检索：每个知识库召回的分段数 */
    private static final int KNOWLEDGE_TOP_K = 5;
    /** 知识库检索：最多拼进提示词的分段数（限制提示词长度，避免超出模型上下文） */
    private static final int KNOWLEDGE_MAX_PASSAGES = 5;

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
    private cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderMapper receiptOrderMapper;
    @Resource
    private cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderMapper shipmentOrderMapper;
    /** 留痕查询：该 RPC 只返回当前登录用户自己的记录，不提供查他人的能力 */
    @Resource
    private cn.iocoder.txgy.module.infra.api.worklog.WorkLogApi workLogApi;
    @Resource
    private WmsAiMasterDataCreateService masterDataCreateService;
    @Resource
    private WmsAiMasterDataDeleteService masterDataDeleteService;
    @Resource
    private WmsAiMasterDataStatusService masterDataStatusService;

    /**
     * AI 模型运行时配置（界面配置优先、环境变量兜底）。
     *
     * <p>必须走这里而不是直接读 {@code txgy.ai.siliconflow.*} 环境变量：界面上切换模型后，
     * 只有识别抽取链路（WmsAiServiceImpl）读了数据库配置，意图路由仍死读环境变量，
     * 就会出现「界面选了 DeepSeek，路由却还在用火山方舟的过期 Key → 403」这种不一致。
     */
    @Resource
    private WmsAiConfigService aiConfigService;

    /** 知识库检索（RAG）：查不到结构化数据时，退到公司资料里找答案 */
    @Resource
    private AiKnowledgeApi aiKnowledgeApi;

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
        TYPE_PERMISSIONS.put("subcategory", "wms:item-category:query");
        TYPE_PERMISSIONS.put("category-items", "wms:item:query");
        TYPE_PERMISSIONS.put("brand", "wms:item-brand:query");
        TYPE_PERMISSIONS.put("merchant", "wms:merchant:query");
        TYPE_PERMISSIONS.put("warehouse", "wms:warehouse:query");
        TYPE_PERMISSIONS.put("inventory", "wms:inventory:query");
        TYPE_PERMISSIONS.put("flow", "wms:inventory-history:query");
        TYPE_PERMISSIONS.put("stats", "wms:inventory:query");
        TYPE_PERMISSIONS.put("quality", "wms:item-quality-report:query");
        TYPE_PERMISSIONS.put("receipt-order", "wms:receipt-order:query");
        TYPE_PERMISSIONS.put("shipment-order", "wms:shipment-order:query");
        // 审批查询：能查单据就能看审批状态；「查看全体成员留痕」走 infra:work-log:query-all
        TYPE_PERMISSIONS.put("approval", "wms:receipt-order:query");
        // 留痕查询：只返回当前登录用户自己的操作记录（RPC 侧强制 SELF），故只需登录态
        TYPE_PERMISSIONS.put("worklog", "wms:receipt-order:query");
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
        if (StrUtil.isBlank(resolveEffectiveApiKey())) {
            return "系统尚未配置大模型 API Key，请到「WMS AI 配置」页选择可用模型，或配置 txgy.ai.siliconflow.api-key。";
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
            // 不直接回报能力介绍：模型偶发返回非 JSON / 调用超时时，
            // 仍应让本地兜底与知识库检索有机会作答，否则用户会莫名收到一段"我能做什么"。
            log.warn("[chat] 意图解析失败，改用本地兜底与知识库兜底，text={}", raw, ex);
            intent = null;
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
        // 3.02 统计/查询类问句纠偏：「这个月入库了多少」「最近一周有哪些出库」
        //      常被模型归到单据查询，回一段单据列表；严重时还会被识别成建单，回"还差商品明细"。
        //      命中时改按库存流水处理——流水带时间与数量，才是这类问题真正要的数据。
        //      注意：明确问「单据/单号/入库单/申请」的不抢，仍走单据查询。
        //      create-item 也一并纠偏：模型曾把「上个月入库最多的商品」当成"要建档的商品名"，
        //      这类问句绝不能落到建档链路去写数据。
        if (isInOutAnalyticsQuestion(raw)
                && ("receipt-order".equals(type) || "shipment-order".equals(type)
                || "create-item".equals(type) || "".equals(type) || "other".equals(type))) {
            if (intent == null) {
                intent = new JSONObject(true);
            }
            intent.set("type", "flow");
            type = "flow";
        }
        // 3.05 「二级分类 / 子分类」问句纠正：模型通常仍归为 category，
        //      回答与「有哪些分类」一模一样，用户会以为没听懂。这里按关键词纠正意图。
        if (wantsSubcategory(raw) && ("category".equals(type) || "".equals(type) || "other".equals(type))) {
            type = "subcategory";
            if (intent == null) {
                intent = new JSONObject(true);
            }
            intent.set("type", "subcategory");
        }
        // 3.1 制度类问句拦截：模型常把「出入库遵循什么原则」这类问规定的问句
        //     误判成建单/建档指令，导致回一段"还差商品明细"。这里统一改交知识库，
        //     命中资料则按资料回答，未命中则继续走原有逻辑，不影响正常指令。
        if (isPolicyQuestion(raw) && isCommandIntent(type)) {
            String policyReply = answerFromKnowledgeBase(raw, sessionKey);
            if (policyReply != null) {
                saveChatContext(sessionKey, raw, policyReply, "knowledge-base", new ArrayList<>());
                return policyReply;
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
        int limit = resolveLimit(intent, raw);
        // 单据查询的附加条件：审批状态 / 单据状态（由路由模型抽取，取不到就不加过滤）
        String approvalWord = intent == null ? "" : StrUtil.nullToEmpty(intent.getStr("approvalStatus")).trim();
        String orderStatusWord = intent == null ? "" : StrUtil.nullToEmpty(intent.getStr("orderStatus")).trim();

        try {
            String reply;
            switch (type) {
                case "item":
                    reply = buildItemReply(names, limit);
                    break;
                case "category":
                    reply = buildCategoryReply(names, limit);
                    break;
                case "subcategory":
                    reply = buildSubcategoryReply(names, limit);
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
                case "receipt-order":
                    reply = buildOrderReply(true, names, warehouseWord, approvalWord, orderStatusWord, limit, raw);
                    break;
                case "shipment-order":
                    reply = buildOrderReply(false, names, warehouseWord, approvalWord, orderStatusWord, limit, raw);
                    break;
                case "approval":
                    reply = buildApprovalReply(raw, approvalWord, limit);
                    break;
                case "worklog":
                    reply = buildWorkLogReply(raw, limit);
                    break;
                case "knowledge":
                    // 模型把「问公司制度规定」也归到这里（如「质检单编号规则是什么」）。
                    // 因此先尝试知识库检索：命中资料就按资料回答，未命中才回静态的能力说明。
                    reply = answerFromKnowledgeBase(raw, sessionKey);
                    if (reply == null) {
                        reply = buildKnowledgeReply();
                    }
                    break;
                case "create-category":
                case "create-brand":
                case "create-merchant":
                case "create-warehouse":
                case "create-item":
                    if (intent == null) {
                        intent = new JSONObject(true);
                    }
                    // 子分类补全：大模型常只抽到 name 而漏掉 parentName，
                    // 会让「新建A的子类B」变成根分类 B。这里用本地规则兜一层。
                    if ("create-category".equals(type) && StrUtil.isBlank(intent.getStr("parentName"))) {
                        JSONObject fb = createCategoryRouteFallback(raw);
                        if (fb != null && StrUtil.isNotBlank(fb.getStr("parentName"))) {
                            intent.set("parentName", fb.getStr("parentName"));
                            if (StrUtil.isBlank(intent.getStr("name"))) {
                                intent.set("name", fb.getStr("name"));
                            }
                            log.info("[chat] create-category 补全 parentName={}", fb.getStr("parentName"));
                        }
                    }
                    reply = masterDataCreateService.create(
                            type.substring("create-".length()),
                            intent);
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
                    // 兜底：先尝试知识库检索（公司制度/产品资料等非结构化文档），
                    // 命中则让模型「照着资料回答」；未命中或知识库为空时，再给澄清反问。
                    reply = answerFromKnowledgeBase(raw, sessionKey);
                    if (reply == null) {
                        reply = buildClarifyReply(raw);
                    }
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

        // 先取全部命中用于给出**真实总数**，再按上限截断用于展示。
        // 若直接取截断后列表的 size，会把「找到 N 个」说成条数上限
        //（例：实际 25 个商品却说「找到 20 个相关商品」），用户会以为商品少了。
        List<WmsItemDO> allMatched = filterByNames(items, names,
                i -> i.getName() + " " + StrUtil.nullToEmpty(i.getCode()), Integer.MAX_VALUE, false);
        List<WmsItemDO> matched = allMatched.size() > limit
                ? new ArrayList<>(allMatched.subList(0, limit)) : allMatched;
        if (matched.isEmpty()) {
            return names.isEmpty() ? "商品管理中暂时还没有商品档案。"
                    : "【商品管理】中未找到与「" + String.join("、", names) + "」相关的商品。可换个说法，或告诉我你想做入库/出库，我先帮你建档再操作。";
        }
        List<String> lines = new ArrayList<>();
        lines.add("在【商品管理】中找到 " + allMatched.size() + " 个相关商品"
                + (allMatched.size() > matched.size()
                        ? "，下面只列出前 " + matched.size() + " 个：" : "："));
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

    /**
     * 是否在问「二级分类 / 子分类」。
     *
     * <p>只用于把这类问句从 category 里区分出来，不影响 create-category 等指令。
     */
    private boolean wantsSubcategory(String text) {
        return containsAny(StrUtil.nullToEmpty(text), Arrays.asList(
                "二级分类", "二级类目", "子分类", "子类", "下级分类", "下一级分类", "下级类目"));
    }

    /**
     * 列出一级分类下面的二级分类。
     *
     * <p>用户问「二级分类呢」「有哪些子分类」时，模型通常仍归为 category，
     * 回答与「有哪些分类」一模一样，看起来像没听懂。这里单独按父分类分组列出子分类。
     *
     * @param names 一级分类关键词；为空表示列出全部一级分类下的子分类
     */
    private String buildSubcategoryReply(List<String> names, int limit) {
        List<WmsItemCategoryDO> cats = loadCategories();
        if (cats.isEmpty()) {
            return "商品分类还没有建立任何分类。";
        }
        Map<Long, List<WmsItemCategoryDO>> byParent = cats.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        List<WmsItemCategoryDO> roots = byParent.getOrDefault(0L, Collections.emptyList());

        // 指定了分类名：只列该分类下的子分类
        if (!names.isEmpty()) {
            List<WmsItemCategoryDO> matched = roots.stream()
                    .filter(c -> containsAny(c.getName(), names))
                    .collect(Collectors.toList());
            if (matched.isEmpty()) {
                return "没有找到叫「" + String.join("、", names) + "」的一级分类。"
                        + "可以先问「商品分类有哪些」看看有哪些一级分类。";
            }
            List<String> lines = new ArrayList<>();
            int index = 0;
            for (WmsItemCategoryDO parent : matched) {
                if (index++ >= limit) {
                    break;
                }
                List<WmsItemCategoryDO> subs = byParent.getOrDefault(parent.getId(), Collections.emptyList());
                if (subs.isEmpty()) {
                    lines.add("• " + parent.getName() + " 下面还没有子分类，它下面的商品是直接挂在这个分类上的。");
                    continue;
                }
                lines.add("• " + parent.getName() + " 下有 " + subs.size() + " 个子分类："
                        + subs.stream().map(WmsItemCategoryDO::getName).collect(Collectors.joining("、")));
            }
            return String.join("\n", lines);
        }

        // 未指定：把所有二级分类按一级分类分组列出来
        if (roots.isEmpty()) {
            return "商品分类还没有建立任何一级分类。";
        }
        int totalSub = cats.size() - roots.size();
        if (totalSub <= 0) {
            return "目前商品分类都只有一级，还没有建立二级分类。";
        }
        List<String> lines = new ArrayList<>();
        lines.add("目前共有 " + roots.size() + " 个一级分类、" + totalSub + " 个二级分类，二级分类如下：");
        int index = 0;
        for (WmsItemCategoryDO parent : roots) {
            List<WmsItemCategoryDO> subs = byParent.getOrDefault(parent.getId(), Collections.emptyList());
            if (subs.isEmpty()) {
                continue;
            }
            if (index++ >= limit) {
                lines.add("…还有更多一级分类未展开，可以说「XX 下有哪些子分类」单独查看。");
                break;
            }
            lines.add("• " + parent.getName() + "：" + subs.stream()
                    .map(WmsItemCategoryDO::getName).collect(Collectors.joining("、")));
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
            lines.add("商品分类一共有 " + cats.size() + " 个，其中一级分类 " + roots.size() + " 个：");
            int index = 0;
            for (WmsItemCategoryDO c : roots) {
                if (index++ >= limit) {
                    break;
                }
                Long sub = childCount.getOrDefault(c.getId(), 0L);
                Long cnt = itemCountByCat.getOrDefault(c.getId(), 0L);
                lines.add("• " + c.getName() + (sub > 0 ? "（下面还有 " + sub + " 个子分类）" : "")
                        + (cnt > 0 ? "，本类下直接有商品 " + cnt + " 个" : ""));
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
            return "「" + String.join("、", names) + "」在" + scopeText + "目前都没有库存。";
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
            return "目前" + scope + "没有库存低于 " + num(threshold) + " 的商品，库存都还充足。";
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
            return "「" + wh.getName() + "」目前没有库存。";
        }
        rows = rows.stream().filter(r -> r.getQuantity() != null && r.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            return "「" + wh.getName() + "」目前没有库存。";
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
        lines.add("「" + wh.getName() + "」里共有 " + rows.size() + " 项库存，数量加起来 " + num(totalQty) + "：");
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

    // ==================== 单据查询（入库单 / 出库单） ====================

    /** 入库单子类型编号 → 名称 */
    private static final Map<Integer, String> RECEIPT_TYPE_NAMES = Map.of(
            WmsOrderTypeConstants.RECEIPT_PRODUCTION, "生产入库",
            WmsOrderTypeConstants.RECEIPT_PURCHASE, "采购入库",
            WmsOrderTypeConstants.RECEIPT_RETURN, "退货入库",
            WmsOrderTypeConstants.RECEIPT_GIVE_BACK, "归还入库");

    /** 出库单子类型编号 → 名称 */
    private static final Map<Integer, String> SHIPMENT_TYPE_NAMES = Map.of(
            WmsOrderTypeConstants.SHIPMENT_RETURN, "退货出库",
            WmsOrderTypeConstants.SHIPMENT_SALE, "销售出库",
            WmsOrderTypeConstants.SHIPMENT_PRODUCTION, "生产出库",
            WmsOrderTypeConstants.SHIPMENT_OTHER, "其他出库");

    /** 审批状态文案（对齐 BPM 的取值，-1 表示尚未提交审批） */
    private static String approvalStatusText(Integer status) {
        if (status == null) {
            return "未提交审批";
        }
        return switch (status) {
            case 1 -> "审批中";
            case 2 -> "审批通过";
            case 3 -> "审批不通过";
            case 4 -> "已取消";
            default -> status == -1 ? "未提交审批" : "未知";
        };
    }

    /** 单据状态文案（对齐 WmsOrderStatusEnum） */
    private static String orderStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "草稿";
            case 4 -> "已完成";
            case 5 -> "已作废";
            default -> "处理中";
        };
    }

    /**
     * 把用户口语化的审批状态词转成审批状态码；识别不到返回 null（表示不过滤）。
     * 只在用户明确提到审批状态时才加过滤，避免「已完成的单子」被误当审批条件。
     */
    private static Integer parseApprovalStatus(String word) {
        String w = StrUtil.nullToEmpty(word).trim();
        if (StrUtil.isBlank(w)) {
            return null;
        }
        if (w.contains("未提交") || w.contains("待提交") || w.contains("草稿审批")) {
            return -1;
        }
        if (w.contains("审批中") || w.contains("待审批") || w.contains("审核中")) {
            return 1;
        }
        if (w.contains("通过") || w.contains("已批") || w.contains("已审批")) {
            return 2;
        }
        if (w.contains("不通过") || w.contains("驳回") || w.contains("拒绝")) {
            return 3;
        }
        if (w.contains("取消")) {
            return 4;
        }
        return null;
    }

    /** 把用户口语化的单据状态词转成单据状态码；识别不到返回 null */
    private static Integer parseOrderStatus(String word) {
        String w = StrUtil.nullToEmpty(word).trim();
        if (StrUtil.isBlank(w)) {
            return null;
        }
        if (w.contains("草稿") || w.contains("未提交")) {
            return 0;
        }
        if (w.contains("完成") || w.contains("已入库") || w.contains("已出库")) {
            return 4;
        }
        if (w.contains("作废") || w.contains("取消")) {
            return 5;
        }
        return null;
    }

    /**
     * 单据查询：支持按单号 / 仓库 / 往来企业 / 审批状态 / 单据状态过滤。
     *
     * @param receipt true 查入库单，false 查出库单
     */
    private String buildOrderReply(boolean receipt, List<String> names, String warehouseWord,
                                   String approvalWord, String orderStatusWord, int limit,
                                   String rawText) {
        String label = receipt ? "入库单" : "出库单";
        Integer approvalStatus = parseApprovalStatus(approvalWord);
        Integer orderStatus = parseOrderStatus(orderStatusWord);

        // 单号直查：优先从用户原话里正则提单号，不依赖路由模型是否把它抽进 names。
        // 单号形如 RK09216038 / CK09215597 / WMS-MES-SIM-XXX / JY-RK-20260811-006。
        String exactNo = null;
        var noMatcher = java.util.regex.Pattern
                .compile("(RK|CK|YK|PK)[A-Za-z0-9-]{4,}|WMS-[A-Za-z0-9-]{4,}|JY-[A-Za-z0-9-]{4,}")
                .matcher(StrUtil.nullToEmpty(rawText));
        if (noMatcher.find()) {
            exactNo = noMatcher.group();
        }
        if (exactNo == null) {
            for (String n : names) {
                if (StrUtil.isBlank(n)) {
                    continue;
                }
                String u = n.trim().toUpperCase(Locale.ROOT);
                if (u.startsWith("RK") || u.startsWith("CK") || u.startsWith("WMS") || u.startsWith("JY-")) {
                    exactNo = n.trim();
                    break;
                }
            }
        }
        if (exactNo != null) {
            // 单号直查：入库单与出库单是不同实体，分别取字段后再统一渲染，避免三元表达式把类型推断成 Object
            String no;
            Integer type;
            Long whId;
            Integer st;
            Integer ap;
            java.math.BigDecimal total;
            String bizNo;
            if (receipt) {
                var r = receiptOrderMapper.selectByNo(exactNo);
                if (r == null) {
                    return "没有找到" + label + "「" + exactNo + "」，请核对单号是否正确。";
                }
                no = r.getNo(); type = r.getType(); whId = r.getWarehouseId(); st = r.getStatus();
                ap = r.getApprovalStatus(); total = r.getTotalQuantity(); bizNo = r.getBizOrderNo();
            } else {
                var s = shipmentOrderMapper.selectByNo(exactNo);
                if (s == null) {
                    return "没有找到" + label + "「" + exactNo + "」，请核对单号是否正确。";
                }
                no = s.getNo(); type = s.getType(); whId = s.getWarehouseId(); st = s.getStatus();
                ap = s.getApprovalStatus(); total = s.getTotalQuantity(); bizNo = s.getBizOrderNo();
            }
            Map<Long, String> whNames = loadWarehouses().stream()
                    .collect(Collectors.toMap(WmsWarehouseDO::getId,
                            w -> StrUtil.blankToDefault(w.getName(), "仓" + w.getId()), (a, b) -> a));
            Map<Integer, String> typeNames = receipt ? RECEIPT_TYPE_NAMES : SHIPMENT_TYPE_NAMES;
            return label + "「" + no + "」：\n"
                    + "• 类型：" + typeNames.getOrDefault(type, String.valueOf(type)) + "\n"
                    + "• 仓库：" + whNames.getOrDefault(whId, "仓" + whId) + "\n"
                    + "• 单据状态：" + orderStatusText(st) + "　审批状态：" + approvalStatusText(ap) + "\n"
                    + "• 来源业务单号：" + StrUtil.blankToDefault(bizNo, "无") + "\n"
                    + "• 总数量：" + (total == null ? "-" : total);
        }

        // 仓库限定
        Long warehouseId = null;
        if (StrUtil.isNotBlank(warehouseWord)) {
            WmsWarehouseDO wh = fuzzyFirst(loadWarehouses(), warehouseWord,
                    w -> w.getName() + " " + StrUtil.nullToEmpty(w.getCode()));
            if (wh == null) {
                return "未找到与「" + warehouseWord + "」匹配的仓库，请核对仓库名称。";
            }
            warehouseId = wh.getId();
        }
        // 往来企业/商品关键词：作为模糊匹配条件（匹配不到就不过滤，避免因用词不同而空结果）
        Long merchantId = null;
        if (CollUtil.isNotEmpty(names)) {
            for (String n : names) {
                if (StrUtil.isBlank(n)) {
                    continue;
                }
                WmsMerchantDO m = fuzzyFirst(loadMerchants(), n,
                        x -> x.getName() + " " + StrUtil.nullToEmpty(x.getCode()));
                if (m != null) {
                    merchantId = m.getId();
                    break;
                }
            }
        }

        List<?> rows;
        if (receipt) {
            var q = new cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderPageReqVO();
            q.setWarehouseId(warehouseId);
            q.setMerchantId(merchantId);
            if (orderStatus != null) {
                q.setStatus(orderStatus);
            }
            q.setPageNo(1);
            q.setPageSize(limit);
            rows = receiptOrderMapper.selectPage(q).getList();
        } else {
            var q = new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderPageReqVO();
            q.setWarehouseId(warehouseId);
            q.setMerchantId(merchantId);
            if (orderStatus != null) {
                q.setStatus(orderStatus);
            }
            q.setPageNo(1);
            q.setPageSize(limit);
            rows = shipmentOrderMapper.selectPage(q).getList();
        }
        // 审批状态在 Mapper 分页里没有对应条件，这里在结果集内过滤（单据量小，代价可忽略）
        if (approvalStatus != null) {
            final Integer target = approvalStatus;
            rows = rows.stream().filter(o -> {
                Integer s = o instanceof cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO r
                        ? r.getApprovalStatus()
                        : ((cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO) o).getApprovalStatus();
                return target.equals(s);
            }).collect(Collectors.toList());
        }

        if (rows.isEmpty()) {
            StringBuilder cond = new StringBuilder();
            if (warehouseId != null) {
                cond.append("仓库「").append(warehouseWord).append("」");
            }
            if (approvalStatus != null) {
                cond.append(cond.length() > 0 ? "、" : "").append("审批状态").append(approvalStatusText(approvalStatus));
            }
            return cond.length() == 0
                    ? "目前没有" + label + "记录。"
                    : "没有符合条件的" + label + "（" + cond + "）。";
        }

        Map<Long, String> whNames = loadWarehouses().stream()
                .collect(Collectors.toMap(WmsWarehouseDO::getId,
                        w -> StrUtil.blankToDefault(w.getName(), "仓" + w.getId()), (a, b) -> a));
        Map<Integer, String> typeNames = receipt ? RECEIPT_TYPE_NAMES : SHIPMENT_TYPE_NAMES;
        List<String> lines = new ArrayList<>();
        lines.add("在【" + (receipt ? "入库管理" : "出库管理") + "】中找到 " + rows.size() + " 张" + label + "：");
        for (Object o : rows) {
            String no;
            Integer type;
            Long whId;
            Integer st;
            Integer ap;
            java.math.BigDecimal total;
            java.time.LocalDateTime created;
            if (o instanceof cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO r) {
                no = r.getNo(); type = r.getType(); whId = r.getWarehouseId(); st = r.getStatus();
                ap = r.getApprovalStatus(); total = r.getTotalQuantity(); created = r.getCreateTime();
            } else {
                var s = (cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO) o;
                no = s.getNo(); type = s.getType(); whId = s.getWarehouseId(); st = s.getStatus();
                ap = s.getApprovalStatus(); total = s.getTotalQuantity(); created = s.getCreateTime();
            }
            StringBuilder line = new StringBuilder("• ").append(no);
            String tn = typeNames.get(type);
            if (StrUtil.isNotBlank(tn)) {
                line.append("｜").append(tn);
            }
            line.append("｜仓库：").append(whNames.getOrDefault(whId, "仓" + whId));
            line.append("｜").append(orderStatusText(st));
            line.append("｜").append(approvalStatusText(ap));
            if (total != null) {
                line.append("｜数量 ").append(total);
            }
            if (created != null) {
                line.append("｜").append(created.format(TIME_FMT));
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    /**
     * 审批查询：汇总当前租户下处于各审批状态的入库单 / 出库单。
     *
     * <p>权限范围严格跟随登录用户：本方法只查「当前登录租户」的单据，
     * 与直接在「入库管理 / 出库管理 / 审批管理」页面看到的范围完全一致，
     * 不会因为通过 AI 提问而扩大可见范围。
     *
     * @param rawText        用户原话（用于判断想问「待办」还是「已办」）
     * @param approvalWord   路由模型抽取的审批状态词，可为空
     */
    private String buildApprovalReply(String rawText, String approvalWord, int limit) {
        String t = StrUtil.nullToEmpty(rawText);
        // 从原话判断意图：待审批 / 已通过 / 已驳回 / 我提交的
        Integer approvalStatus = parseApprovalStatus(approvalWord);
        if (approvalStatus == null) {
            if (containsAny(t, Arrays.asList("待审批", "待我", "待办", "审批中", "审核中", "我要审"))) {
                approvalStatus = 1;
            } else if (containsAny(t, Arrays.asList("已通过", "审批通过", "通过的单", "已批准的"))) {
                approvalStatus = 2;
            } else if (containsAny(t, Arrays.asList("驳回", "不通过", "拒绝"))) {
                approvalStatus = 3;
            }
        }
        // 「我提交的」：按创建人过滤，仍限定在当前租户内
        boolean mineOnly = containsAny(t, Arrays.asList("我提交", "我申请", "我发起", "我的申请", "我提的"));

        List<String> lines = new ArrayList<>();
        String[] labels = {"入库单", "出库单"};
        int totalFound = 0;
        for (int i = 0; i < 2; i++) {
            boolean receipt = i == 0;
            List<?> rows = queryOrdersForApproval(receipt, approvalStatus, mineOnly, limit);
            for (Object o : rows) {
                String no;
                Integer type;
                Long whId;
                Integer ap;
                String creator;
                java.math.BigDecimal total;
                java.time.LocalDateTime created;
                if (o instanceof cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO r) {
                    no = r.getNo(); type = r.getType(); whId = r.getWarehouseId();
                    ap = r.getApprovalStatus(); creator = r.getCreator();
                    total = r.getTotalQuantity(); created = r.getCreateTime();
                } else {
                    var s = (cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO) o;
                    no = s.getNo(); type = s.getType(); whId = s.getWarehouseId();
                    ap = s.getApprovalStatus(); creator = s.getCreator();
                    total = s.getTotalQuantity(); created = s.getCreateTime();
                }
                Map<Integer, String> typeNames = receipt ? RECEIPT_TYPE_NAMES : SHIPMENT_TYPE_NAMES;
                StringBuilder line = new StringBuilder("• ");
                String tn = typeNames.get(type);
                line.append(StrUtil.blankToDefault(tn, labels[i])).append(" ").append(no);
                line.append("｜").append(approvalStatusText(ap));
                if (total != null) {
                    line.append("｜数量 ").append(total);
                }
                if (created != null) {
                    line.append("｜").append(created.format(TIME_FMT));
                }
                if (StrUtil.isNotBlank(creator)) {
                    line.append("｜提交人 ").append(creator);
                }
                lines.add(line.toString());
                totalFound++;
            }
        }

        String scopeDesc = approvalStatus == null ? "全部"
                : (approvalStatus == 1 ? "审批中"
                : (approvalStatus == 2 ? "审批通过" : (approvalStatus == 3 ? "审批不通过" : "已取消")));
        if (mineOnly) {
            scopeDesc = "我提交的、" + scopeDesc;
        }
        if (totalFound == 0) {
            return "没有找到「" + scopeDesc + "」的入库单或出库单。";
        }
        lines.add(0, "【审批查询】" + scopeDesc + "的单据共 " + totalFound + " 张（仅限你有权限查看的范围）：");
        return String.join("\n", lines);
    }

    /** 按审批状态 / 创建人查询单据；始终限定在当前登录租户内 */
    private List<?> queryOrdersForApproval(boolean receipt, Integer approvalStatus,
                                           boolean mineOnly, int limit) {
        Long me = null;
        if (mineOnly) {
            me = cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
            if (me == null) {
                return new ArrayList<>();
            }
        }
        String creator = me == null ? null : String.valueOf(me);
        if (receipt) {
            var q = new cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderPageReqVO();
            q.setPageNo(1);
            q.setPageSize(limit);
            q.setCreator(creator);
            List<cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO> list =
                    receiptOrderMapper.selectPage(q).getList();
            if (approvalStatus == null) {
                return list;
            }
            final Integer target = approvalStatus;
            return list.stream().filter(o -> target.equals(o.getApprovalStatus())).collect(Collectors.toList());
        }
        var q = new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderPageReqVO();
        q.setPageNo(1);
        q.setPageSize(limit);
        q.setCreator(creator);
        List<cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO> list =
                shipmentOrderMapper.selectPage(q).getList();
        if (approvalStatus == null) {
            return list;
        }
        final Integer target = approvalStatus;
        return list.stream().filter(o -> target.equals(o.getApprovalStatus())).collect(Collectors.toList());
    }

    /**
     * 留痕查询：列出**当前登录用户自己**的操作记录。
     *
     * <p>数据来自 infra 模块的 {@link cn.iocoder.txgy.module.infra.api.worklog.WorkLogApi}，
     * 该接口在服务端强制 {@code scope=SELF}，无法指定查询他人 —— 因此 AI 的可见范围
     * 与「留痕查询」页面对普通用户的范围完全一致，不会成为越权入口。
     * 想查看全员留痕请到「留痕查询」页面（那里会校验 {@code infra:work-log:query-all}）。
     */
    private String buildWorkLogReply(String rawText, int limit) {
        String t = StrUtil.nullToEmpty(rawText);
        // 默认只显示「有实质动作」的留痕（新增/修改/删除/审批等），
        // 否则大量 QUERY 记录（如"获得仓库精简列表"）会淹没真正有意义的操作。
        boolean wantAll = containsAny(t, Arrays.asList("全部", "所有", "包括查询", "含查询", "都列出来"));
        String action = null;
        if (containsAny(t, Arrays.asList("新增", "新建", "创建", "建单", "建档"))) {
            action = "CREATE";
        } else if (containsAny(t, Arrays.asList("修改", "编辑", "更新"))) {
            action = "UPDATE";
        } else if (containsAny(t, Arrays.asList("删除", "移除"))) {
            action = "DELETE";
        } else if (containsAny(t, Arrays.asList("审批", "通过", "驳回"))) {
            action = "APPROVE";
        } else if (containsAny(t, Arrays.asList("查询", "查看", "看了"))) {
            action = "QUERY";
        } else if (!wantAll) {
            // 未指定动作且不要求全部：排除纯查询动作
            action = "NON_QUERY";
        }
        String module = t.contains("wms") || t.contains("仓储") || t.contains("仓库") ? "wms" : null;

        cn.iocoder.txgy.framework.common.pojo.PageResult<cn.iocoder.txgy.module.infra.api.worklog.dto.WorkLogRespDTO> page;
        try {
            // NON_QUERY 是本地语义，RPC 侧不认；改为拉取后在本端过滤
            String rpcAction = "NON_QUERY".equals(action) ? null : action;
            int fetchSize = "NON_QUERY".equals(action) ? Math.min(Math.max(limit * 5, 50), 200) : Math.min(limit, 50);
            var resp = workLogApi.getMyWorkLogPage(module, rpcAction, null, 1, fetchSize);
            if (resp == null || resp.getData() == null) {
                return "暂时查不到你的操作留痕，请稍后再试。";
            }
            page = resp.getData();
        } catch (Exception e) {
            log.warn("[buildWorkLogReply][查询留痕失败] err({})", e.getMessage());
            return "暂时查不到你的操作留痕（服务暂时不可用），请稍后再试。";
        }

        List<cn.iocoder.txgy.module.infra.api.worklog.dto.WorkLogRespDTO> list =
                page.getList() == null ? new ArrayList<>() : page.getList();
        // 未指定动作且不要求全部时，剔除纯查询类留痕
        if ("NON_QUERY".equals(action)) {
            list = list.stream()
                    .filter(w -> !"QUERY".equalsIgnoreCase(StrUtil.nullToEmpty(w.getAction())))
                    .limit(limit)
                    .collect(Collectors.toList());
        }
        if (list.isEmpty()) {
            return "还没有查到你的操作留痕。你在系统里的新增、修改、删除、审批等操作都会被留痕，"
                    + "操作后再来问我即可。";
        }
        List<String> lines = new ArrayList<>();
        if ("NON_QUERY".equals(action)) {
            lines.add("你的操作留痕（仅显示你自己的记录，已滤除纯查询操作；想看全部请说「包括查询」）：");
        } else {
            lines.add("你的操作留痕（共 " + page.getTotal() + " 条，仅显示你自己的记录）：");
        }
        for (var w : list) {
            StringBuilder line = new StringBuilder("• ");
            if (w.getBeginTime() != null) {
                line.append(w.getBeginTime().format(TIME_FMT)).append("　");
            }
            line.append(StrUtil.blankToDefault(w.getSummary(),
                    StrUtil.blankToDefault(w.getAction(), "操作")));
            if (StrUtil.isNotBlank(w.getBusinessKey())) {
                line.append("｜").append(w.getBusinessKey());
            }
            if (w.getResultCode() != null && w.getResultCode() != 0) {
                line.append("｜").append("失败");
            }
            lines.add(line.toString());
        }
        lines.add("提示：想查看全体成员的留痕，请到「留痕查询」页面（需有查看全员的权限）。");
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
        // 按仓库汇总
        Map<Long, List<WmsInventoryDO>> byWh = all.stream()
                .collect(Collectors.groupingBy(WmsInventoryDO::getWarehouseId));
        List<String> lines = new ArrayList<>();
        // 用「种商品」而不是「SKU 条数」：仓储同事不一定熟悉 SKU 这个词
        lines.add("系统里目前有库存的商品共 " + all.size() + " 项（同一商品在不同仓库分开算），"
                + "分布在 " + byWh.size() + " 个仓库：");
        List<Map.Entry<Long, List<WmsInventoryDO>>> sorted = new ArrayList<>(byWh.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().size(), a.getValue().size()));
        for (Map.Entry<Long, List<WmsInventoryDO>> e : sorted) {
            BigDecimal sum = e.getValue().stream()
                    .map(WmsInventoryDO::getQuantity)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lines.add("• " + whNames.getOrDefault(e.getKey(), "仓" + e.getKey())
                    + "：有 " + e.getValue().size() + " 项库存，数量加起来 " + num(sum));
        }
        // 数量排行 TOP 商品
        Map<Long, BigDecimal> qtyBySku = all.stream().filter(r -> r.getQuantity() != null)
                .collect(Collectors.groupingBy(WmsInventoryDO::getSkuId,
                        Collectors.reducing(BigDecimal.ZERO, WmsInventoryDO::getQuantity, BigDecimal::add)));
        List<Map.Entry<Long, BigDecimal>> top = new ArrayList<>(qtyBySku.entrySet());
        top.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        if (!top.isEmpty()) {
            lines.add("库存数量最多的前 " + Math.min(top.size(), 5) + " 个商品：");
            List<WmsItemSkuDO> skus = loadSkusByItemIds(loadItems().stream()
                    .map(WmsItemDO::getId).collect(Collectors.toList()));
            Map<Long, String> skuNameMap = skus.stream()
                    .collect(Collectors.toMap(WmsItemSkuDO::getId, s -> s.getName(), (a, b) -> a));
            for (int i = 0; i < Math.min(top.size(), 5); i++) {
                Map.Entry<Long, BigDecimal> e = top.get(i);
                lines.add("• " + skuNameMap.getOrDefault(e.getKey(), "商品#" + e.getKey()) + "：" + num(e.getValue()));
            }
        }
        return String.join("\n", lines);
    }

    private String capabilityIntro() {
        return "你好，我是【天信管业 WMS】智能助手。我可以帮你查系统里的数据，例如：\n"
                + "• 商品档案：查某类商品/规格/条码，如「有没有岩棉板这个商品」「把所有商品列给我」\n"
                + "• 分类 / 品牌：如「商品分类有哪些」「XX 品牌下有什么商品」\n"
                + "• 往来企业：如「供应商有哪些」「查一下 XX 公司的电话」\n"
                + "• 仓库与库存：如「岩棉板库存还有多少」「低于 20 的库存预警」「金岩原辅材料库都有什么」\n"
                + "• 出入库流水：如「岩棉板最近进过货吗」\n"
                + "• 单据查询：如「有哪些入库单」「待审批的出库单」「入库单 RK09216038 什么状态」\n"
                + "• 建档与删除：如「新建分类 保温材料」「删除商品 岩棉板」，删除前我会先请你确认\n"
                + "也可以直接把「入库 / 出库 / 移库 / 盘库」的清单（文字或图片）发我建单。\n"
                + "想一次看全某类数据时，说「全部」或「所有」即可（最多 200 条）。";
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
        prompt.append("{\"type\":\"...\",\"names\":[\"...\"],\"name\":\"\",\"parentName\":\"\",\"kind\":\"\",\"warehouse\":\"\",\"threshold\":null,\"limit\":null,"
                + "\"categoryName\":\"\",\"brandName\":\"\",\"unit\":\"\",\"specs\":[],\"barCode\":\"\",\"contact\":\"\",\"mobile\":\"\","
                + "\"telephone\":\"\",\"address\":\"\",\"email\":\"\",\"remark\":\"\","
                + "\"approvalStatus\":\"\",\"orderStatus\":\"\"}\n");
        prompt.append("type 取值分两大类：\n");
        prompt.append("【查询类】type 取值：\n");
        prompt.append("- item：问商品档案（有哪些商品/有没有XX/规格/条码/编码）。names 填商品关键词；问“有哪些商品/全部”时 names=[]。\n");
        prompt.append("- category：问商品分类/类别/类目。names 填分类关键词；问“有哪些分类”时 names=[]。\n");
        prompt.append("- subcategory：问二级分类/子分类/下级分类。names 填一级分类关键词（问全部子分类时 names=[]）。"
                + "注意：用户说“二级分类呢”“有哪些子分类”时用这个，不要再用 category。\n");
        prompt.append("- brand：问商品品牌。names 填品牌关键词。\n");
        prompt.append("- merchant：问往来企业/供应商/客户。kind 填 supplier=供应商、customer=客户、all=不明确；names 填企业名称关键词。\n");
        prompt.append("- warehouse：问仓库有哪些/仓库信息。names 填仓库关键词。\n");
        prompt.append("- inventory：问库存/还有多少/缺货/低库存预警。names 填商品关键词；warehouse 填仓库限定词（没有填空串）；threshold 仅在“低于/不足/少于/小于 N”时填数字否则 null。\n");
        prompt.append("- flow：问出入库流水/最近进货/出货。names 填商品关键词。\n");
        prompt.append("- receipt-order：问入库单本身（有哪些入库单/入库单列表/某张入库单状态/待审批的入库单/供应商的入库单）。"
                + "names 填商品或企业关键词（没有填空数组），warehouse 填仓库限定词，"
                + "approvalStatus 填审批状态词（未提交/审批中/审批通过/审批不通过，没提填空串），"
                + "orderStatus 填单据状态词（草稿/已完成/已作废，没提填空串）。"
                + "注意：问「商品进过货吗」用 flow；问「有哪些入库单/单据状态」用这个。\n");
        prompt.append("- shipment-order：问出库单本身（有哪些出库单/出库单列表/某张出库单状态/待审批的出库单）。"
                + "字段填法与 receipt-order 相同。\n");
        prompt.append("- approval：问审批相关（有哪些待审批的/我提交的申请/审批通过的单子/驳回的单子）。"
                + "approvalStatus 填审批状态词（审批中/审批通过/审批不通过/未提交，没提填空串）。"
                + "注意：问「有哪些入库单」用 receipt-order；问「待审批/审批状态汇总」用这个。\n");
        prompt.append("- worklog：问自己的操作留痕/操作记录（我最近做了什么/我的操作记录/留痕查询）。"
                + "只能查当前登录用户自己的记录，若用户要查别人的留痕，回复需要到「留痕查询」页面查看。\n");
        prompt.append("- stats：汇总统计（总库存/哪个仓最多/库存排行）。\n");
        prompt.append("- quality：问商品质检/质检单/质检报告（有没有质检单/哪些商品没质检/质检情况）。names 填商品关键词；问整体质检情况时 names=[]。\n");
        prompt.append("- category-items：问某个分类下有哪些商品。names 填分类名。\n");
        prompt.append("- knowledge：问这个助手能做什么/系统说明。\n");
        prompt.append("【建档类】仅当用户是明确的“新增/建档/建立/添加/录入”命令式请求时使用，注意区分“查询有没有XX”与“帮我新建XX”是两回事：\n");
        prompt.append("- create-item：新建商品档案。必须抽取 name=商品名（填到 name 字段，不要只放到 names 数组）；categoryName=用户说的分类名（没说留空）；unit=单位；brandName=品牌；specs=规格名数组（如 [\"DN200\",\"DN400\"]，用户给了才填，否则 []）；barCode/remark 若有则填。\n");
        prompt.append("- create-category：新建商品分类。抽取 name=要新建的分类名；parentName=父分类名（用户说“作为XX的子类”“在XX下面建”“XX的下级/子分类”时填写，没说则填空串）。"
                + "注意：「新建A的子类B」里 A 是 parentName、B 是 name，绝不能把 A 当成 name 或忽略父分类。\n");
        prompt.append("- create-brand：新建品牌。抽取 name=品牌名。\n");
        prompt.append("- create-warehouse：新建仓库。抽取 name=仓库名。\n");
        prompt.append("- create-merchant：新建往来企业。抽取 name=企业名；kind=supplier/customer/both；contact=联系人；mobile=手机；telephone=电话；address=地址；email/remark 若有则填。\n");
        prompt.append("【删除类】仅当用户是明确的“删除/删掉/移除”命令式请求时使用：\n");
        prompt.append("- delete-category：删除商品分类。抽取 name=分类名。\n");
        prompt.append("- delete-brand：删除品牌。抽取 name=品牌名。\n");
        prompt.append("- delete-item：删除商品。抽取 name=商品名。\n");
        prompt.append("- delete-warehouse：删除仓库。抽取 name=仓库名。\n");
        prompt.append("- delete-merchant：删除往来企业。抽取 name=企业名。\n");
        prompt.append("【状态类】仅当用户明确要“启用/停用（禁用）”商品分类时使用：\n");
        prompt.append("- enable-category：启用商品分类。抽取 name=分类名。\n");
        prompt.append("- disable-category：停用商品分类。抽取 name=分类名。\n");
        prompt.append("其余情况 type=greet（寒暄）或 other（与 WMS 无关或无法归类）。\n");
        prompt.append("【多轮上下文】如果对话历史里已经聊过某个商品/分类/仓库，而本轮用户用了“它/这个/那个/还有呢/哪几个”等指代，"
                + "请结合历史把对应名称补进 names，不要反问用户。\n");
        prompt.append("【重要】判断“建档”还是“查询”：只要用户在疑问（有哪些/有没有/多少/是不是/吗），一律归入查询类，"
                + "绝不可归为 create-item。只有明确的命令式（新建/建档/添加/录入）才是建档。\n");
        prompt.append("规则：字段只能从用户原话或上下文抽取，没提到的字段一律留空字符串或 null，严禁编造。"
                + "names 保留用户原文中的实质名称词，不加量词。查询与建档都不得编造系统数据。\n");
        // limit 必须显式说明，否则大模型不会填，「全部/所有/都列出来」这类请求会一直用默认条数
        prompt.append("【limit 字段】仅查询类使用，表示用户希望的返回条数，填数字：\n"
                + "- 用户明确说了数量（如「列 20 个」「前 50 条」「给我 100 个」）→ 填该数字。\n"
                + "- 用户说「全部/所有/都列出来/一个不漏/完整列表」→ 填 200（表示尽可能多）。\n"
                + "- 用户没说 → 填 null（用系统默认条数）。\n");
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
        // 1.05 子分类建档兜底：「新建A的子类B」「在A下面建B」这类句式，
        //      大模型常只抽到 name 而漏掉 parentName，导致子分类被建成根分类。
        JSONObject createCategory = createCategoryRouteFallback(t);
        if (createCategory != null) {
            return createCategory;
        }
        // 1.1 删除/启停兜底：switch 里有 delete-*/enable-*/disable-* 分支，
        //     但大模型经常把「删除分类 X」误判为寒暄或建档，导致这些分支永远走不到，
        //     因此这里用本地规则兜底，保证删除与启停命令能真正执行（删除仍会二次确认）。
        JSONObject deleteOrStatus = deleteOrStatusRouteFallback(t);
        if (deleteOrStatus != null) {
            return deleteOrStatus;
        }
        // 2. 质检问句兜底：「哪些商品没有质检单」「质检情况」→ quality。
        //    但「质检单编号规则是什么」这类在问制度规定的问句应交给知识库，
        //    否则会因为含「质检」两个字被误判成「查商品质检状态」。
        if (t.contains("质检") && !isPolicyQuestion(t)) {
            JSONObject q = new JSONObject(true);
            q.set("type", "quality");
            return q;
        }
        // 2.5 基础实体查询兜底：分类/品牌/仓库/往来企业/商品。
        //     这个兜底原本只覆盖库存与流水，换用路由能力较弱的模型（如 DeepSeek）后，
        //     「商品分类有哪些」「有哪些仓库」这类最基础的问句会被判成寒暄而落到能力介绍，
        //     因此在这里按关键词补齐，顺序上先判更具体的词，避免互相抢。
        JSONObject entity = entityQueryRouteFallback(t);
        if (entity != null) {
            return entity;
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
    /**
     * 是否为「会改变数据 / 生成单据」的指令类意图。
     *
     * <p>用于把「问制度规定」的问句从指令里摘出来：模型看到「入库/出库」字样时，
     * 容易把「出入库遵循什么原则」当成建单请求。
     */
    private boolean isCommandIntent(String type) {
        if (StrUtil.isBlank(type)) {
            return false;
        }
        return type.startsWith("create-") || type.startsWith("delete-")
                || type.startsWith("enable-") || type.startsWith("disable-");
    }

    /**
     * 是否为「入库/出库的统计型问句」。
     *
     * <p>例如「这个月入库了多少」「最近一周有哪些出库」「上个月入库最多的商品」。
     * 这类问句要的是**流水数据**（带时间与数量），不是单据列表，更不是建单指令。
     *
     * <p>刻意排除三类，避免误伤：
     * 1. 明确提到「单据/单号/入库单/出库单/申请」——那是单据查询；
     * 2. 不含任何时间窗或统计词——可能是普通描述；
     * 3. 不含「入库/出库/进货/发货」——与出入库无关。
     */
    private boolean isInOutAnalyticsQuestion(String text) {
        String t = StrUtil.nullToEmpty(text);
        if (StrUtil.isBlank(t)) {
            return false;
        }
        if (!t.contains("入库") && !t.contains("出库") && !t.contains("进货") && !t.contains("发货")) {
            return false;
        }
        if (containsAny(t, Arrays.asList("单据", "单号", "入库单", "出库单", "收货单", "发货单", "申请", "审批"))) {
            return false;
        }
        return containsAny(t, Arrays.asList("多少", "几个", "几条", "几笔", "几单",
                "最近", "近期", "本周", "这周", "本月", "这个月", "当月", "上月", "上个月",
                "昨天", "今天", "近一周", "近一个月", "最多", "最少", "排行", "排名",
                "汇总", "合计", "总计", "总量", "统计", "趋势"));
    }

    /**
     * 兜底澄清反问。
     *
     * <p>替换原来的能力介绍：用户没被听懂时，回一段「我能做什么」等于让他自己再猜一次措辞；
     * 直接给出编号选项，回复成本最低，也能提示系统究竟支持哪些口径。
     */
    private String buildClarifyReply(String text) {
        String quoted = StrUtil.isBlank(text) ? "" : StrUtil.maxLength(StrUtil.trim(text), 30);
        return (StrUtil.isBlank(quoted) ? "我没太确定你想查什么。" : "「" + quoted + "」这句我没太确定要查哪块数据。")
                + "你可以回我序号，或把问题说得更具体一点：\n"
                + "1. 库存——如「岩棉板库存还有多少」「低于 20 的库存预警」\n"
                + "2. 出入库流水——如「最近一周有哪些出库」「这个月入库了多少」\n"
                + "3. 主数据——商品 / 分类 / 品牌 / 仓库 / 往来企业，如「供应商有哪些」\n"
                + "4. 单据与审批——如「有哪些入库单」「待审批的出库单」\n"
                + "也可以直接把「入库 / 出库 / 移库」清单（文字或图片）发我建单。";
    }

    /**
     * 判断是否为「问制度/规定/标准」这类知识型问句。
     *
     * <p>这类问句要交给知识库检索，不能被「质检」「仓库」等实体关键词抢走：
     * 例如「质检单编号规则是什么」含「质检」但不是「查商品质检状态」，
     * 「库房温度要求是多少」含「库房」但不是「查仓库列表」。
     */
    private boolean isPolicyQuestion(String text) {
        return containsAny(StrUtil.nullToEmpty(text), Arrays.asList(
                "要求", "规定", "标准", "制度", "流程", "规范", "规则", "编号",
                "原则", "遵循", "依据", "条件", "限制", "禁止", "必须", "应当", "负责",
                "怎么", "如何", "为什么", "是否", "允许", "多久", "多长时间"));
    }

    /**
     * 基础实体查询的本地兜底（分类 / 品牌 / 仓库 / 往来企业 / 商品）。
     *
     * <p>背景：本地兜底原本只处理质检、库存、流水三类，当所用大模型路由能力偏弱
     * （实测 DeepSeek 会把「商品分类有哪些」「有哪些仓库」判成寒暄）时，这些最基础的
     * 问句就会落到能力介绍。这里按关键词补齐，判定顺序从具体到宽泛。
     *
     * <p>只做“归类”，不编造数据；真正的查询仍由既有 build*Reply 完成。
     */
    /** 按给定顺序返回第一个出现在文本里的候选词；都没有则返回空串 */
    private static String firstMatch(String text, List<String> candidates) {
        String t = StrUtil.nullToEmpty(text);
        for (String c : candidates) {
            if (t.contains(c)) {
                return c;
            }
        }
        return "";
    }

    private JSONObject entityQueryRouteFallback(String text) {
        String t = StrUtil.trim(StrUtil.nullToEmpty(text));
        if (StrUtil.isBlank(t)) {
            return null;
        }
        // 只在“问句”语境下接管，避免把命令句（新建/删除）抢走
        boolean asking = containsAny(t, Arrays.asList("哪些", "哪个", "有哪些", "有没有", "多少",
                "什么", "列出", "查看", "查询", "查一下", "看看", "是啥", "是什么", "吗"));
        if (!asking) {
            return null;
        }
        // 知识类问句（制度/规定/要求/标准/流程…）应交给知识库检索，不能被「仓库/商品」等实体词抢走：
        // 例如「库房温度要求是多少」含「库房」也曾含「多少」，若按仓库查询处理会答非所问。
        // 但若同时出现明确的列举词（哪些/有没有/清单），说明用户确实在要数据，仍按实体查询处理。
        boolean listWord = containsAny(t, Arrays.asList("哪些", "哪个", "有没有", "清单", "列表",
                "所有", "全部", "几个", "几家"));
        if (isPolicyQuestion(t) && !listWord) {
            return null;
        }

        JSONObject fb = new JSONObject(true);
        boolean receiptOrder = t.contains("入库单") || t.contains("收货单");
        boolean shipmentOrder = t.contains("出库单") || t.contains("发货单");
        // 留痕查询：「我的操作记录/留痕」——只查自己，必须最先判，避免被单据或审批兜底抢走
        if (containsAny(t, Arrays.asList("留痕", "操作记录", "操作日志", "我做过", "我最近做", "我的记录"))) {
            fb.set("type", "worklog");
            return fb;
        }
        // 审批查询：「待审批/我提交的申请/审批通过的单子」——比单据列表更偏"审批口径"，
        // 必须排在单据查询之前，否则会被 receipt-order 兜底抢走。
        boolean approvalAsk = containsAny(t, Arrays.asList("待审批", "待办", "待我审", "审批中",
                "我提交", "我申请", "我发起", "我的申请", "审批通过", "已通过", "驳回", "审批不通过"));
        boolean orderCtx = receiptOrder || shipmentOrder || t.contains("单据") || t.contains("申请") || t.contains("审批");
        if (approvalAsk && orderCtx) {
            fb.set("type", "approval");
            String ap = firstMatch(t, Arrays.asList("审批中", "审批通过", "审批不通过", "未提交", "待审批"));
            if (StrUtil.isNotBlank(ap)) {
                fb.set("approvalStatus", ap);
            }
            return fb;
        }
        // 单据查询：「入库单」本身（单据列表/单号/审批状态），而不是「库存」。
        // 必须排在「商品/仓库」之前，否则「有哪些入库单」会被商品或仓库兜底抢走。
        boolean orderWord = t.contains("单据") || t.contains("单号") || receiptOrder || shipmentOrder
                || ((t.contains("入库") || t.contains("出库")) && containsAny(t, Arrays.asList("哪些", "列出", "列表", "清单", "状态")));
        if (orderWord && !t.contains("流水")) {
            // 同时提到入库单和出库单时，分别按各自意图处理会更准确；这里以先出现的为准
            boolean useReceipt = receiptOrder && (!shipmentOrder || t.indexOf("入库") <= t.indexOf("出库"));
            fb.set("type", useReceipt ? "receipt-order" : "shipment-order");
            String kw = extractEntityKeyword(t);
            fb.set("names", (StrUtil.isBlank(kw) ? new JSONArray() : new JSONArray(Collections.singletonList(kw))));
            String approval = firstMatch(t, Arrays.asList("未提交", "待提交", "审批中", "待审批", "审批通过", "审批不通过", "驳回"));
            if (StrUtil.isNotBlank(approval)) {
                fb.set("approvalStatus", approval);
            }
            String orderStatus = firstMatch(t, Arrays.asList("草稿", "已完成", "已作废"));
            if (StrUtil.isNotBlank(orderStatus)) {
                fb.set("orderStatus", orderStatus);
            }
            return fb;
        }
        // 分类下有哪些商品：比“分类”更具体，先判
        if (containsAny(t, Arrays.asList("分类下", "类目下")) && containsAny(t, Arrays.asList("商品", "产品"))) {
            fb.set("type", "category-items");
            fb.set("names", (StrUtil.isBlank(extractEntityKeyword(t)) ? new JSONArray() : new JSONArray(Collections.singletonList(extractEntityKeyword(t)))));
            return fb;
        }
        if (containsAny(t, Arrays.asList("分类", "类目", "类别"))) {
            fb.set("type", "category");
            fb.set("names", (StrUtil.isBlank(extractEntityKeyword(t)) ? new JSONArray() : new JSONArray(Collections.singletonList(extractEntityKeyword(t)))));
            return fb;
        }
        if (containsAny(t, Arrays.asList("品牌"))) {
            fb.set("type", "brand");
            fb.set("names", (StrUtil.isBlank(extractEntityKeyword(t)) ? new JSONArray() : new JSONArray(Collections.singletonList(extractEntityKeyword(t)))));
            return fb;
        }
        if (containsAny(t, Arrays.asList("往来企业", "供应商", "客户", "厂商", "往来单位", "企业"))) {
            fb.set("type", "merchant");
            fb.set("kind", t.contains("客户") && !t.contains("供应商") ? "customer"
                    : (t.contains("供应商") && !t.contains("客户") ? "supplier" : "all"));
            fb.set("names", (StrUtil.isBlank(extractEntityKeyword(t)) ? new JSONArray() : new JSONArray(Collections.singletonList(extractEntityKeyword(t)))));
            return fb;
        }
        if (containsAny(t, Arrays.asList("仓库", "库房"))) {
            fb.set("type", "warehouse");
            fb.set("names", (StrUtil.isBlank(extractEntityKeyword(t)) ? new JSONArray() : new JSONArray(Collections.singletonList(extractEntityKeyword(t)))));
            return fb;
        }
        if (containsAny(t, Arrays.asList("商品", "产品", "物料", "货品"))) {
            fb.set("type", "item");
            fb.set("names", (StrUtil.isBlank(extractEntityKeyword(t)) ? new JSONArray() : new JSONArray(Collections.singletonList(extractEntityKeyword(t)))));
            return fb;
        }
        return null;
    }

    /**
     * 从问句里粗略抽一个实体关键词（用于「有没有XX这个分类」这类带具体名字的问法）。
     * 抽不到（如「商品分类有哪些」）时返回空串，交给既有逻辑列全部。
     */
    private String extractEntityKeyword(String text) {
        String t = StrUtil.trim(StrUtil.nullToEmpty(text));
        if (StrUtil.isBlank(t)) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:有没有|查一下|查询|查看|看看|有)([\\u4e00-\\u9fa5A-Za-z0-9\\-]{2,20}?)(?:这个|的)?"
                        + "(?:分类|类目|品牌|仓库|商品|产品|供应商|客户|企业)")
                .matcher(t);
        if (m.find()) {
            String kw = m.group(1);
            if (!containsAny(kw, Arrays.asList("哪些", "哪个", "什么", "所有", "全部", "几个", "多少"))) {
                return kw;
            }
        }
        return "";
    }

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

    /**
     * 删除 / 启停的本地兜底路由。
     *
     * <p>switch 中已有 delete-category / delete-brand / delete-item / delete-warehouse /
     * delete-merchant / enable-category / disable-category 七个分支，但大模型经常把
     * 「删除分类 X」误判为寒暄、甚至误判成 create-*，导致这些分支永远走不到。
     * 这里按关键词在本地判定 domain 与名称，保证删除与启停命令能真正落到业务实现上。
     *
     * <p>只负责「识别 + 传参」，删除是否真正执行仍由 WmsAiMasterDataDeleteServiceImpl
     * 决定（跨层级删除会先要求「确认删除」）。
     */
    private JSONObject deleteOrStatusRouteFallback(String text) {
        String t = StrUtil.trim(StrUtil.nullToEmpty(text));
        if (StrUtil.isBlank(t)) {
            return null;
        }
        boolean isDelete = containsAny(t, Arrays.asList("删除", "删掉", "移除", "删了", "清理掉"));
        boolean isDisable = containsAny(t, Arrays.asList("停用", "禁用", "关闭分类"));
        boolean isEnable = containsAny(t, Arrays.asList("启用", "开启分类"));
        if (!isDelete && !isDisable && !isEnable) {
            return null;
        }

        // 1. 判定 domain（先匹配更具体的词，避免「商品分类」被「商品」抢走）
        String domain = null;
        if (containsAny(t, Arrays.asList("分类", "类目", "类别"))) {
            domain = "category";
        } else if (containsAny(t, Arrays.asList("品牌"))) {
            domain = "brand";
        } else if (containsAny(t, Arrays.asList("仓库"))) {
            domain = "warehouse";
        } else if (containsAny(t, Arrays.asList("往来企业", "供应商", "客户", "厂商", "往来单位"))) {
            domain = "merchant";
        } else if (containsAny(t, Arrays.asList("商品", "物料", "货品"))) {
            domain = "item";
        }
        if (domain == null) {
            return null;
        }
        // 启停目前只支持分类，其他域不接管（交回大模型/常规回复）
        if ((isEnable || isDisable) && !"category".equals(domain)) {
            return null;
        }

        // 2. 抽取要操作的主体名
        String name = extractDeleteTargetName(t);
        if (StrUtil.isBlank(name)) {
            return null;
        }

        JSONObject fb = new JSONObject(true);
        String prefix = isDelete ? "delete-" : (isEnable ? "enable-" : "disable-");
        fb.set("type", prefix + domain);
        fb.set("name", name);
        fb.set("names", new JSONArray());
        return fb;
    }

    /**
     * 从「删除分类 XX」「把 XX 这个分类删了」这类句子里抽出主体名。
     *
     * <p>先按 `key=值` / `key：值` 取，再退化到按首尾关键词截取；
     * 去掉「这个/该/的/吧/。 」等修饰，避免把「这个分类」整体当成名字。
     */
    private String extractDeleteTargetName(String text) {
        for (String key : new String[]{"分类", "类目", "类别", "品牌", "仓库", "往来企业", "供应商", "客户", "商品", "物料"}) {
            String v = extractFieldAfterKey(text, key);
            if (StrUtil.isNotBlank(v)) {
                return cleanTargetName(v);
            }
        }
        String s = text;
        s = s.replaceAll("^(请|帮我|麻烦|把|将)+", "");
        String head = null;
        for (String w : new String[]{"删除", "删掉", "移除", "清理掉", "停用", "禁用", "启用"}) {
            int i = s.indexOf(w);
            if (i >= 0 && (head == null || i < head.length())) {
                head = s.substring(i + w.length());
            }
        }
        if (head == null) {
            return "";
        }
        // 去掉主体类型词本身（delete 的 domain 已单独判定）
        head = head.replaceAll("(分类|类目|类别|品牌|仓库|往来企业|供应商|客户|商品|物料|档案|记录|这个|该|这条|这条记录)+", " ");
        return cleanTargetName(head);
    }

    private String cleanTargetName(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        String s = raw.trim();
        s = s.replaceAll("^[的了，,。.、:：\\s]+", "");
        s = s.replaceAll("[的了，,。.、;；!！?？\\s]+$", "");
        s = s.replaceAll("(这个|该|这条|这条记录|档案|记录)$", "");
        return s.trim();
    }

    /**
     * 子分类建档的本地兜底路由。
     *
     * <p>覆盖这些说法（大模型经常只抽 name、漏掉 parentName，从而把子分类建成根分类）：
     * <ul>
     *   <li>新建{父}的子类{子} / 新增{父}的子分类{子}</li>
     *   <li>在{父}下面/下新建/新增{子}</li>
     *   <li>{父}的下级/子分类叫{子} / 名字是{子}</li>
     * </ul>
     *
     * @return 命中则返回带 parentName 的 create-category 意图；否则 null（交回大模型）
     */
    private JSONObject createCategoryRouteFallback(String text) {
        String t = StrUtil.trim(StrUtil.nullToEmpty(text));
        if (StrUtil.isBlank(t)) {
            return null;
        }
        boolean createWord = containsAny(t, Arrays.asList("新建", "新增", "创建", "添加", "建一个", "建个"));
        boolean categoryWord = containsAny(t, Arrays.asList("分类", "类目", "类别", "子类"));
        if (!createWord || !categoryWord) {
            return null;
        }

        String parent = null;
        String child = null;

        // 句式 A：「{父}的(子类|子分类|下级|下一级)(名字是|叫|为|：|:)? {子}」
        java.util.regex.Matcher mA = java.util.regex.Pattern
                .compile("([\\u4e00-\\u9fa5A-Za-z0-9]+?)的(?:子类|子分类|下级|下一级|下一层)"
                        + "(?:名字是|名叫|叫|为|是|：|:)?([\\u4e00-\\u9fa5A-Za-z0-9]+)")
                .matcher(t);
        if (mA.find()) {
            parent = mA.group(1);
            child = mA.group(2);
        }

        // 句式 B：「在{父}(下面|下|里|中)(新建|新增|创建|添加|建)?(一个|个)?(分类|子分类|子类)?(名字是|叫|为|：|:)? {子}」
        if (parent == null) {
            java.util.regex.Matcher mB = java.util.regex.Pattern
                    .compile("在([\\u4e00-\\u9fa5A-Za-z0-9]+?)(?:下面|下|里|中)"
                            + "(?:新建|新增|创建|添加|建)?(?:一个|个)?(?:分类|子分类|子类)?"
                            + "(?:名字是|名叫|叫|为|：|:)?([\\u4e00-\\u9fa5A-Za-z0-9]+)")
                    .matcher(t);
            if (mB.find()) {
                parent = mB.group(1);
                child = mB.group(2);
            }
        }

        if (StrUtil.isBlank(parent) || StrUtil.isBlank(child)) {
            return null;
        }
        parent = cleanTargetName(parent);
        child = cleanTargetName(child);
        if (StrUtil.isBlank(parent) || StrUtil.isBlank(child)) {
            return null;
        }
        log.info("[chat] 子分类建档本地兜底：parent={} name={}", parent, child);
        JSONObject fb = new JSONObject(true);
        fb.set("type", "create-category");
        fb.set("name", child);
        fb.set("parentName", parent);
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

    /** 「全部/所有/都列出来」这类表述：即使大模型没吐 limit，也按最大条数返回 */
    private static final java.util.regex.Pattern ALL_LIMIT_HINT = java.util.regex.Pattern.compile(
            "全部|所有|都列出|都给我|列完|一个不漏|完整列表|还有哪些|其余|剩下的");

    /**
     * 结合用户原话决定条数：大模型给的 limit 优先，其次识别「全部/所有」表述，最后用默认值。
     */
    private int resolveLimit(JSONObject intent, String rawText) {
        Integer fromIntent = null;
        if (intent != null) {
            BigDecimal d = parseDecimal(intent.get("limit"));
            if (d != null) {
                fromIntent = Math.max(1, Math.min(d.intValue(), LIST_MAX_LIMIT));
            }
        }
        if (fromIntent != null) {
            return fromIntent;
        }
        if (StrUtil.isNotBlank(rawText) && ALL_LIMIT_HINT.matcher(rawText).find()) {
            return LIST_MAX_LIMIT;
        }
        return LIST_DEFAULT_LIMIT;
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
                + "• 「商品分类有哪些」「有没有岩棉板这个商品」\n"
                + "• 「岩棉板库存还有多少」「低于 20 的库存预警」\n"
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
                + "想查什么直接说，例如「岩棉板库存还有多少」。";
    }

    /**
     * 知识库问答（RAG）：把公司制度、产品资料等非结构化文档检索出来，让模型照着资料回答。
     *
     * <p>只在「意图路由没命中结构化数据查询 / 指令」时兜底调用，因此这里做两件事：
     * 1. 跳过过短的寒暄（「你好」「谢谢」），省一次向量检索与一次模型调用；
     * 2. 检索无命中或知识库为空时返回 {@code null}，由调用方回落到能力介绍，
     *    避免用户问「你能做什么」时被知识库的空结果顶掉。
     *
     * <p>不引入新的存储或配置：检索范围由 AI 模块按当前租户取全部启用中的知识库，
     * 业务侧无需维护「用哪个知识库」的映射。
     */
    private String answerFromKnowledgeBase(String question, String sessionKey) {
        if (StrUtil.isBlank(question) || question.trim().length() < 2) {
            return null;
        }
        List<AiKnowledgeSegmentDTO> segments;
        try {
            segments = aiKnowledgeApi.searchKnowledge(question, KNOWLEDGE_TOP_K).getCheckedData();
        } catch (Exception ex) {
            // 知识库不可用不应影响助手其它能力，记录后让调用方回落
            log.warn("[chat] 知识库检索失败，回落能力介绍：{}", ex.getMessage());
            return null;
        }
        if (CollUtil.isEmpty(segments)) {
            log.info("[chat] 知识库未命中，回落能力介绍：{}", question);
            return null;
        }

        // 拼装资料：结果已按相似度倒序，取前若干条，避免提示词过长
        StringBuilder passages = new StringBuilder();
        int used = 0;
        for (AiKnowledgeSegmentDTO segment : segments) {
            if (used >= KNOWLEDGE_MAX_PASSAGES) {
                break;
            }
            String content = StrUtil.trimToEmpty(segment.getContent());
            if (StrUtil.isBlank(content)) {
                continue;
            }
            used++;
            passages.append("【资料").append(used).append("】").append(content).append('\n');
        }
        if (used == 0) {
            return null;
        }

        String systemPrompt = "你是【天信管业 WMS】仓储系统的智能助手。"
                + "用户的问题在系统结构化数据里查不到答案，下面是检索到的公司资料片段。\n"
                + "回答要求：\n"
                + "1. 只依据下面的资料回答，不要编造资料中没有的内容；\n"
                + "2. 资料不足以回答时，明确说「现有资料里没有相关规定」，并建议联系对应负责人；\n"
                + "3. 不要提及「资料片段」「检索」等实现细节，像正常同事回答一样自然；\n"
                + "4. 用简洁的中文，必要时分点说明。\n\n"
                + "资料如下：\n" + passages;
        String answer = callModel(systemPrompt, question, loadHistoryMessages(sessionKey));
        if (StrUtil.isBlank(answer)) {
            log.info("[chat] 知识库命中 {} 段但模型未给出回答：{}", used, question);
            return null;
        }
        log.info("[chat] 知识库命中 {} 段，已按资料作答：{}", used, question);
        return answer.trim();
    }

    // ==================== 大模型调用 ====================

    private String callModel(String systemPrompt, String userContent) {
        return callModel(systemPrompt, userContent, (List<JSONObject>) null);
    }

    /**
     * 取当前生效的模型配置：**界面配置优先**，未配置才回落到环境变量。
     *
     * <p>这是本类所有模型调用的唯一入口。历史实现直接读 {@code txgy.ai.siliconflow.*}
     * 环境变量，导致「界面上把模型切成 DeepSeek，意图路由却仍在用火山方舟的旧 Key（403 欠费）」
     * 这类不一致；统一走这里即可与识别抽取链路（WmsAiServiceImpl）保持同一份配置。
     */
    private WmsAiRuntimeConfig resolveEffectiveRuntime() {
        try {
            WmsAiRuntimeConfig runtime = aiConfigService.resolveRuntimeConfig();
            if (runtime != null && runtime.usable()) {
                return runtime;
            }
        } catch (Exception ex) {
            log.warn("[chat] 解析运行时模型配置失败，回落环境变量：{}", ex.getMessage());
        }
        WmsAiRuntimeConfig fallback = new WmsAiRuntimeConfig();
        fallback.setSource(StrUtil.isBlank(apiKey) ? WmsAiRuntimeConfig.Source.NONE
                : WmsAiRuntimeConfig.Source.ENV);
        fallback.setBaseUrl(baseUrl);
        fallback.setApiKey(apiKey);
        fallback.setModel(model);
        fallback.setVisionModel(model);
        return fallback;
    }

    /** 取当前生效的 API Key（用于调用前的可用性判断） */
    private String resolveEffectiveApiKey() {
        return resolveEffectiveRuntime().getApiKey();
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
            // 解析生效的模型配置：界面配置优先，未配置才回落到环境变量（与识别链路保持同一份配置）
            WmsAiRuntimeConfig runtime = resolveEffectiveRuntime();
            String effBaseUrl = runtime.getBaseUrl();
            String effApiKey = runtime.getApiKey();
            String effModel = runtime.getModel();
            JSONObject body = new JSONObject(true);
            body.set("model", effModel);
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
                    .uri(URI.create(effBaseUrl))
                    .timeout(Duration.ofSeconds(240))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + effApiKey)
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
