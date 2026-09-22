package cn.iocoder.txgy.module.wms.service.ai;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiApprovalAdviceReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiApprovalAdviceRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiCreateOrderReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiCreateOrderRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeFileReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO.AiEntity;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO.AiItem;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.inventory.WmsInventoryMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemCategoryMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderTypeConstants;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.idev.excel.FastExcel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WMS AI 智能识别 Service 实现
 *
 * <p>设计说明：AI 只负责“听懂人话 + 抽取关键词”，所有业务字段（商品/SKU 编码、单位、仓库、
 * 供应商等）一律回数据库主数据匹配补全，绝不让大模型凭空编造，最大限度保证与「商品管理」
 * 中录入的资料一致。识别结果不落库，由前端展示「确认预览」后再调用既有的入库/出库/移库
 * create 接口正式建单。
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class WmsAiServiceImpl implements WmsAiService {

    /** 供应商类型：2=供应商，3=客户/供应商 */
    private static final List<Integer> SUPPLIER_TYPES = Arrays.asList(
            cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum.SUPPLIER.getType(),
            cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType());
    /** 客户类型：1=客户，3=客户/供应商 */
    private static final List<Integer> CUSTOMER_TYPES = Arrays.asList(
            cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum.CUSTOMER.getType(),
            cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType());

    /** 缺品建档草稿：商品名 -> 草稿（进程内缓存，30 分钟内有效） */
    private static final Map<String, MissingItemDraft> MISSING_ITEM_DRAFTS = new ConcurrentHashMap<>();
    private static final int DRAFT_MAX_ASK = 3;
    private static final long DRAFT_TTL_MILLIS = 30 * 60 * 1000L;

    /** 清单文件解析文本的最大长度（防止提示词过大） */
    private static final int FILE_TEXT_LIMIT = 20000;

    /** 单个关键词候选数量上限 */
    private static final int CANDIDATE_LIMIT = 6;
    /** 商品名录注入上限（防提示词过大） */
    private static final int CATALOG_ITEM_LIMIT = 200;
    /** 名录里每家 SKU 展示上限 */
    private static final int CATALOG_SKU_LIMIT = 2;

    /** 入库/出库业务类型常量 -> 名称 */
    private static final Map<Integer, String> ORDER_TYPE_NAMES = new HashMap<>();

    static {
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_PRODUCTION, "生产入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_PURCHASE, "采购入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_RETURN, "退货入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.RECEIPT_GIVE_BACK, "归还入库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.SHIPMENT_RETURN, "退货出库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.SHIPMENT_SALE, "销售出库");
        ORDER_TYPE_NAMES.put(WmsOrderTypeConstants.SHIPMENT_PRODUCTION, "生产出库");
    }

    /**
     * AI 客服的模型配置来源。
     *
     * 模型/地址/Key 优先取「AI 客服配置」菜单里选中的模型（模型与 Key 仍在 AI 模块统一维护，
     * 见 ai_model / ai_api_key），未配置时回落容器环境变量 txgy.ai.siliconflow.*。
     * 每次调用实时解析，所以界面上换模型后立即生效，无需重启容器。
     */
    @Resource
    private WmsAiConfigService aiConfigService;

    /** 取当前生效的模型配置（每次调用实时解析，支持界面切换后立即生效） */
    private WmsAiRuntimeConfig runtime() {
        return aiConfigService.resolveRuntimeConfig();
    }

    /**
     * 拉取前端上传文件（相对路径 URL）时的网关基础地址；容器网络内默认走服务名 gateway
     */
    @Value("${txgy.ai.file-base-url:http://gateway:48080}")
    private String fileBaseUrl;

    // ==================== 确认建单依赖 ====================
    // AI 只负责「识别 + 匹配主数据」，最终落库复用既有单据服务，
    // 因此单据编号规则、状态机、审批联动、库存执行都与手工建单完全一致。

    @Resource
    private cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService receiptOrderService;
    @Resource
    private cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService shipmentOrderService;
    @Resource
    private cn.iocoder.txgy.module.wms.service.order.movement.WmsMovementOrderService movementOrderService;
    @Resource
    private cn.iocoder.txgy.module.wms.service.order.check.WmsCheckOrderService checkOrderService;
    @Resource
    private cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderDetailService receiptOrderDetailService;
    @Resource
    private cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderDetailService shipmentOrderDetailService;
    @Resource
    private WmsInventoryMapper inventoryMapper;

    @Resource
    private WmsItemMapper itemMapper;
    @Resource
    private WmsItemCategoryMapper itemCategoryMapper;
    @Resource
    private WmsItemSkuMapper itemSkuMapper;
    @Resource
    private WmsWarehouseMapper warehouseMapper;
    @Resource
    private WmsMerchantMapper merchantMapper;
    @Resource
    private WmsAiChatService aiChatService;
    @Resource
    private WmsAiMasterDataCreateService masterDataCreateService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public WmsAiRecognizeRespVO recognize(String text, List<String> images, String sessionId) {
        return doRecognize(text, images, sessionId, 0);
    }

    private WmsAiRecognizeRespVO doRecognize(String text, List<String> images, String sessionId, int missingDepth) {
        // 1. 参数 / 配置兜底
        boolean hasImages = CollUtil.isNotEmpty(images);
        String raw = StrUtil.trim(text);
        if (StrUtil.isBlank(raw) && !hasImages) {
            return buildEmptyResp("请告诉我你想做什么（如「采购入库 200 米 预制直埋保温管 DN200 到 金岩原辅材料库」），"
                    + "或直接上传/拍照一张入库清单图片，我来帮你识别建单。");
        }
        if (!runtime().usable()) {
            log.error("[recognize] 系统未配置硅基流动 API Key");
            return buildEmptyResp("系统尚未配置大模型 API Key（txgy.ai.siliconflow.api-key），请联系管理员配置后再使用。");
        }
        if (StrUtil.isBlank(raw)) {
            raw = "我上传了清单图片，请按图片内容生成待确认的入库/出库/移库草稿（我未提供文字说明）。";
        }

        // 1.5 上一轮在等建档补充、本轮是"自动建档/分类=xx"回复且没带新清单 → 直接续上缺品建档闭环
        //（必须放在最前：否则"自动建档"这类短句会被判为 UNKNOWN 转交闲聊，闭环断掉）
        WmsAiRecognizeRespVO resumed = tryResumePendingMissingCreate(raw, images, sessionId);
        if (resumed != null) {
            return resumed;
        }

        // 2. 加载主数据名录，交给大模型帮助规范化抽取
        List<WmsWarehouseDO> warehouses = warehouseMapper.selectSimpleList();
        List<WmsMerchantDO> suppliers = merchantMapper.selectList(new LambdaQueryWrapperX<WmsMerchantDO>()
                .in(WmsMerchantDO::getType, SUPPLIER_TYPES).orderByDesc(WmsMerchantDO::getId));
        List<WmsMerchantDO> customers = merchantMapper.selectList(new LambdaQueryWrapperX<WmsMerchantDO>()
                .in(WmsMerchantDO::getType, CUSTOMER_TYPES).orderByDesc(WmsMerchantDO::getId));
        List<WmsItemDO> itemCatalog = itemMapper.selectList(new LambdaQueryWrapperX<WmsItemDO>()
                .orderByDesc(WmsItemDO::getId).last("LIMIT " + CATALOG_ITEM_LIMIT));
        List<WmsItemSkuDO> skuCatalog = itemSkuMapper.selectList(new LambdaQueryWrapperX<WmsItemSkuDO>()
                .orderByDesc(WmsItemSkuDO::getId).last("LIMIT " + CATALOG_ITEM_LIMIT));
        Map<Long, List<WmsItemSkuDO>> skusByItem = skuCatalog.stream()
                .collect(Collectors.groupingBy(WmsItemSkuDO::getItemId));
        Map<Long, WmsItemDO> itemMap = itemCatalog.stream()
                .collect(Collectors.toMap(WmsItemDO::getId, i -> i, (a, b) -> a));

        // 3. 大模型结构化抽取：文字走文本模型；上传了清单图片则走视觉模型（逐行识别，含手写）
        String content;
        if (hasImages) {
            content = chatOnce(runtime().getVisionModel(), buildExtractSystemPrompt(warehouses, suppliers, customers,
                    itemCatalog, skusByItem, true), raw, images);
        } else {
            content = chatOnce(runtime().getModel(), buildExtractSystemPrompt(warehouses, suppliers, customers,
                    itemCatalog, skusByItem, false), raw, null);
        }
        if (StrUtil.isBlank(content)) {
            return buildEmptyResp("抱歉，AI 暂时没看懂你发的内容，请换一种说法重试，或换一张更清晰、"
                    + "正对镜头的清单照片。");
        }
        // 4. 解析抽取结果
        JSONObject json;
        try {
            json = JSONUtil.parseObj(extractJson(content));
        } catch (Exception ex) {
            log.warn("[recognize] 大模型返回非 JSON：{}", content, ex);
            return buildEmptyResp("AI 识别结果异常，请再试一次。");
        }
        WmsAiRecognizeRespVO resp = new WmsAiRecognizeRespVO();
        resp.setItems(new ArrayList<>());
        resp.setTips(new ArrayList<>());

        // 5. 动作与单据类型：图片无动作信息时，默认按采购入库处理并提示可改
        String action = normalizeAction(json.getStr("action", ""), raw);
        String hint = json.getStr("orderTypeHint", "");
        if ("UNKNOWN".equals(action) || StrUtil.isBlank(action)) {
            // 5.0 图片也可能只是「商品清单」而不是单据：明细一行数量都没有，构不成出入库单。
            //     此时若直接默认采购入库，用户就没法用图片做「添加商品」了。故先走建档闭环。
            boolean imageLooksLikeItemList = hasImages && isItemListWithoutQuantity(json);
            if (hasImages && imageLooksLikeItemList && missingDepth < 2) {
                boolean wantsCreate = isBatchCreateIntent(raw) || isCreateOnlyIntent(raw);
                WmsAiRecognizeRespVO createResp = buildMissingCreateResp(json, itemMap, skusByItem);
                if (createResp != null) {
                    log.info("[recognize] 图片识别为商品清单（无数量），走建档闭环 batchIntent={}", wantsCreate);
                    WmsAiRecognizeRespVO done = handleMissingItemForCreate(raw, images, createResp, sessionId, missingDepth);
                    if (done != null) {
                        return asCreateOnlyResp(done, StrUtil.nullToEmpty(done.getReply()));
                    }
                    // 明细都能匹配到档案 → 告知已建档即可，不生成入库草稿
                    String names0 = createResp.getItems().stream()
                            .map(i -> i.getSku() == null ? "" : StrUtil.trimToEmpty(i.getSku().getName()))
                            .filter(StrUtil::isNotBlank).distinct()
                            .collect(Collectors.joining("、"));
                    String tail0 = "如果这些商品是要入库/出库，请带上数量再发我（如「采购入库 … 20 件 到 金岩原辅材料库」）。";
                    String readyReply0 = StrUtil.isBlank(names0)
                            ? "没有从图片里识别到可建档的商品明细。要建商品档案请发商品名单；要入库/出库请带上数量。"
                            : "图片里这些商品在【商品管理】中都已经有档案了：" + names0 + "。" + tail0;
                    return asCreateOnlyResp(createResp, readyReply0);
                }
            }
            if (hasImages) {
                action = "IN";
                if (StrUtil.isBlank(hint)) {
                    hint = "采购";
                }
                resp.getTips().add("从图片中无法判断单据类型，已默认按【采购入库】生成草稿；若实际是出库，可在上方预览卡中把动作切换为【出库】。");
            } else {
                // 5.1 建档意图直达：用户说"新建这些商品"或纯发商品清单文件（无数量、构不成出入库单），
                //     但大模型已抽出商品明细 → 直接走缺品建档闭环，而不是转闲聊问非所答
                boolean fromFile = raw.contains("【清单文件：");
                JSONArray createArr = json.getJSONArray("items");
                log.info("[recognize] UNKNOWN分支 fromFile={}, batchIntent={}, itemsSize={}, depth={}",
                        fromFile, isBatchCreateIntent(raw), createArr == null ? -1 : createArr.size(), missingDepth);
                if (missingDepth < 2 && (isBatchCreateIntent(raw) || fromFile)) {
                    WmsAiRecognizeRespVO createResp = buildMissingCreateResp(json, itemMap, skusByItem);
                    if (createResp != null) {
                        WmsAiRecognizeRespVO done = handleMissingItemForCreate(raw, images, createResp, sessionId, missingDepth);
                        if (done != null) {
                            // 建档闭环已经给出结论（反问建档信息 / 已建档并重建）；
                            // 若用户只要建档、没提单据动作，这里要把重建出来的单据字段全部清掉
                            return isCreateOnlyIntent(raw) ? asCreateOnlyResp(done, StrUtil.nullToEmpty(done.getReply()))
                                    : done;
                        }
                        // 明细都能匹配到档案 → 这些商品已建档，告知即可，不生成出入库草稿
                        String names = createResp.getItems().stream()
                                .map(i -> i.getSku() == null ? "" : StrUtil.trimToEmpty(i.getSku().getName()))
                                .filter(StrUtil::isNotBlank).distinct()
                                .collect(Collectors.joining("、"));
                        String tail = "如果要入库/出库，直接发带数量的清单（如「采购入库 … 20 件 到 北京仓」）。";
                        String readyReply = StrUtil.isBlank(names)
                                ? "没有从你发的内容里识别到可建档的商品明细。要建商品档案请把商品名单发我（如：电阻-10K-0805、电容-100nF-0603）；要入库/出库请带上数量（如「采购入库 电阻 20 件」）。"
                                : missingDepth > 0
                                        ? "商品档案已全部就绪：" + names + "。" + tail
                                        : "这些商品在【商品管理】里都已经有档案了，无需重复创建：" + names + "。" + tail;
                        return asCreateOnlyResp(createResp, readyReply);
                    }
                    if (fromFile) {
                        // 文件场景但没抽出明细：直接给引导，不转闲聊（闲聊只会答非所问）
                        return buildEmptyResp("已收到你的清单文件，但这次没能从里面识别出商品明细。请确认文件里是商品名单（每行一个商品名）后重发，"
                                + "或改用 Excel/CSV 格式再试；如果想入库/出库，请在文字里带上数量（如「采购入库 某商品 20 件」）。");
                    }
                }
                // 非出入库操作（纯提问/闲聊/主数据问题）转交全域问答（带会话上下文）
                String chatReply = aiChatService.chat(raw, sessionId);
                if (StrUtil.isBlank(chatReply)) {
                    chatReply = "这个问题我暂时回答不了，你可以试试直接发「入库/出库/移库」清单让我建单，"
                            + "或问我库存、商品、供应商等 WMS 数据。";
                }
                return buildEmptyResp(chatReply);
            }
        }
        int orderType = parseOrderType(action, hint);
        resp.setAction(action);
        resp.setOrderType(orderType);
        resp.setOrderTypeName(ORDER_TYPE_NAMES.getOrDefault(orderType, ""));

        // 6. 仓库匹配补全
        if ("MOVE".equals(action)) {
            String src = json.getStr("sourceWarehouse", "");
            String dst = json.getStr("targetWarehouse", "");
            resp.setSourceWarehouse(matchWarehouse(src, warehouses));
            resp.setTargetWarehouse(matchWarehouse(dst, warehouses));
            if (!isResolved(resp.getSourceWarehouse())) {
                resp.getTips().add("未识别到明确的「移出仓库」" + (StrUtil.isBlank(src) ? "（如：从金岩成品库…）" : "（“" + src + "”未匹配到仓库，请选择）"));
            }
            if (!isResolved(resp.getTargetWarehouse())) {
                resp.getTips().add("未识别到明确的「移入仓库」" + (StrUtil.isBlank(dst) ? "（如：…移到工程项目周转库）" : "（“" + dst + "”未匹配到仓库，请选择）"));
            }
        } else {
            String wh = json.getStr("warehouse", "");
            resp.setWarehouse(matchWarehouse(wh, warehouses));
            if (!isResolved(resp.getWarehouse())) {
                resp.getTips().add(StrUtil.isBlank(wh) ? "未提到要放到/取出哪个仓库，请确认预览时选择仓库。"
                        : "“" + wh + "”未匹配到仓库，请在确认预览中选择。");
            }
        }

        // 7. 往来企业（供应商/客户）匹配补全
        String merchantWord = json.getStr("merchant", "");
        List<WmsMerchantDO> merchantPool = "OUT".equals(action) ? customers : suppliers;
        resp.setMerchant(matchMerchant(merchantWord, merchantPool));
        if (StrUtil.isNotBlank(merchantWord) && !isResolved(resp.getMerchant())) {
            resp.getTips().add("“" + merchantWord + "”未匹配到" + ("OUT".equals(action) ? "客户" : "供应商") + "，可不填或选择。");
        }

        // 8. 明细行：逐个关键词回主数据补全（同名多规格时挂候选，不替用户乱猜）
        JSONArray itemsArr = json.getJSONArray("items");
        if (itemsArr != null && !itemsArr.isEmpty()) {
            String remark = json.getStr("remark", "");
            for (Object itemObj : itemsArr) {
                if (!(itemObj instanceof JSONObject)) {
                    continue;
                }
                JSONObject itemNode = (JSONObject) itemObj;
                String name = itemNode.getStr("name", "");
                BigDecimal quantity = parseQuantity(itemNode.get("quantity"));
                BigDecimal price = parseQuantity(itemNode.get("price"));
                AiItem aiItem = buildAiItem(name, quantity, price, itemMap, skusByItem, remark);
                if (aiItem == null) {
                    continue;
                }
                resp.getItems().add(aiItem);
                if (!isResolved(aiItem.getSku())) {
                    // 可能：匹配到多个同规格候选 / 完全没匹配到
                    if (aiItem.getSku().getCandidates().size() > 1) {
                        resp.getTips().add("“" + name + "”在【商品管理】中有 " + aiItem.getSku().getCandidates().size()
                                + " 种规格（可能是同名的多种类），请在明细中选择正确的规格后再提交。");
                    } else {
                        resp.getTips().add("“" + name + "”未在【商品管理】中匹配到商品" + buildItemHint(itemCatalog, name)
                                + "，请在明细中选择或换说法重试。");
                    }
                } else if (aiItem.getQuantity() == null) {
                    resp.getTips().add("“" + name + "”没识别到数量，请确认预览时填写。");
                }
                // 单价疑似取成整行金额时，把核对提示透出到气泡上（warnIfPriceLooksLikeTotal 已写入 remark）
                if (isResolved(aiItem.getSku()) && aiItem.getRemark() != null
                        && aiItem.getRemark().contains("可能把清单上的「金额」当成了单价")) {
                    resp.getTips().add(aiItem.getRemark());
                }
            }
        } else {
            resp.getTips().add("没有识别到具体要操作的商品，请确认预览时在明细中添加，或换一张更清晰的清单照片。");
        }

        // 8.5 清单图片完整性核对：大模型同时回传图片上的清单行数，与识别出的明细数比对，防漏项
        if (hasImages) {
            int listRows = json.getInt("listRows", 0);
            int gotRows = resp.getItems().size();
            if (listRows > 0) {
                if (gotRows >= listRows) {
                    resp.getTips().add("已按清单逐行识别，共 " + listRows + " 项，未发现漏项。");
                } else {
                    resp.getTips().add("清单原约 " + listRows + " 项，目前识别出 " + gotRows
                            + " 项，请人工核对下方明细是否漏行，或换一张更清晰的照片重试。");
                }
            } else {
                resp.getTips().add("已识别清单图片" + (resp.getItems().isEmpty() ? "，但未读出商品明细，请换更清晰的图片。" : "，请核对下方明细是否与清单一致、有无漏项。"));
            }
        }

        // 8.8 缺品自动建档（文字/图片/文件场景都生效）：
        // 明细行完全没匹配到商品时，走"问清建档信息（或按默认分类一键建）-> 批量建档 -> 重建单"的多轮闭环，
        // 而不是让用户手动去商品管理建档。missingDepth 防止建档后仍缺品导致无限递归。
        if (missingDepth < 2) {
            WmsAiRecognizeRespVO missingResp = handleMissingItemForCreate(raw, images, resp, sessionId, missingDepth);
            if (missingResp != null) {
                return missingResp;
            }
        }

        // 9. 关键信息缺失时，AI 先以对话形式反问，而不是直接弹出确认卡
        String clarifyQuestion = buildClarifyQuestion(resp, json, warehouses);
        if (StrUtil.isNotBlank(clarifyQuestion)) {
            resp.setReply(clarifyQuestion);
            resp.setBuildable(false);
            resp.setReady(false);
            return resp;
        }

        // 10. 汇总文案
        resp.setReply(buildReply(resp));
        resp.setBuildable(true);
        resp.setReady(isReady(resp));
        return resp;
    }

    // ==================== 子逻辑 ====================

    @Override
    public WmsAiRecognizeRespVO recognizeFile(String text, List<WmsAiRecognizeFileReqVO.FileDoc> files, String sessionId) {
        // 1. 参数 / 配置兜底
        if (CollUtil.isEmpty(files)) {
            return buildEmptyResp("没有收到清单文件。请点击输入框左侧按钮，选择 Excel/CSV/TXT/PDF 清单文件后再发送。");
        }
        if (!runtime().usable()) {
            return buildEmptyResp("AI 服务未配置，请联系管理员在系统配置中填写大模型 API Key。");
        }
        // 2. 逐个解析文件为文本（单个失败不影响其它文件）
        List<String> contents = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (WmsAiRecognizeFileReqVO.FileDoc file : files) {
            String name = file == null ? "清单文件" : StrUtil.blankToDefault(file.getName(), "清单文件");
            try {
                byte[] bytes = decodeFileBytes(file == null ? null : file.getUrl());
                if (bytes == null || bytes.length == 0) {
                    failures.add(name + "（内容为空）");
                    continue;
                }
                String parsed = parseFileToText(name, bytes);
                if (StrUtil.isBlank(parsed)) {
                    failures.add(name + "（未解析到有效文本）");
                    continue;
                }
                if (parsed.length() > FILE_TEXT_LIMIT) {
                    parsed = parsed.substring(0, FILE_TEXT_LIMIT) + "\n…（内容过长，已截断）";
                }
                contents.add("【清单文件：" + name + "】\n" + parsed);
            } catch (Exception e) {
                log.error("[wmsAi][recognizeFile] 解析清单文件({})失败", name, e);
                failures.add(name + "（" + e.getMessage() + "）");
            }
        }
        if (CollUtil.isEmpty(contents)) {
            return buildEmptyResp("清单文件解析失败：" + String.join("；", failures)
                    + "。可以把清单截图后按图片发送，或改用 Excel/CSV/TXT 格式重试。");
        }
        // 3. 拼装提示词，复用文字识别链路（抽取 + 主数据匹配 + 多轮上下文）
        StringBuilder prompt = new StringBuilder();
        if (StrUtil.isNotBlank(text)) {
            prompt.append(StrUtil.trim(text)).append("\n\n");
        }
        prompt.append("（用户上传了清单文件，以下为按行解析的内容，同一行内各列以「 | 」分隔）\n");
        prompt.append(String.join("\n\n", contents));
        WmsAiRecognizeRespVO resp = recognize(prompt.toString(), null, sessionId);
        // 4. 部分文件失败时，把失败原因透出给用户
        if (CollUtil.isNotEmpty(failures)) {
            String tip = "部分文件解析失败：" + String.join("；", failures);
            // 只前置到 reply，不再同时追加到 tips：前端会把二者拼接，重复设置会显示两遍
            resp.setReply(tip + "\n" + StrUtil.nullToDefault(resp.getReply(), ""));
        }
        return resp;
    }

    /**
     * 按前端传来的文件地址取文件字节：
     * 1. data: 前缀 → base64 data URL 直接解码
     * 2. http(s):// → 直接下载
     * 3. 其它（如 /admin-api/infra/file/4/get/xx.xlsx 相对路径）→ 拼网关地址后下载
     */
    private byte[] decodeFileBytes(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        String target = url.trim();
        if (target.startsWith("data:")) {
            String data = target.contains(",") ? target.substring(target.indexOf(',') + 1) : target;
            try {
                return Base64.getDecoder().decode(data.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("文件内容不是有效的 base64 数据", e);
            }
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            return httpGetBytes(target);
        }
        return httpGetBytes(fileBaseUrl + (target.startsWith("/") ? target : "/" + target));
    }

    /**
     * HTTP GET 拉取文件字节（复用 LLM 的 HttpClient）
     */
    private byte[] httpGetBytes(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("文件下载失败（HTTP " + response.statusCode() + "）");
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("文件下载失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按扩展名把文件字节解析为逐行文本
     */
    private String parseFileToText(String name, byte[] bytes) {
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            ext = name.substring(dot + 1).toLowerCase();
        }
        switch (ext) {
            case "xlsx":
            case "xls":
                return excelToText(bytes);
            case "csv":
            case "txt":
                return decodeTextBytes(bytes);
            case "pdf":
                return pdfToText(bytes);
            default:
                throw new IllegalStateException("暂不支持 ." + ext + " 格式");
        }
    }

    /**
     * 解析 Excel（xlsx/xls）为逐行文本，保留表头行，列之间以「 | 」分隔
     */
    private String excelToText(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            List<Map<Integer, Object>> rows = FastExcel.read(in).headRowNumber(0).sheet(0).doReadSync();
            StringBuilder sb = new StringBuilder();
            for (Map<Integer, Object> row : rows) {
                if (row == null || row.isEmpty()) {
                    continue;
                }
                List<Integer> keys = new ArrayList<>(row.keySet());
                Collections.sort(keys);
                StringBuilder line = new StringBuilder();
                boolean hasText = false;
                for (Integer key : keys) {
                    Object value = row.get(key);
                    String cell = value == null ? "" : String.valueOf(value).trim();
                    if (line.length() > 0) {
                        line.append(" | ");
                    }
                    line.append(cell);
                    if (!cell.isEmpty()) {
                        hasText = true;
                    }
                }
                if (hasText) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString().trim();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Excel 解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析纯文本（csv/txt），自动识别 UTF-8 / GBK 编码
     */
    private String decodeTextBytes(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            try {
                text = new String(bytes, Charset.forName("GBK"));
            } catch (Exception ignore) {
                text = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text.trim();
    }

    /**
     * 解析 PDF 为文本（PDFBox）
     */
    private String pdfToText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("PDF 解析失败：" + e.getMessage(), e);
        }
    }

    private WmsAiRecognizeRespVO buildEmptyResp(String reply) {
        WmsAiRecognizeRespVO resp = new WmsAiRecognizeRespVO();
        resp.setAction("UNKNOWN");
        resp.setActionName("未识别");
        resp.setItems(new ArrayList<>());
        // 不要把 reply 再塞进 tips：前端会把 reply 与 tips 依次拼接展示
        // （index.vue: [resp.reply, ...resp.tips].join('\n')），两边同时设置会让同一段话显示两遍。
        resp.setTips(new ArrayList<>());
        resp.setReply(reply);
        resp.setBuildable(false);
        resp.setReady(false);
        return resp;
    }

    // ==================== 缺品自动建档闭环 ====================

    /**
     * 从大模型抽取结果里构造"纯建档"响应（只含商品明细，不生成出入库单）。
     * 没有有效明细行时返回 null，由调用方回落到常规分支。
     */
    private WmsAiRecognizeRespVO buildMissingCreateResp(JSONObject json, Map<Long, WmsItemDO> itemMap,
                                                        Map<Long, List<WmsItemSkuDO>> skusByItem) {
        JSONArray itemsArr = json == null ? null : json.getJSONArray("items");
        if (itemsArr == null || itemsArr.isEmpty()) {
            return null;
        }
        WmsAiRecognizeRespVO resp = new WmsAiRecognizeRespVO();
        resp.setItems(new ArrayList<>());
        resp.setTips(new ArrayList<>());
        for (Object itemObj : itemsArr) {
            if (!(itemObj instanceof JSONObject)) {
                continue;
            }
            JSONObject itemNode = (JSONObject) itemObj;
            String name = StrUtil.trim(itemNode.getStr("name", ""));
            if (StrUtil.isBlank(name)) {
                continue;
            }
            // 防脏数据：模型偶尔会把「（用户补充：分类=xx）」这类说明文字当成商品名。
            // 这类名字一旦建档就是垃圾档案，且会真实落库，因此在这里直接丢弃。
            if (looksLikeMetaTextNotItemName(name)) {
                log.warn("[recognize] 丢弃疑似说明性文字的“商品名”：{}", name);
                continue;
            }
            AiItem aiItem = buildAiItem(name, parseQuantity(itemNode.get("quantity")),
                    parseQuantity(itemNode.get("price")), itemMap, skusByItem, "");
            if (aiItem != null) {
                resp.getItems().add(aiItem);
            }
        }
        return resp.getItems().isEmpty() ? null : resp;
    }

    /**
     * 判定「抽出来的 items 是否是一份没有数量的商品清单」。
     *
     * <p>用于区分图片到底是一张单据（有数量）还是一份商品名单（没数量）：
     * 数量全空/全 0 且至少有一行商品时，认为用户是想建档（添加商品），
     * 而不是要我们凭空按采购入库生成草稿。
     */
    private boolean isItemListWithoutQuantity(JSONObject json) {
        JSONArray arr = json == null ? null : json.getJSONArray("items");
        if (arr == null || arr.isEmpty()) {
            return false;
        }
        int named = 0;
        for (Object o : arr) {
            if (!(o instanceof JSONObject node)) {
                continue;
            }
            String name = StrUtil.trim(node.getStr("name", ""));
            if (StrUtil.isBlank(name)) {
                continue;
            }
            named++;
            BigDecimal q = parseQuantity(node.get("quantity"));
            if (q != null && q.signum() > 0) {
                return false; // 只要有一行带数量，就按单据处理
            }
        }
        return named > 0;
    }

    /**
     * 判断候选名是否其实是「说明性文字」而不是商品名。
     *
     * <p>触发场景：缺品建档反问后，用户回复「分类=保温材料，单位=立方米」，
     * 系统把它拼成「（用户补充：补充建档信息：分类=保温材料）」回灌给模型，
     * 模型会把它当成第二个商品名，从而建出「补充建档信息：分类=保温材料」这样的垃圾档案。
     */
    private boolean looksLikeMetaTextNotItemName(String name) {
        if (StrUtil.isBlank(name)) {
            return true;
        }
        String s = name.trim();
        // 1. 明确的元信息标记词（「补充建档信息：分类=xx」就是从这里进来的）
        if (s.contains("用户补充") || s.contains("补充建档信息") || s.contains("自动建档")
                || s.contains("清单里") || s.contains("建档前") || s.contains("补充信息")
                || s.contains("待新建") || s.contains("未建档")) {
            return true;
        }
        // 2. 去掉「补充/说明/备注」等前缀后，如果形如「分类=xx」「单位=xx」这类字段表达式，
        //    说明整串其实是字段说明而不是商品名（商品名里不会出现「key=值」）。
        String core = s.replaceAll("^(补充|说明|备注|注明|追加)[^=：:]{0,8}[：:]?", "");
        String[] keys = {"分类", "单位", "品牌", "价格", "单价", "重量", "规格", "条码", "类目"};
        for (String k : keys) {
            if (core.startsWith(k + "=") || core.startsWith(k + "：") || core.startsWith(k + ":")) {
                return true;
            }
        }
        // 3. 整串命中 2 个以上「key=值」，基本是参数说明
        int kv = 0;
        for (String k : keys) {
            if (core.contains(k + "=") || core.contains(k + "：")) {
                kv++;
            }
        }
        if (kv >= 2) {
            return true;
        }
        // 4. 长度异常：正常商品名不会长到 30 字以上
        if (s.length() > 30) {
            return true;
        }
        // 5. 以句号结尾或含换行的整句，基本是说明文字而非名称
        return s.endsWith("。") || s.contains("\n");
    }


    /**
     * 处理"用户要入库/出库的商品还没建档"的场景：
     * 返回 null = 明细里没有需要建档的缺品，可继续常规流程；
     * 否则返回本轮应展示给用户的最终回复（引导补充建档信息，或已建档后递归重新识别）。
     */
    private WmsAiRecognizeRespVO handleMissingItemForCreate(String raw, List<String> images,
                                                            WmsAiRecognizeRespVO resp, String sessionId, int missingDepth) {
        List<String> missNames = allMissingNames(resp);
        if (missNames.isEmpty()) {
            return null;
        }
        // 0. 用户已回复「自动建档」明确放行：按默认分类批量建档，补充信息用反问时缓存的草稿值，
        //    不再调大模型抽字段（省一轮 110s 级调用）
        if (raw.contains("自动建档")) {
            MissingItemDraft cached = getValidDraft(missNames.get(0));
            String unit0 = cached != null ? StrUtil.nullToEmpty(cached.unit) : "";
            BigDecimal price0 = cached != null ? cached.costPrice : null;
            String weight0 = cached != null ? StrUtil.nullToEmpty(cached.weight) : "";
            return createMissingAndRebuild(raw, images, resp, sessionId, missingDepth,
                    missNames, "其他", unit0, price0, weight0);
        }
        // 1. 尝试从整段话（可能是"原指令；补充：xxx"拼接）抽取建档字段
        JSONObject fields;
        try {
            fields = extractItemFields(raw);
        } catch (Exception ex) {
            log.warn("[recognize] 建档字段抽取失败 names={}", missNames, ex);
            fields = null;
        }
        if (fields == null) {
            fields = new JSONObject();
        }
        String categoryName = StrUtil.trim(fields.getStr("categoryName"));
        String unit = StrUtil.trim(fields.getStr("unit"));
        BigDecimal costPrice = parseQuantity(fields.get("costPrice"));
        String weight = StrUtil.trim(fields.getStr("weight"));
        // 2. 分类未明确：建档前必须先反问确认（自动建档前先告知用户，绝不默默建档），
        //    用户回复「自动建档」或指定分类后才会真正建档；追问计数防死循环
        if (StrUtil.isBlank(categoryName)) {
            String keeper = missNames.get(0);
            MissingItemDraft draft = getValidDraft(keeper);
            if (draft == null) {
                draft = new MissingItemDraft(keeper);
                MISSING_ITEM_DRAFTS.put(keeper, draft);
            } else {
                draft.askCount++;
                if (draft.askCount >= DRAFT_MAX_ASK) {
                    MISSING_ITEM_DRAFTS.remove(keeper);
                    return buildAskResp(resp, "我几次都没能确认这批新商品的分类。为避免建错档案，请到【商品管理】手动建档后，再发我入库/出库指令。");
                }
            }
            draft.lastRaw = raw;
            draft.lastImages = images;
            draft.unit = unit;
            draft.costPrice = costPrice;
            draft.weight = weight;
            return buildAskResp(resp, "清单里有 " + missNames.size() + " 个商品还没建档：" + String.join("、", missNames) + "。\n"
                    + "建档前先跟你确认：\n"
                    + "· 回复「自动建档」：我会按分类「其他」一键建好这些商品（价格/重量以清单为准），并继续整理单据；\n"
                    + "· 或回复「分类=保温材料，单位=立方米，价格=150，重量=80kg」，按你指定的信息建档（分类不存在我会自动新建）。\n"
                    + "现有分类：" + listCategoryNamesBrief());
        }
        return createMissingAndRebuild(raw, images, resp, sessionId, missingDepth,
                missNames, categoryName, unit, costPrice, weight);
    }

    /**
     * 批量建档缺品 → 重建单（带原文/原图重新识别，让新商品直接匹配进明细）。
     * costPrice/weight 为建档补充信息：来自用户指定回复或清单中抽取，建档时一并落库。
     */
    private WmsAiRecognizeRespVO createMissingAndRebuild(String raw, List<String> images, WmsAiRecognizeRespVO resp,
                                                         String sessionId, int missingDepth,
                                                         List<String> missNames, String categoryName, String unit,
                                                         BigDecimal costPrice, String weight) {
        List<String> created = new ArrayList<>();
        List<String> createdDetails = new ArrayList<>();
        List<String> existed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (String missName : missNames) {
            JSONObject f = new JSONObject();
            f.set("name", missName);
            f.set("categoryName", categoryName);
            if (StrUtil.isNotBlank(unit)) {
                f.set("unit", unit);
            }
            if (costPrice != null) {
                f.set("costPrice", costPrice);
            }
            if (StrUtil.isNotBlank(weight)) {
                f.set("weight", weight);
            }
            try {
                String r = masterDataCreateService.create("item", f);
                if (r != null && r.contains("已存在")) {
                    existed.add(missName);
                } else {
                    created.add(missName);
                    // 展示建档详情（分类/单位/规格/成本价/重量），让用户核对新档案信息
                    createdDetails.add(r == null ? "已新建商品「" + missName + "」"
                            : r.replace("现在就可以继续做入库/出库/移库啦。", "").trim());
                }
            } catch (Exception ex) {
                log.error("[recognize] 缺品批量建档失败 name={}", missName, ex);
                failed.add(missName);
            }
            MISSING_ITEM_DRAFTS.remove(missName);
        }
        if (created.isEmpty() && existed.isEmpty()) {
            return buildAskResp(resp, "自动建档没有成功（" + String.join("、", failed)
                    + "），可稍后再试，或到【商品管理】手动建档后重发。");
        }
        // 建档完成后重建单：带原文/原图重新识别，missingDepth+1 防递归失控
        WmsAiRecognizeRespVO next;
        try {
            next = doRecognize(raw, images, sessionId, missingDepth + 1);
        } catch (Exception ex) {
            log.warn("[recognize] 建档后继续识别异常", ex);
            next = null;
        }
        if (next == null) {
            next = resp;
        }
        StringBuilder prefix = new StringBuilder();
        if (!created.isEmpty()) {
            prefix.append("已按分类「").append(categoryName).append("」自动建档 ").append(created.size()).append(" 个商品：\n");
            for (String d : createdDetails) {
                prefix.append("· ").append(d).append('\n');
            }
        }
        if (!existed.isEmpty()) {
            prefix.append("这些商品系统里已有同名档案，未重复创建：").append(String.join("、", existed)).append("。\n");
        }
        if (!failed.isEmpty()) {
            prefix.append("这几项建档失败，请在确认卡里手动处理：").append(String.join("、", failed)).append("。\n");
        }
        prefix.append("\n");
        next.setReply(prefix + StrUtil.nullToEmpty(next.getReply()));
        return next;
    }

    /**
     * 上一轮已反问过建档信息、本轮用户纯文字回复（如"自动建档"/"分类=xx，单位=xx"）且没带新清单时，
     * 用上一轮缓存的原文/原图继续"建档→重建单"闭环。
     */
    private WmsAiRecognizeRespVO tryResumePendingMissingCreate(String raw, List<String> images, String sessionId) {
        MissingItemDraft pending = null;
        long now = System.currentTimeMillis();
        for (MissingItemDraft d : MISSING_ITEM_DRAFTS.values()) {
            if (d.lastRaw != null && now - d.createdAt <= DRAFT_TTL_MILLIS) {
                pending = d;
                break;
            }
        }
        if (pending == null) {
            return null;
        }
        // 用户常会"带着原清单图回复"：同一张图（base64 完全一致）视为回复而非新清单；换成新图才是新清单
        boolean sameImages = CollUtil.isNotEmpty(images) && images.equals(pending.lastImages);
        if (CollUtil.isNotEmpty(images) && !sameImages) {
            return null;
        }
        boolean hasCategoryReply = false;
        if (!isBatchCreateIntent(raw)) {
            try {
                JSONObject f = extractItemFields(raw);
                hasCategoryReply = f != null && StrUtil.isNotBlank(f.getStr("categoryName"));
            } catch (Exception ignore) {
            }
            if (!hasCategoryReply) {
                return null; // 不是建档相关回复，走常规流程
            }
        }
        MISSING_ITEM_DRAFTS.remove(pending.name);
        // 把用户本轮的建档指令/分类信息拼进上一轮清单原文，重建时意图不丢
        String supplement = isBatchCreateIntent(raw)
                ? "自动建档——清单里没建档的商品直接按默认分类建好并继续"
                : "补充建档信息：" + raw;
        String resumeRaw = pending.lastRaw + "\n（用户补充：" + supplement + "）";
        try {
            return doRecognize(resumeRaw, pending.lastImages, sessionId, 1);
        } catch (Exception ex) {
            log.warn("[recognize] 恢复缺品建档流程异常", ex);
            return null;
        }
    }

    /**
     * 收集明细中所有"完全没匹配到商品"的行（多候选待选的不算）
     */
    private List<String> allMissingNames(WmsAiRecognizeRespVO resp) {
        List<String> names = new ArrayList<>();
        if (resp.getItems() == null) {
            return names;
        }
        for (AiItem it : resp.getItems()) {
            if (it.getSku() != null && !isResolved(it.getSku()) && hasNoCandidate(it.getSku())
                    && StrUtil.isNotBlank(it.getSku().getName())) {
                String n = it.getSku().getName().trim();
                // 双保险：说明性文字（如「补充建档信息：分类=xx」）不能进入自动建档，否则会落成垃圾档案
                if (looksLikeMetaTextNotItemName(n)) {
                    log.warn("[recognize] 自动建档前丢弃疑似说明性文字：{}", n);
                    continue;
                }
                if (!names.contains(n)) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    /**
     * 用户这句话是否表达"把缺的商品直接建档"的意图
     */
    private boolean isBatchCreateIntent(String raw) {
        if (StrUtil.isBlank(raw)) {
            return false;
        }
        String t = raw.trim();
        if (StrUtil.containsAny(t, "不要建档", "别建档", "先不建档", "暂不建档", "不用建档", "不要建", "别建", "先不建", "暂不建")) {
            return false;
        }
        // 「新增这些商品」「新建这几个商品」等中间插词的写法，用「新增/新建/添加/创建/建立 + 商品」组合判定
        boolean verbThenItem = (t.contains("新增") || t.contains("新建") || t.contains("创建") || t.contains("建立")
                || t.contains("添加") || t.contains("加入") || t.contains("录入"))
                && (t.contains("商品") || t.contains("档案") || t.contains("货品") || t.contains("物料"));
        return StrUtil.containsAny(t, "自动建档", "帮我建档", "建档", "添加到商品", "加到商品", "加入商品",
                "添加商品", "新增商品", "创建商品", "建商品", "商品建档", "一键建")
                || verbThenItem;
    }

    /**
     * 是否"只要建档、不要建单"：命中建档语义，且整句话里没有任何出入库/移库的单据动词。
     *
     * <p>用于给建档闭环加一道闸门：像「帮我新增这些商品」这类请求，
     * 建档完成后**绝不能**再产出【采购入库】待确认单——用户从头到尾没说过要入库。
     * 反之「新建这些商品并入库到北京仓」含「入库」，应当正常建单。
     */
    private boolean isCreateOnlyIntent(String raw) {
        if (!isBatchCreateIntent(raw)) {
            return false;
        }
        String t = StrUtil.trim(raw);
        // 含单据动词 = 用户确实要建单，不能只建档
        return !StrUtil.containsAny(t, "入库", "出库", "移库", "转库", "调拨", "收货", "发货", "进货",
                "采购", "销售", "领用", "归还", "退库", "盘点");
    }

    /**
     * 建档意图下的"无单据"收尾：把响应里所有单据相关字段清干净，
     * 只保留建档结果文案 + 商品明细，避免前端渲染出待确认入库单。
     */
    private WmsAiRecognizeRespVO asCreateOnlyResp(WmsAiRecognizeRespVO resp, String reply) {
        resp.setAction("UNKNOWN");
        resp.setActionName("已建档");
        resp.setOrderType(0);
        resp.setOrderTypeName("");
        resp.setWarehouse(null);
        resp.setSourceWarehouse(null);
        resp.setTargetWarehouse(null);
        resp.setMerchant(null);
        // 数量/单价清空：建档与数量无关，留着会让前端把 0 渲染成待填数量
        if (resp.getItems() != null) {
            for (AiItem item : resp.getItems()) {
                item.setQuantity(null);
                item.setPrice(null);
            }
        }
        resp.setReply(reply);
        resp.setBuildable(false);
        resp.setReady(false);
        return resp;
    }

    /**
     * 建档完成后重建单的结果处理：若用户只要建档，则丢弃重建出来的单据，只保留建档汇总。
     */
    private WmsAiRecognizeRespVO keepCreateSummaryOnly(WmsAiRecognizeRespVO next, String prefix) {
        StringBuilder reply = new StringBuilder(prefix);
        List<String> ready = new ArrayList<>();
        if (next != null && next.getItems() != null) {
            for (AiItem item : next.getItems()) {
                if (isResolved(item.getSku())) {
                    String name = StrUtil.trimToEmpty(item.getSku().getName());
                    if (StrUtil.isNotBlank(name) && !ready.contains(name)) {
                        ready.add(name);
                    }
                }
            }
        }
        if (!ready.isEmpty()) {
            reply.append("商品档案已就绪：").append(String.join("、", ready)).append("。\n");
        }
        reply.append("商品已建档完成，未生成任何单据。需要入库/出库时，请再发一句带数量的话（如「采购入库 商品名 20 件 到 ")
                .append("仓库名」），我会帮你整理待确认单。");
        WmsAiRecognizeRespVO out = next == null ? new WmsAiRecognizeRespVO() : next;
        if (out.getItems() == null) {
            out.setItems(new ArrayList<>());
        }
        if (out.getTips() == null) {
            out.setTips(new ArrayList<>());
        }
        return asCreateOnlyResp(out, reply.toString());
    }

    private String listCategoryNamesBrief() {
        try {
            List<WmsItemCategoryDO> cats = itemCategoryMapper.selectList(new LambdaQueryWrapperX<WmsItemCategoryDO>()
                    .eq(WmsItemCategoryDO::getParentId, WmsItemCategoryDO.PARENT_ID_ROOT)
                    .eq(WmsItemCategoryDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                    .orderByAsc(WmsItemCategoryDO::getSort)
                    .orderByAsc(WmsItemCategoryDO::getId));
            return cats.stream().map(WmsItemCategoryDO::getName).limit(10).collect(Collectors.joining("、"));
        } catch (Exception ex) {
            log.warn("[recognize] 查询分类候选失败", ex);
            return "";
        }
    }

    /**
     * 找到明细中第一件"完全没匹配到商品"的行（不是多候选需要用户选的情形）
     */
    private String firstMissingName(WmsAiRecognizeRespVO resp) {
        if (resp.getItems() == null) {
            return null;
        }
        for (AiItem it : resp.getItems()) {
            if (it.getSku() != null && !isResolved(it.getSku()) && hasNoCandidate(it.getSku())
                    && StrUtil.isNotBlank(it.getSku().getName())) {
                return it.getSku().getName().trim();
            }
        }
        return null;
    }

    private MissingItemDraft getValidDraft(String name) {
        MissingItemDraft draft = MISSING_ITEM_DRAFTS.get(name);
        if (draft == null) {
            return null;
        }
        if (System.currentTimeMillis() - draft.createdAt > DRAFT_TTL_MILLIS) {
            MISSING_ITEM_DRAFTS.remove(name);
            return null;
        }
        return draft;
    }

    private JSONObject onlyNameFields(String name) {
        JSONObject fields = new JSONObject();
        fields.set("name", name);
        return fields;
    }

    private WmsAiRecognizeRespVO buildAskResp(WmsAiRecognizeRespVO resp, String message) {
        resp.setReply(message);
        resp.setBuildable(false);
        resp.setReady(false);
        return resp;
    }

    /**
     * 从"原指令；补充：xxx"整段文本中，抽取为新建商品所需的最小字段集。
     * 严格只取原话信息，未提及的字段留空，绝不编造。
     */
    private JSONObject extractItemFields(String text) {
        String prompt = "用户可能正在描述一次入库/出库/移库，或在补充一个待新建商品的档案信息。"
                + "请从这句话里只做一件事：抽取“新建一个商品档案所需的字段”，严格只输出一个 JSON，不要任何解释：\n"
                + "{\"name\":\"\",\"categoryName\":\"\",\"unit\":\"\",\"brandName\":\"\",\"specs\":[],\"barCode\":\"\",\"costPrice\":\"\",\"weight\":\"\"}\n"
                + "字段含义：\n"
                + "- name：这句话要处理/建档的“商品主体名”（如 岩棉板、预制直埋保温管）。\n"
                + "- categoryName：用户明确提到的分类/类别/类目名（如“分类=保温材料”“保温材料类”），未提及填空串。\n"
                + "- unit：用户提到的单位（件/立方米/米/kg/箱等），未提及填空串。\n"
                + "- brandName：用户提到的品牌，未提及填空串。\n"
                + "- specs：规格名数组，例如用户说“30袋/箱”“DN200”且这是指该商品的规格时放入数组；其余为 [].\n"
                + "- barCode：明确出现的条码，没有填空串。\n"
                + "- costPrice：用户提到的单价/价格/金额（如“单价3.5元”“价格=8块”），输出纯数字字符串；未提及填空串。\n"
                + "- weight：用户明确提到的重量（如“每件10kg”“重500克”），原样输出保留单位；未提及填空串。\n"
                + "注意：全部字段只能来自用户原话，严禁推测或编造；纯闲聊/寒暄时 name 填空串。\n"
                + "【重要】文本里如出现「（用户补充：…）」这样的段落，那是用户对上一轮问题的补充说明（分类/单位/价格等），"
                + "属于元信息，绝不是商品名。禁止把「用户补充」「补充建档信息」「分类=…」「单位=…」等说明性文字当作 name 输出；"
                + "此时 name 必须留空字符串，只在 categoryName / unit / costPrice / weight 等对应字段里体现这些补充值。\n"
                + "用户输入：\n" + text;
        String content = chatOnce(runtime().getModel(), prompt, "开始", null);
        if (StrUtil.isBlank(content)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(extractJson(content));
        } catch (Exception ex) {
            log.warn("[recognize] 建档字段抽取非 JSON：{}", content, ex);
            return null;
        }
    }

    /** 缺品建档草稿 */
    private static class MissingItemDraft {
        private final String name;
        private final long createdAt;
        private int askCount;
        /** 反问时保存的原文/原图，供用户后续纯文字回复（如"自动建档"）时重建清单 */
        private String lastRaw;
        private List<String> lastImages;
        /** 反问时已抽取的建档补充信息（用户回复「自动建档」时直接使用） */
        private String unit;
        private BigDecimal costPrice;
        private String weight;

        private MissingItemDraft(String name) {
            this.name = name;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /**
     * 规范化动作：优先取大模型判断；缺失时按原文关键字兜底
     */
    private String normalizeAction(String llmAction, String raw) {
        String action = StrUtil.isNotBlank(llmAction) ? llmAction.trim().toUpperCase(Locale.ROOT) : "";
        if ("IN".equals(action) || "OUT".equals(action) || "MOVE".equals(action)) {
            return action;
        }
        if (StrUtil.containsAny(raw, "移库", "转库", "调拨", "挪库")) {
            return "MOVE";
        }
        if (StrUtil.containsAny(raw, "出库", "销售", "发货", "领用")) {
            return "OUT";
        }
        if (StrUtil.containsAny(raw, "入库", "收货", "采购", "进货")) {
            return "IN";
        }
        return "UNKNOWN";
    }

    private int parseOrderType(String action, String hint) {
        String h = StrUtil.isBlank(hint) ? "" : hint;
        if ("IN".equals(action)) {
            if (StrUtil.contains(h, "生产")) return WmsOrderTypeConstants.RECEIPT_PRODUCTION;
            if (StrUtil.contains(h, "退")) return WmsOrderTypeConstants.RECEIPT_RETURN;
            if (StrUtil.containsAny(h, "归", "还")) return WmsOrderTypeConstants.RECEIPT_GIVE_BACK;
            return WmsOrderTypeConstants.RECEIPT_PURCHASE; // 默认采购入库
        }
        if ("OUT".equals(action)) {
            if (StrUtil.contains(h, "退")) return WmsOrderTypeConstants.SHIPMENT_RETURN;
            if (StrUtil.contains(h, "生产")) return WmsOrderTypeConstants.SHIPMENT_PRODUCTION;
            return WmsOrderTypeConstants.SHIPMENT_SALE; // 默认销售出库
        }
        return 0;
    }

    /**
     * 仓库匹配：名称包含 / 编码相等命中；
     * 无论命中与否，candidates 都带上仓库候选（命中项排最前），前端确认卡统一渲染为
     * 下拉选择框：唯一命中时默认选中、用户可随时改选；未指定/未命中时由用户在卡上点选，
     * 避免"自动替用户选仓库"且改不了的问题。
     */
    private AiEntity matchWarehouse(String word, List<WmsWarehouseDO> warehouses) {
        AiEntity entity = new AiEntity();
        entity.setCandidates(new ArrayList<>());
        if (StrUtil.isBlank(word)) {
            // 未指定：候选给全部仓库，等用户在确认卡中点选
            fillWarehouseOptions(entity, warehouses, null, null);
            return entity;
        }
        String kw = word.trim().toLowerCase(Locale.ROOT);
        List<WmsWarehouseDO> matched = new ArrayList<>();
        for (WmsWarehouseDO wh : warehouses) {
            String whName = StrUtil.nullToEmpty(wh.getName()).toLowerCase(Locale.ROOT);
            boolean hit = (StrUtil.isNotBlank(wh.getName()) && whName.contains(kw))
                    || (StrUtil.isNotBlank(wh.getCode()) && wh.getCode().equalsIgnoreCase(kw))
                    || (StrUtil.isNotBlank(wh.getName()) && kw.contains(whName));
            if (hit) {
                matched.add(wh);
            }
        }
        if (matched.size() == 1) {
            WmsWarehouseDO hit = matched.get(0);
            fillEntity(entity, hit.getId(), hit.getCode(), hit.getName());
            fillWarehouseOptions(entity, warehouses, hit, null);
            return entity;
        }
        entity.setName(word.trim());
        // 命中多个 → 命中的排前面；完全未命中 → 全部仓库供选择
        fillWarehouseOptions(entity, warehouses, matched.isEmpty() ? null : matched.get(0),
                matched.isEmpty() ? null : matched);
        return entity;
    }

    private AiEntity toWarehouseCandidate(WmsWarehouseDO wh) {
        AiEntity cand = new AiEntity();
        fillEntity(cand, wh.getId(), wh.getCode(), wh.getName());
        return cand;
    }

    /**
     * 填充仓库候选：first（命中的仓库）排最前，matchedOnly 非空时优先展示命中的，
     * 然后补齐其余仓库；仓库数量少，不做严格截断，保证卡上可改选任意仓库。
     */
    private void fillWarehouseOptions(AiEntity entity, List<WmsWarehouseDO> warehouses,
                                      WmsWarehouseDO first, List<WmsWarehouseDO> matchedOnly) {
        if (first != null) {
            entity.getCandidates().add(toWarehouseCandidate(first));
        }
        if (matchedOnly != null) {
            for (WmsWarehouseDO wh : matchedOnly) {
                if (first != null && wh.getId().equals(first.getId())) {
                    continue;
                }
                entity.getCandidates().add(toWarehouseCandidate(wh));
            }
        }
        for (WmsWarehouseDO wh : warehouses) {
            if (entity.getCandidates().size() >= 20) {
                break;
            }
            boolean exists = entity.getCandidates().stream().anyMatch(c -> wh.getId().equals(c.getId()));
            if (!exists) {
                entity.getCandidates().add(toWarehouseCandidate(wh));
            }
        }
    }

    private void fillWarehouseCandidates(AiEntity entity, List<WmsWarehouseDO> selected) {
        for (WmsWarehouseDO wh : selected) {
            if (entity.getCandidates().size() >= CANDIDATE_LIMIT) {
                break;
            }
            AiEntity cand = new AiEntity();
            fillEntity(cand, wh.getId(), wh.getCode(), wh.getName());
            entity.getCandidates().add(cand);
        }
    }

    /**
     * 往来企业匹配（供应商/客户）
     */
    private AiEntity matchMerchant(String word, List<WmsMerchantDO> pool) {
        AiEntity entity = new AiEntity();
        entity.setCandidates(new ArrayList<>());
        if (StrUtil.isBlank(word)) {
            return entity;
        }
        String kw = word.trim().toLowerCase(Locale.ROOT);
        List<WmsMerchantDO> matched = pool.stream()
                .filter(m -> StrUtil.isNotBlank(m.getName()) && m.getName().toLowerCase(Locale.ROOT).contains(kw)
                        || StrUtil.isNotBlank(m.getCode()) && m.getCode().equalsIgnoreCase(kw))
                .collect(Collectors.toList());
        if (matched.size() == 1) {
            fillEntity(entity, matched.get(0).getId(), matched.get(0).getCode(), matched.get(0).getName());
            return entity;
        }
        entity.setName(word.trim());
        for (WmsMerchantDO m : matched.isEmpty() ? pool : matched) {
            if (entity.getCandidates().size() >= CANDIDATE_LIMIT) {
                break;
            }
            AiEntity cand = new AiEntity();
            fillEntity(cand, m.getId(), m.getCode(), m.getName());
            entity.getCandidates().add(cand);
        }
        return entity;
    }

    /**
     * 明细行：keyword 回商品/SKU 主数据匹配补全，完整资料由数据库回填。
     * price 为清单/图片中识别到的单价：识别到则优先展示，未识别到回退 SKU 成本价。
     */
    private AiItem buildAiItem(String keyword, BigDecimal quantity, BigDecimal price,
                               Map<Long, WmsItemDO> itemMap,
                               Map<Long, List<WmsItemSkuDO>> skusByItem, String remark) {
        if (StrUtil.isBlank(keyword)) {
            return null;
        }
        AiItem aiItem = new AiItem();
        aiItem.setSku(new AiEntity());
        aiItem.getSku().setCandidates(new ArrayList<>());
        aiItem.setQuantity(quantity);

        List<WmsItemSkuDO> matchedSkus = searchSku(keyword, itemMap, skusByItem);
        List<WmsItemSkuDO> showSkus = matchedSkus.size() > CANDIDATE_LIMIT ? matchedSkus.subList(0, CANDIDATE_LIMIT) : matchedSkus;
        if (showSkus.size() == 1) {
            WmsItemSkuDO sku = showSkus.get(0);
            fillSkuEntity(aiItem.getSku(), sku, itemMap.get(sku.getItemId()));
        } else if (!showSkus.isEmpty()) {
            // 多个规格：给出候选，用户在确认预览中选择
            aiItem.getSku().setName(keyword);
            for (WmsItemSkuDO sku : showSkus) {
                AiEntity cand = new AiEntity();
                fillSkuEntity(cand, sku, itemMap.get(sku.getItemId()));
                aiItem.getSku().getCandidates().add(cand);
            }
        } else {
            aiItem.getSku().setName(keyword);
        }
        if (StrUtil.isNotBlank(remark)) {
            aiItem.setRemark(remark);
        }
        // 单价：优先清单/图片中识别到的价格，其次该 SKU 成本价
        if (price != null) {
            aiItem.setPrice(price);
            warnIfPriceLooksLikeTotal(aiItem, keyword, quantity, price);
        } else if (isResolved(aiItem.getSku()) && aiItem.getSku().getPrice() != null) {
            aiItem.setPrice(aiItem.getSku().getPrice());
        }
        return aiItem;
    }

    /**
     * 价格合理性自检：识别到的「单价」可能是清单上的整行金额。
     *
     * <p>典型误识别：清单金额列写 12000（该行合计），模型把它当成单价 → 确认卡片按
     * 单价×数量 汇总，金额被放大到数量倍。这里与该 SKU 的档案成本价做量级比对，
     * 相差过大时给出提示，让用户在提交前核对（不擅自改数，避免把正确价格改坏）。
     */
    private void warnIfPriceLooksLikeTotal(AiItem aiItem, String keyword, BigDecimal quantity, BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            return;
        }
        BigDecimal ref = null;
        if (isResolved(aiItem.getSku()) && aiItem.getSku().getPrice() != null) {
            ref = aiItem.getSku().getPrice();
        }
        if (ref == null || ref.signum() <= 0) {
            return;
        }
        // 单价高于档案价 20 倍以上，且数量>1（说明很可能把整行金额当单价了）
        if (quantity != null && quantity.compareTo(BigDecimal.ONE) > 0
                && price.compareTo(ref.multiply(BigDecimal.valueOf(20))) > 0) {
            String msg = "「" + StrUtil.blankToDefault(keyword, "该商品") + "」识别到单价 " + price
                    + "，明显高于档案成本价 " + ref + "，可能把清单上的「金额」当成了单价，"
                    + "建议核对后再提交（若清单金额是整行合计，单价应为 金额÷数量）。";
            aiItem.setRemark(StrUtil.isBlank(aiItem.getRemark()) ? msg : aiItem.getRemark() + "；" + msg);
            log.warn("[recognize] 单价疑似整行金额：name={} quantity={} price={} ref={}",
                    keyword, quantity, price, ref);
        }
    }

    /**
     * 商品匹配：优先 SKU 名称/编码/条码；其次商品名称/编码；再按拼音首字母拆分匹配结果合并去重
     */
    private List<WmsItemSkuDO> searchSku(String keyword, Map<Long, WmsItemDO> itemMap,
                                         Map<Long, List<WmsItemSkuDO>> skusByItem) {
        Set<Long> matchedSkuIds = new LinkedHashSet<>();
        List<WmsItemSkuDO> result = new ArrayList<>();
        // A. 商品表命中
        List<WmsItemDO> hitItems = itemMapper.selectList(new LambdaQueryWrapperX<WmsItemDO>()
                .and(q -> q.like(WmsItemDO::getName, keyword).or().like(WmsItemDO::getCode, keyword))
                .orderByDesc(WmsItemDO::getId));
        for (WmsItemDO item : hitItems) {
            List<WmsItemSkuDO> itemSkus = skusByItem.getOrDefault(item.getId(), Collections.emptyList());
            if (itemSkus.isEmpty()) {
                // 兜底：重新查一次
                itemSkus = itemSkuMapper.selectListByItemId(item.getId());
                skusByItem.put(item.getId(), itemSkus);
            }
            for (WmsItemSkuDO sku : itemSkus) {
                if (matchedSkuIds.add(sku.getId())) {
                    result.add(sku);
                }
            }
        }
        // B. SKU 表命中（名称/编码/条码）
        List<WmsItemSkuDO> hitSkus = itemSkuMapper.selectList(new LambdaQueryWrapperX<WmsItemSkuDO>()
                .and(q -> q.like(WmsItemSkuDO::getName, keyword)
                        .or().like(WmsItemSkuDO::getCode, keyword)
                        .or().like(WmsItemSkuDO::getBarCode, keyword))
                .orderByDesc(WmsItemSkuDO::getId));
        for (WmsItemSkuDO sku : hitSkus) {
            if (matchedSkuIds.add(sku.getId())) {
                result.add(sku);
            }
        }
        return result;
    }

    private void fillEntity(AiEntity entity, Long id, String code, String name) {
        entity.setResolved(true);
        entity.setId(id);
        entity.setCode(code);
        entity.setName(name);
    }

    private void fillSkuEntity(AiEntity entity, WmsItemSkuDO sku, WmsItemDO item) {
        entity.setResolved(true);
        entity.setId(sku.getId());
        entity.setCode(sku.getCode());
        entity.setSkuCode(sku.getCode());
        entity.setSkuName(sku.getName());
        entity.setBarCode(sku.getBarCode());
        if (item != null) {
            entity.setItemCode(item.getCode());
            entity.setItemName(item.getName());
            entity.setUnit(item.getUnit());
        }
        entity.setPrice(sku.getCostPrice());
        // 展示名称：商品名称（规格名称）
        StringBuilder name = new StringBuilder();
        if (item != null && StrUtil.isNotBlank(item.getName())) {
            name.append(item.getName());
        }
        if (StrUtil.isNotBlank(sku.getName())) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(sku.getName());
        }
        entity.setName(name.length() > 0 ? name.toString() : sku.getCode());
    }

    /**
     * 匹配不到时，给用户的提示里追加几个相近的商品名示例
     */
    private String buildItemHint(List<WmsItemDO> itemCatalog, String keyword) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        List<String> similar = itemCatalog.stream()
                .filter(i -> StrUtil.isNotBlank(i.getName())
                        && (i.getName().toLowerCase(Locale.ROOT).contains(lower)
                        || lower.contains(StrUtil.nullToEmpty(i.getName()).toLowerCase(Locale.ROOT))))
                .map(WmsItemDO::getName)
                .distinct()
                .limit(3)
                .collect(Collectors.toList());
        return similar.isEmpty() ? "" : "（相近商品：" + String.join("、", similar) + "）";
    }

    private boolean isResolved(AiEntity entity) {
        return entity != null && Boolean.TRUE.equals(entity.getResolved()) && entity.getId() != null;
    }

    private boolean isReady(WmsAiRecognizeRespVO resp) {
        boolean whOk;
        if ("MOVE".equals(resp.getAction())) {
            whOk = isResolved(resp.getSourceWarehouse()) && isResolved(resp.getTargetWarehouse());
        } else {
            whOk = isResolved(resp.getWarehouse());
        }
        if (!whOk || resp.getItems().isEmpty()) {
            return false;
        }
        for (AiItem item : resp.getItems()) {
            // 数量必须为正数：模型未识别到数量时会填 0/null，此前只判 null 会把
            // 「quantity=0」也当作可提交，导致用户提交出零数量的入库单。
            if (!isResolved(item.getSku()) || item.getQuantity() == null
                    || item.getQuantity().signum() <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 关键信息缺失/未匹配时，以对话形式主动反问用户。
     * 仅当候选列表为空时才反问；有候选时仍走确认卡片让用户选择。
     *
     * <p>应对"不清楚的入库/出库要求"：反问时告诉用户系统里现有仓库可直接照着回，
     * 并引导把整句要求一次说全，避免多轮"只问一个字段"的干巴巴对话。
     */
    private String buildClarifyQuestion(WmsAiRecognizeRespVO resp, JSONObject json,
                                        List<WmsWarehouseDO> warehouses) {
        List<String> questions = new ArrayList<>();
        String action = resp.getAction();
        String actionName = "MOVE".equals(action) ? "移库"
                : ("OUT".equals(action) ? "出库" : "入库");

        if ("MOVE".equals(action)) {
            String srcWord = json.getStr("sourceWarehouse", "");
            if (!isResolved(resp.getSourceWarehouse()) && hasNoCandidate(resp.getSourceWarehouse())) {
                questions.add(StrUtil.isBlank(srcWord)
                        ? "还差「移出仓库」：直接回复仓库名即可" + warehousePickText(warehouses)
                        : "“" + srcWord + "”未匹配到仓库，请回复准确的仓库名" + warehousePickText(warehouses));
            }
            String dstWord = json.getStr("targetWarehouse", "");
            if (!isResolved(resp.getTargetWarehouse()) && hasNoCandidate(resp.getTargetWarehouse())) {
                questions.add(StrUtil.isBlank(dstWord)
                        ? "还差「移入仓库」：直接回复仓库名即可" + warehousePickText(warehouses)
                        : "“" + dstWord + "”未匹配到仓库，请回复准确的仓库名" + warehousePickText(warehouses));
            }
        } else if ("IN".equals(action) || "OUT".equals(action)) {
            String whWord = json.getStr("warehouse", "");
            if (!isResolved(resp.getWarehouse()) && hasNoCandidate(resp.getWarehouse())) {
                questions.add(StrUtil.isBlank(whWord)
                        ? "还差「" + ("IN".equals(action) ? "入库仓库" : "出库仓库")
                        + "」：直接回复仓库名即可" + warehousePickText(warehouses)
                        : "“" + whWord + "”未匹配到仓库，请回复准确的仓库名" + warehousePickText(warehouses));
            }
        }

        // 往来企业：仅在明确提到但未匹配时提示（可选字段，未提不强制问）
        String merchantWord = json.getStr("merchant", "");
        if (StrUtil.isNotBlank(merchantWord) && !isResolved(resp.getMerchant()) && hasNoCandidate(resp.getMerchant())) {
            questions.add("“" + merchantWord + "”未匹配到" + ("OUT".equals(action) ? "客户" : "供应商")
                    + "：此项可选，可直接忽略不填，或回复准确的名称。");
        }

        // 商品：有候选时让用户在确认卡里选；完全无候选时才反问
        List<String> notMatchedItems = new ArrayList<>();
        boolean hasItems = false;
        for (AiItem it : resp.getItems()) {
            hasItems = true;
            if (!isResolved(it.getSku()) && hasNoCandidate(it.getSku())) {
                notMatchedItems.add(it.getSku() == null ? "未识别商品" : it.getSku().getName());
            }
        }
        if (!hasItems) {
            questions.add("还差要操作的「商品明细」：请用一句话说全，"
                    + "例如「采购入库 商品名称 20 件 到 " + sampleWarehouseName(warehouses) + "」");
        } else if (!notMatchedItems.isEmpty()) {
            questions.add("“" + String.join("、", notMatchedItems) + "”在【商品管理】中没有匹配到商品："
                    + "请回复准确的商品名称/规格，或到商品管理建档后重发一次。");
        }

        if (questions.isEmpty()) {
            return null;
        }
        return "我还需要你补充一些信息，就能帮你整理出「" + actionName + "」待确认单：\n"
                + questions.stream()
                        .map(q -> "• " + q)
                        .collect(Collectors.joining("\n"))
                + "\n\n提示：可以只回复缺的那一项（例如只回仓库名）；如果缺好几项，"
                + "建议把整句" + actionName + "要求一次说全再发给我，我会重新整理。";
    }

    /** 仓库候选提示：贴出系统内已有仓库名，方便用户照着回复 */
    private String warehousePickText(List<WmsWarehouseDO> warehouses) {
        if (CollUtil.isEmpty(warehouses)) {
            return "（可在「仓库管理」里查看可用仓库）";
        }
        String names = warehouses.stream()
                .map(WmsWarehouseDO::getName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .limit(6)
                .collect(Collectors.joining("、"));
        return StrUtil.isBlank(names) ? "" : "（仓库可选：" + names + "）";
    }

    /** 取一个真实仓库名用于示例话术，避免示例里出现系统不存在的仓库 */
    private String sampleWarehouseName(List<WmsWarehouseDO> warehouses) {
        if (CollUtil.isNotEmpty(warehouses)) {
            for (WmsWarehouseDO w : warehouses) {
                if (w != null && StrUtil.isNotBlank(w.getName())) {
                    return w.getName();
                }
            }
        }
        return "仓库名";
    }

    private boolean hasNoCandidate(AiEntity entity) {
        return entity == null || entity.getCandidates() == null || entity.getCandidates().isEmpty();
    }

    private String buildReply(WmsAiRecognizeRespVO resp) {
        StringBuilder reply = new StringBuilder();
        List<String> lines = new ArrayList<>();
        if ("MOVE".equals(resp.getAction())) {
            reply.append("收到！我来帮你执行「移库」");
            lines.add("移出：" + entityText(resp.getSourceWarehouse(), "仓库"));
            lines.add("移入：" + entityText(resp.getTargetWarehouse(), "仓库"));
        } else {
            String merchant = entityText(resp.getMerchant(), "OUT".equals(resp.getAction()) ? "客户" : "供应商");
            if (StrUtil.isNotBlank(merchant)) {
                lines.add(merchant);
            }
            String wh = entityText(resp.getWarehouse(), "OUT".equals(resp.getAction()) ? "出库仓库" : "目标仓库");
            if (StrUtil.isNotBlank(wh)) {
                lines.add(wh);
            }
        }
        for (AiItem item : resp.getItems()) {
            String skuText = entityText(item.getSku(), "商品");
            lines.add(skuText + " × " + (item.getQuantity() != null ? item.getQuantity().stripTrailingZeros().toPlainString() : "？"));
        }
        reply.append(String.join("\n", lines));
        if (resp.getAction() != null && !"MOVE".equals(resp.getAction()) && resp.getOrderTypeName() != null) {
            reply.insert(0, "【" + resp.getOrderTypeName() + "】\n");
        }
        if (Boolean.FALSE.equals(resp.getReady())) {
            reply.append("\n（还有几项待你确认，见下方预览卡）");
        } else {
            reply.append("\n信息已齐全，确认无误即可一键生成单据。");
        }
        return reply.toString();
    }

    private String entityText(AiEntity entity, String type) {
        if (isResolved(entity)) {
            String remark = StrUtil.isNotBlank(entity.getRemark()) ? "（" + entity.getRemark() + "）" : "";
            return (StrUtil.isBlank(type) ? "" : type + "：") + entity.getName() + remark;
        }
        // 未唯一命中：保留大模型识别出的原始名称，多候选时提示待选规格，避免回复里出现空白项
        String name = entity.getName();
        if (StrUtil.isBlank(name)) {
            return "";
        }
        String prefix = StrUtil.isBlank(type) ? "" : type + "：";
        int candCount = entity.getCandidates() != null ? entity.getCandidates().size() : 0;
        if (candCount > 1) {
            return prefix + name + "（有 " + candCount + " 种规格待选）";
        }
        return prefix + name;
    }

    // ==================== 大模型调用 ====================

    private String buildExtractSystemPrompt(List<WmsWarehouseDO> warehouses,
                                            List<WmsMerchantDO> suppliers,
                                            List<WmsMerchantDO> customers,
                                            List<WmsItemDO> items,
                                            Map<Long, List<WmsItemSkuDO>> skusByItem,
                                            boolean withImages) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是【天信管业 WMS 仓储系统】的智能助手。用户会发来一句中文自然语言指令，例如：\n");
        prompt.append("- “采购入库 200 米 预制直埋保温管 DN200 到 金岩原辅材料库，供应商 河北华美”\n");
        prompt.append("- “把 3 桶 环氧富锌底漆 20kg/桶 放到 原辅材料库”\n");
        prompt.append("- “移库：从 金岩成品库 移 50 件 3PE防腐钢管 DN400 到 工程项目周转库”\n");
        prompt.append("- “出库 10 桶 环氧云铁中间漆 给 天津灯塔”\n");
        prompt.append("\n请严格只输出一个 JSON 对象，不要输出任何解释、注释或 Markdown，格式如下：\n");
        prompt.append("{\"action\":\"IN\",\"orderTypeHint\":\"采购\",\"warehouse\":\"金岩原辅材料库\",\"sourceWarehouse\":\"\",\"targetWarehouse\":\"\",\"merchant\":\"河北华美\",\"remark\":\"\",\"listRows\":0,\"items\":[{\"name\":\"预制直埋保温管 DN200\",\"quantity\":200,\"unit\":\"米\",\"price\":0}]}\n");
        prompt.append("字段说明：\n");
        prompt.append("- action：IN=入库，OUT=出库，MOVE=移库，UNKNOWN=无法构成出入库单（例如闲聊、或用户要求新建/建档商品）。\n");
        prompt.append("- orderTypeHint：入库填 生产/采购/退货/归还；出库填 销售/退货/生产；不明确填空串。\n");
        prompt.append("- warehouse：入库/出库用，填仓库名；MOVE 时此项填空串。用户文字和图片中都没有提到仓库时，必须填空串，严禁猜测或默认填任何仓库。\n");
        prompt.append("- sourceWarehouse / targetWarehouse：仅 MOVE 时分别填移出/移入仓库；用户没提到就填空串，严禁猜测；其余情况都填空串。\n");
        prompt.append("- merchant：入库=供应商名，出库=客户名；没提到填空串。\n");
        prompt.append("- listRows：图片清单的明细总行数（仅图片输入时输出，无图片固定填 0）。\n");
        prompt.append("- items：每种商品一行。name 尽量与下方“商品名录”中的完整名称一致（含规格，如“预制直埋保温管 DN200”）；quantity 为阿拉伯数字；unit 为数量单位；price 为【单价】（纯数字，单位元，来自用户文字或清单原文，没有就填 0，严禁根据商品名猜测）。用户一次说多种商品时拆成多行。中文数字请转阿拉伯数字。\n");
        prompt.append("【price 专项要求——最容易出错的地方】\n");
        prompt.append("  · price 只能是“单个单位的价钱”（单价/报价），绝不能填整行的合计金额。\n");
        prompt.append("  · 清单上若同时有“数量 / 单价 / 金额”三列，price 取【单价】那一列；\n");
        prompt.append("  · 若清单上只有“金额（合计）”而没有单价列，必须先自行计算 单价 = 金额 ÷ 数量 后再填入 price；\n");
        prompt.append("  · 自检：填完后确认 price × quantity ≈ 清单该行金额。若相差十倍/百倍以上，说明取错列了，请改正。\n");
        prompt.append("  · 数量为 0 或缺失时，price 填 0，禁止把该行金额直接当单价。\n");
        prompt.append("- remark：用户额外交代的内容放这里，没有就填空串。\n");
        prompt.append("- 关键：当 action=UNKNOWN 但用户文字或清单文件里有具体商品时，仍必须把这些商品逐行输出到 items（name 取商品名，没有数量时 quantity 填 0）；只有纯提问/闲聊才输出空 items。清单文件内容按「 | 」分隔逐行解析，一个商品一行，不要合并。\n");
        if (withImages) {
            prompt.append("\n===== 图片清单识别特别要求（本次输入附带清单图片）=====\n");
            prompt.append("- 清单可能是打印体、电子表格截图，也可能是【手写】，请结合上下文逐字仔细辨认，绝不臆造。\n");
            prompt.append("- 必须把图片上【每一行】物品都输出到 items，一条不漏、不合并；同一商品不同规格也分行输出。\n");
            prompt.append("- 相同商品若被写成多行（分页/分单），也逐行保留，不可只取一行。\n");
            prompt.append("- 先看图片标题/抬头（如 出库单/送货单/退货单/领料单 等）判断 action 与 orderTypeHint；看不出单据类型时 action=IN、orderTypeHint=采购。\n");
            prompt.append("- 请输出 listRows=图片上清单的明细总行数（商品条目数，不含表头合计行）。\n");
            prompt.append("- 若个别文字实在无法辨认，输出你认为最可能的名称并在 name 末尾加“(存疑)”，不要遗漏该行。\n");
            prompt.append("- 清单上可见的单价/金额列（含手写价格）必须逐行输出到对应商品的 price（纯数字，元）；看不清或没有该列时填 0，严禁编造。\n");
        prompt.append("- 特别注意：若该行只看到“金额/合计/总额”这类整行金额，且同时能看到数量，请输出【单价】= 金额 ÷ 数量，而不是把整行金额填进 price。\n");
            prompt.append("- 图片上没有出现仓库名称（或不确定）时，warehouse 必须填空串；没有出现供应商/客户名称时 merchant 必须填空串，绝不能根据单据内容或下方名录猜测填充。\n");
        }
        prompt.append("\n===== 当前系统仓库名录（请尽量使用其中准确名称）=====\n");
        for (WmsWarehouseDO wh : warehouses) {
            prompt.append("- ").append(wh.getCode()).append(" ").append(wh.getName()).append('\n');
        }
        prompt.append("\n===== 当前系统供应商名录（入库用）=====\n");
        for (WmsMerchantDO m : suppliers) {
            prompt.append("- ").append(m.getName()).append('\n');
        }
        prompt.append("\n===== 当前系统客户名录（出库用）=====\n");
        for (WmsMerchantDO m : customers) {
            prompt.append("- ").append(m.getName()).append('\n');
        }
        prompt.append("\n===== 当前系统商品名录（供你核对名称与规格写法）=====\n");
        for (WmsItemDO item : items) {
            List<WmsItemSkuDO> skus = skusByItem.get(item.getId());
            StringBuilder line = new StringBuilder("- ").append(item.getName());
            if (StrUtil.isNotBlank(item.getUnit())) {
                line.append("（单位：").append(item.getUnit()).append('）');
            }
            if (skus != null) {
                int count = 0;
                for (WmsItemSkuDO sku : skus) {
                    if (count++ >= CATALOG_SKU_LIMIT) {
                        break;
                    }
                    line.append(" / 规格：").append(sku.getName());
                }
            }
            prompt.append(line).append('\n');
        }
        prompt.append("\n如果用户明确提到的商品或仓库名与名录写法不一致，请修正为最接近的名录写法；但用户没有提到的字段一律留空串，绝不能替用户填写或推断。");
        prompt.append(withImages
                ? "现在请先逐行仔细查看用户上传的清单图片，再输出 JSON（务必全收不漏项）。"
                : "现在开始解析用户指令。");
        return prompt.toString();
    }

    /**
     * 调用硅基流动 Chat Completions（OpenAI 兼容），返回 assistant 文本。
     * 传了 images 时走多模态消息格式（text + image_url），供视觉模型识别清单图片。
     */
    private String chatOnce(String modelName, String systemPrompt, String userText, List<String> images) {
        // 每次调用实时解析（支持界面换模型后立即生效）：地址、Key、超时、thinking 都取自当前配置
        WmsAiRuntimeConfig cfg = runtime();
        try {
            JSONObject body = new JSONObject(true);
            body.set("model", modelName);
            body.set("temperature", 0.0);
            body.set("max_tokens", CollUtil.isNotEmpty(images) ? 2048 : 1024);
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.set("role", "system");
            sys.set("content", systemPrompt);
            messages.add(sys);
            JSONObject user = new JSONObject();
            user.set("role", "user");
            if (CollUtil.isNotEmpty(images)) {
                // 多模态：content = [{type:text,...}, {type:image_url,image_url:{url:dataUrl}},...]
                JSONArray contentArr = new JSONArray();
                JSONObject textPart = new JSONObject(true);
                textPart.set("type", "text");
                textPart.set("text", userText);
                contentArr.add(textPart);
                for (String image : images) {
                    if (StrUtil.isBlank(image)) {
                        continue;
                    }
                    JSONObject imagePart = new JSONObject(true);
                    imagePart.set("type", "image_url");
                    JSONObject imageUrl = new JSONObject(true);
                    imageUrl.set("url", image);
                    imagePart.set("image_url", imageUrl);
                    contentArr.add(imagePart);
                }
                user.set("content", contentArr);
            } else {
                user.set("content", userText);
            }
            messages.add(user);
            body.set("messages", messages);
            // 火山方舟等推理模型可通过 thinking.type=disabled 关闭思考链，显著降低延迟
            if (StrUtil.isNotBlank(cfg.getThinkingType())) {
                JSONObject thinking = new JSONObject(true);
                thinking.set("type", cfg.getThinkingType());
                body.set("thinking", thinking);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getBaseUrl()))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            long _t0 = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[chatOnce] done model={} status={} elapsed={}ms",
                    modelName, response.statusCode(), System.currentTimeMillis() - _t0);
            if (response.statusCode() != 200) {
                log.error("[chatOnce] 大模型调用失败，model={}，status={}，body={}", modelName, response.statusCode(), response.body());
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
            log.error("[chatOnce] 大模型调用异常，model={}", modelName, ex);
            return null;
        }
    }

    /**
     * 兼容大模型输出包了 ```json``` / 前后空白等杂质的 JSON 提取
     */
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

    private BigDecimal parseQuantity(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        if (StrUtil.isBlank(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================== 确认建单 ====================

    /**
     * 确认建单：把用户已核对的草稿落库。
     *
     * <p>设计要点：
     * 1) 只接受「已匹配到主数据」的明细行——未匹配的行直接报错，避免 AI 幻觉落库；
     * 2) skuId 缺失时用 itemId 兜底解析（前端候选选中后可能只回填了 itemId）；
     * 3) 落库一律走既有单据 Service，接口层的 @PreAuthorize 权限校验照常生效。
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public WmsAiCreateOrderRespVO createOrder(WmsAiCreateOrderReqVO reqVO) {
        String action = StrUtil.blankToDefault(reqVO.getAction(), "").trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("IN", "OUT", "MOVE", "CHECK").contains(action)) {
            throw new IllegalArgumentException("不支持的单据动作：" + reqVO.getAction());
        }
        if (CollUtil.isEmpty(reqVO.getItems())) {
            throw new IllegalArgumentException("商品明细不能为空");
        }

        // 1. 解析明细行的 SKU（必须有真实 skuId，否则拒绝，避免落脏数据）
        List<SkuLine> lines = new ArrayList<>();
        for (WmsAiCreateOrderReqVO.Item it : reqVO.getItems()) {
            Long skuId = it.getSkuId();
            if (skuId == null && it.getItemId() != null) {
                skuId = resolveFirstSkuId(it.getItemId());
            }
            // 兜底：前端旧缓存或字段映射异常时可能只带商品名，后端自己按名称再查一次
            if (skuId == null && StrUtil.isNotBlank(it.getName())) {
                skuId = resolveSkuIdByKeyword(it.getName());
            }
            if (skuId == null) {
                throw new IllegalArgumentException("商品「"
                        + StrUtil.blankToDefault(it.getName(), "未命名") + "」未匹配到系统商品，无法建单，请在明细中选择");
            }
            if (it.getQuantity() == null || it.getQuantity().signum() <= 0) {
                throw new IllegalArgumentException("商品「"
                        + StrUtil.blankToDefault(it.getName(), "未命名") + "」的数量必须大于 0");
            }
            lines.add(new SkuLine(skuId, it.getQuantity(), it.getPrice()));
        }

        // 2. 按动作分派到既有单据服务
        Long orderId;
        switch (action) {
            case "IN" -> orderId = createReceipt(reqVO, lines);
            case "OUT" -> orderId = createShipment(reqVO, lines);
            case "MOVE" -> {
                // 移库必须明确移出仓与移入仓，且不能相同，否则会生成无意义的单据
                if (reqVO.getWarehouseId() == null) {
                    throw new IllegalArgumentException("移库单缺少「移出仓库」，请在确认卡片中选择");
                }
                if (reqVO.getTargetWarehouseId() == null) {
                    throw new IllegalArgumentException("移库单缺少「移入仓库」，请在确认卡片中选择");
                }
                if (reqVO.getWarehouseId().equals(reqVO.getTargetWarehouseId())) {
                    throw new IllegalArgumentException("移出仓库与移入仓库不能相同");
                }
                orderId = createMovement(reqVO, lines);
            }
            case "CHECK" -> {
                if (reqVO.getWarehouseId() == null) {
                    throw new IllegalArgumentException("盘库单缺少「盘库仓库」，请在确认卡片中选择");
                }
                orderId = createCheck(reqVO, lines);
            }
            default -> throw new IllegalArgumentException("不支持的单据动作：" + reqVO.getAction());
        }
        if (orderId == null) {
            throw new IllegalStateException("单据创建失败，请稍后重试");
        }

        // 3. 回读单号返回给前端
        String orderNo = readOrderNo(action, orderId);
        WmsAiCreateOrderRespVO resp = new WmsAiCreateOrderRespVO();
        resp.setOrderId(orderId);
        resp.setOrderNo(orderNo);
        resp.setReply("已生成" + ORDER_ACTION_NAMES.getOrDefault(action, "单据") + " " + StrUtil.nullToEmpty(orderNo)
                + "（草稿）。" + ORDER_ACTION_HINTS.getOrDefault(action, "请到对应列表补充信息后提交审批。"));
        log.info("[createOrder][AI 建单成功 action({}) orderId({}) orderNo({})]", action, orderId, orderNo);
        return resp;
    }

    /** 动作 → 单据中文名（用于建单成功提示） */
    private static final Map<String, String> ORDER_ACTION_NAMES = Map.of(
            "IN", "入库单", "OUT", "出库单", "MOVE", "移库单", "CHECK", "盘库单");

    /** 动作 → 后续操作提示（各类单据需要补充的信息不同） */
    private static final Map<String, String> ORDER_ACTION_HINTS = Map.of(
            "IN", "请到对应列表补充验收数量后提交审批。",
            "OUT", "请到对应列表核对出库数量后提交审批。",
            "MOVE", "请到移库单列表核对移出/移入仓库后提交审批。",
            "CHECK", "请到盘库单列表填写实盘数量后提交审批。");

    // ==================== AI 辅助审批 ====================

    @Override
    public WmsAiApprovalAdviceRespVO approvalAdvice(WmsAiApprovalAdviceReqVO reqVO) {
        WmsAiApprovalAdviceRespVO resp = new WmsAiApprovalAdviceRespVO();
        String orderType = StrUtil.blankToDefault(reqVO.getOrderType(), "").trim().toUpperCase(Locale.ROOT);
        boolean isReceipt = "RECEIPT".equals(orderType);
        if (!isReceipt && !"SHIPMENT".equals(orderType)) {
            resp.setOk(Boolean.FALSE);
            resp.setErrorMessage("不支持的单据类型：" + reqVO.getOrderType());
            return resp;
        }

        // 1. 取单据主体
        Long warehouseId;
        String orderNo;
        String bizNo;
        String sourceModule;
        Integer approvalStatus;
        List<String> lineTexts = new ArrayList<>();
        List<ApprovalLine> lines = new ArrayList<>();

        if (isReceipt) {
            var order = receiptOrderService.getReceiptOrder(reqVO.getOrderId());
            if (order == null) {
                resp.setOk(Boolean.FALSE);
                resp.setErrorMessage("入库单不存在：" + reqVO.getOrderId());
                return resp;
            }
            warehouseId = order.getWarehouseId();
            orderNo = order.getNo();
            bizNo = order.getBizOrderNo();
            sourceModule = order.getSourceModule();
            approvalStatus = order.getApprovalStatus();
            var details = receiptOrderDetailService.getReceiptOrderDetailList(order.getId());
            for (var d : details) {
                lines.add(new ApprovalLine(d.getSkuId(), d.getQuantity(), d.getActualQuantity(),
                        d.getQualifiedQuantity(), d.getUnqualifiedQuantity(), d.getPrice()));
                lineTexts.add(buildLineText(d.getSkuId(), d.getQuantity(), d.getActualQuantity(),
                        d.getQualifiedQuantity(), d.getUnqualifiedQuantity(), d.getPrice()));
            }
        } else {
            var order = shipmentOrderService.getShipmentOrder(reqVO.getOrderId());
            if (order == null) {
                resp.setOk(Boolean.FALSE);
                resp.setErrorMessage("出库单不存在：" + reqVO.getOrderId());
                return resp;
            }
            warehouseId = order.getWarehouseId();
            orderNo = order.getNo();
            bizNo = order.getBizOrderNo();
            sourceModule = order.getSourceModule();
            approvalStatus = order.getApprovalStatus();
            var details = shipmentOrderDetailService.getShipmentOrderDetailList(order.getId());
            for (var d : details) {
                lines.add(new ApprovalLine(d.getSkuId(), d.getQuantity(), null, null, null, d.getPrice()));
                lineTexts.add(buildLineText(d.getSkuId(), d.getQuantity(), null, null, null, d.getPrice()));
            }
        }

        resp.setDetailCount(lines.size());

        // 2. 后端先算出「事实」（库存、验收、价格），再交给模型归纳，避免模型凭空推断
        WmsWarehouseDO warehouse = warehouseId == null ? null : warehouseMapper.selectById(warehouseId);
        String warehouseName = warehouse == null ? "未指定仓库"
                : StrUtil.blankToDefault(warehouse.getName(), "未命名仓库");

        Map<Long, BigDecimal> stockMap = queryStock(warehouseId, lines);

        // 3. 规则层：先给出确定性风险，模型只做归纳与措辞
        List<WmsAiApprovalAdviceRespVO.Risk> risks = new ArrayList<>();
        StringBuilder facts = new StringBuilder();
        facts.append("单据类型：").append(isReceipt ? "入库单" : "出库单").append('\n');
        facts.append("单号：").append(StrUtil.nullToEmpty(orderNo)).append('\n');
        facts.append("来源业务单号：").append(StrUtil.blankToDefault(bizNo, "无")).append('\n');
        facts.append("来源系统：").append(StrUtil.blankToDefault(sourceModule, "WMS 自建")).append('\n');
        facts.append("仓库：").append(warehouseName).append('\n');
        facts.append("当前审批状态码：").append(approvalStatus).append("（0 待提交 / 1 审批中 / 2 已通过 / 3 已拒绝）").append('\n');
        facts.append("明细行数：").append(lines.size()).append('\n');
        facts.append("明细：\n");
        for (String t : lineTexts) {
            facts.append("  - ").append(t).append('\n');
        }

        // 仓库已关闭
        if (warehouse != null && warehouse.getStatus() != null
                && !CommonStatusEnum.ENABLE.getStatus().equals(warehouse.getStatus())) {
            risks.add(risk("HIGH", "仓库已关闭",
                    "仓库「" + warehouseName + "」当前为关闭状态，不应继续出入库业务"));
        }
        if (lines.isEmpty()) {
            risks.add(risk("HIGH", "单据无明细", "该单据没有任何商品明细，无法执行出入库"));
        }

        for (ApprovalLine line : lines) {
            String skuName = lineTexts.isEmpty() ? String.valueOf(line.skuId()) : skuLabel(line.skuId());
            BigDecimal stock = line.skuId() == null ? null : stockMap.get(line.skuId());
            BigDecimal qty = line.quantity() == null ? BigDecimal.ZERO : line.quantity();

            if (isReceipt) {
                // 入库：实际数 = 合格数 + 不合格数，且不得超过申请数
                BigDecimal actual = line.actualQuantity();
                BigDecimal qualified = line.qualifiedQuantity();
                BigDecimal unqualified = line.unqualifiedQuantity();
                if (actual == null && qualified == null) {
                    risks.add(risk("MEDIUM", "未填验收数量",
                            skuName + " 尚未填写验收数量，审批通过后无法据实入库"));
                } else {
                    BigDecimal q = qualified == null ? BigDecimal.ZERO : qualified;
                    BigDecimal u = unqualified == null ? BigDecimal.ZERO : unqualified;
                    BigDecimal sum = q.add(u);
                    if (actual != null && sum.compareTo(actual) != 0) {
                        risks.add(risk("HIGH", "验收数量不平衡",
                                skuName + " 合格数 " + q + " + 不合格数 " + u + " = " + sum
                                        + "，与实收数 " + actual + " 不一致"));
                    }
                    if (actual != null && actual.compareTo(qty) > 0) {
                        risks.add(risk("HIGH", "验收超出申请数",
                                skuName + " 实收 " + actual + " 大于申请数 " + qty));
                    }
                }
            } else {
                // 出库：库存是否足够
                if (stock == null) {
                    risks.add(risk("MEDIUM", "库存未知",
                            skuName + " 在该仓库查不到库存记录，无法判断是否够出库"));
                } else if (stock.compareTo(qty) < 0) {
                    risks.add(risk("HIGH", "库存不足",
                            skuName + " 当前库存 " + stock + "，本单需出库 " + qty
                                    + "，缺口 " + qty.subtract(stock)));
                }
            }
        }

        // 4. 模型归纳（失败不影响规则层结论）
        String suggestion;
        String summary = null;
        String reason = null;
        boolean hasHigh = risks.stream().anyMatch(r -> "HIGH".equals(r.getLevel()));
        boolean hasMedium = risks.stream().anyMatch(r -> "MEDIUM".equals(r.getLevel()));

        String modelOut = null;
        if (runtime().usable()) {
            try {
                String sys = buildApprovalAdviceSystemPrompt();
                modelOut = chatOnce(runtime().getModel(), sys, facts.toString(), null);
            } catch (Exception e) {
                log.warn("[approvalAdvice][模型调用失败，改用规则结论] orderId({}) err({})",
                        reqVO.getOrderId(), e.getMessage());
            }
        }

        if (StrUtil.isNotBlank(modelOut)) {
            JSONObject json = tryParseJsonObject(modelOut);
            if (json != null) {
                summary = json.getStr("summary");
                reason = json.getStr("reason");
                String s = StrUtil.blankToDefault(json.getStr("suggestion"), "").trim().toUpperCase(Locale.ROOT);
                if (Arrays.asList("APPROVE", "REJECT", "MANUAL").contains(s)) {
                    suggestion = s;
                } else {
                    suggestion = ruleSuggestion(hasHigh, hasMedium, lines.isEmpty());
                }
                // 模型列出的补充风险（去重后追加）
                JSONArray modelRisks = json.getJSONArray("risks");
                if (modelRisks != null) {
                    for (Object o : modelRisks) {
                        if (!(o instanceof JSONObject ro)) {
                            continue;
                        }
                        String title = ro.getStr("title");
                        String detail = ro.getStr("detail");
                        if (StrUtil.isBlank(title)) {
                            continue;
                        }
                        boolean dup = risks.stream().anyMatch(r -> StrUtil.equals(r.getTitle(), title));
                        if (!dup) {
                            risks.add(risk(StrUtil.blankToDefault(ro.getStr("level"), "LOW"), title,
                                    StrUtil.blankToDefault(detail, title)));
                        }
                    }
                }
            } else {
                // 模型没按 JSON 返回时，至少把文本当作摘要
                summary = modelOut.length() > 500 ? modelOut.substring(0, 500) : modelOut;
                suggestion = ruleSuggestion(hasHigh, hasMedium, lines.isEmpty());
            }
        } else {
            suggestion = ruleSuggestion(hasHigh, hasMedium, lines.isEmpty());
        }

        if (hasHigh && !"REJECT".equals(suggestion)) {
            // 有高危风险时不允许给出「建议通过」的乐观结论
            suggestion = "MANUAL";
        }
        if (StrUtil.isBlank(summary)) {
            summary = (isReceipt ? "入库单 " : "出库单 ") + StrUtil.nullToEmpty(orderNo)
                    + "，仓库「" + warehouseName + "」，共 " + lines.size() + " 行明细。";
        }
        if (StrUtil.isBlank(reason)) {
            reason = risks.isEmpty()
                    ? "未发现明显异常，单据关键字段齐全。"
                    : "存在 " + risks.size() + " 项需关注事项，详见风险列表。";
        }

        resp.setSuggestion(suggestion);
        resp.setSuggestionLabel(suggestionLabel(suggestion));
        resp.setSummary(summary);
        resp.setReason(reason);
        resp.setRisks(risks);
        if (!runtime().usable()) {
            resp.setErrorMessage("未配置大模型 API Key，本结论仅由规则生成");
        }
        log.info("[approvalAdvice][orderId({}) type({}) suggestion({}) risks({})]",
                reqVO.getOrderId(), orderType, suggestion, risks.size());
        return resp;
    }

    /** 审批建议用的明细行（兼容入库的验收字段） */
    private record ApprovalLine(Long skuId, BigDecimal quantity, BigDecimal actualQuantity,
                                BigDecimal qualifiedQuantity, BigDecimal unqualifiedQuantity,
                                BigDecimal price) {
    }

    /** 查询这些 SKU 在指定仓库的库存（查不到则不放入 map，调用方据此提示「库存未知」） */
    private Map<Long, BigDecimal> queryStock(Long warehouseId, List<ApprovalLine> lines) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (warehouseId == null || lines.isEmpty()) {
            return map;
        }
        List<Long> skuIds = lines.stream().map(ApprovalLine::skuId).filter(Objects::nonNull).distinct().toList();
        for (Long skuId : skuIds) {
            try {
                var inv = inventoryMapper.selectBySkuIdAndWarehouseId(skuId, warehouseId);
                if (inv != null && inv.getQuantity() != null) {
                    map.put(skuId, inv.getQuantity());
                }
            } catch (Exception e) {
                // 单条库存查询失败不影响整体审批建议
                log.warn("[approvalAdvice][库存查询失败 skuId({}) warehouseId({}) err({})]",
                        skuId, warehouseId, e.getMessage());
            }
        }
        return map;
    }

    private String skuLabel(Long skuId) {
        if (skuId == null) {
            return "未知商品";
        }
        WmsItemSkuDO sku = itemSkuMapper.selectById(skuId);
        if (sku == null) {
            return "SKU(" + skuId + ")";
        }
        String itemName = null;
        if (sku.getItemId() != null) {
            WmsItemDO item = itemMapper.selectById(sku.getItemId());
            itemName = item == null ? null : item.getName();
        }
        return StrUtil.blankToDefault(itemName, "") + " / "
                + StrUtil.blankToDefault(sku.getName(), sku.getCode());
    }

    private String buildLineText(Long skuId, BigDecimal quantity, BigDecimal actual,
                                 BigDecimal qualified, BigDecimal unqualified, BigDecimal price) {
        StringBuilder sb = new StringBuilder();
        sb.append(skuLabel(skuId));
        sb.append("，申请数量 ").append(quantity == null ? "-" : quantity);
        if (actual != null) {
            sb.append("，实收 ").append(actual);
        }
        if (qualified != null || unqualified != null) {
            sb.append("，合格 ").append(qualified == null ? 0 : qualified)
                    .append("，不合格 ").append(unqualified == null ? 0 : unqualified);
        }
        sb.append("，单价 ").append(price == null ? "未提供" : price);
        return sb.toString();
    }

    private WmsAiApprovalAdviceRespVO.Risk risk(String level, String title, String detail) {
        WmsAiApprovalAdviceRespVO.Risk r = new WmsAiApprovalAdviceRespVO.Risk();
        r.setLevel(level);
        r.setTitle(title);
        r.setDetail(detail);
        return r;
    }

    private String ruleSuggestion(boolean hasHigh, boolean hasMedium, boolean empty) {
        if (empty || hasHigh) {
            return "MANUAL";
        }
        return hasMedium ? "MANUAL" : "APPROVE";
    }

    private String suggestionLabel(String suggestion) {
        return switch (suggestion == null ? "" : suggestion) {
            case "APPROVE" -> "建议通过";
            case "REJECT" -> "建议驳回";
            default -> "需要人工判断";
        };
    }

    private JSONObject tryParseJsonObject(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String t = text.trim();
        // 去掉 ```json ``` 包裹
        if (t.startsWith("```")) {
            int first = t.indexOf('\n');
            int last = t.lastIndexOf("```");
            if (first > 0 && last > first) {
                t = t.substring(first + 1, last).trim();
            }
        }
        int s = t.indexOf('{');
        int e = t.lastIndexOf('}');
        if (s < 0 || e <= s) {
            return null;
        }
        try {
            return JSONUtil.parseObj(t.substring(s, e + 1));
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildApprovalAdviceSystemPrompt() {
        return """
                你是仓储单据的审批辅助。审批人会看到你给出的结论，但最终决定由人做出。
                下面会给你一张入库单或出库单的【真实事实】：仓库、明细、库存、验收数量、单价。
                请只依据这些事实判断，不要臆测未提供的信息。

                判断要点：
                - 出库：库存是否足够；数量是否明显异常；单据信息是否缺失。
                - 入库：实收是否等于合格+不合格；验收是否超出申请数量；是否尚未填写验收数量。
                - 仓库是否已关闭；明细是否为空；来源单号是否缺失。
                - 若关键信息不足（如未填验收数量、查不到库存），应给 MANUAL 而不是 APPROVE。

                请严格只输出一个 JSON 对象（不要加解释、不要用 markdown 代码块），字段：
                {
                  "suggestion": "APPROVE | REJECT | MANUAL",
                  "summary": "一到两句中文摘要，说明这是什么单、什么仓库、多少明细",
                  "risks": [ { "level": "HIGH | MEDIUM | LOW", "title": "简短标题", "detail": "一句话说明" } ],
                  "reason": "给出该建议的理由，一句话"
                }
                规则：有硬性错误（库存不足、验收不平衡、验收超申请数、仓库关闭、无明细）时 suggestion 必须是 MANUAL 或 REJECT，不得给 APPROVE。
                """;
    }

    /** 明细行（已解析出真实 SKU） */
    private record SkuLine(Long skuId, BigDecimal quantity, BigDecimal price) {
    }

    /**
     * 生成单据编号，与前端 {@code generateOrderNo} 保持同一格式：前缀 + MMDD + 4 位随机数。
     *
     * <p>单号在入库/出库单里是必填项（后端有唯一性校验），此处生成后仍需依赖
     * 数据库唯一索引兜底；极小概率碰撞时由调用方重试。
     */
    private String generateOrderNo(String prefix) {
        java.time.LocalDate today = java.time.LocalDate.now();
        String mmdd = String.format("%02d%02d", today.getMonthValue(), today.getDayOfMonth());
        return prefix + mmdd + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000));
    }

    /** 用商品编号解析第一个可用 SKU（前端仅回填 itemId 时的兜底） */
    private Long resolveFirstSkuId(Long itemId) {
        List<WmsItemSkuDO> skus = itemSkuMapper.selectListByItemId(itemId);
        return CollUtil.isEmpty(skus) ? null : skus.get(0).getId();
    }

    /**
     * 按商品名 / 商品编码 / SKU 名 兜底解析 SKU。
     *
     * <p>用途：前端若因旧缓存或字段映射异常只提交了展示名称（未带 skuId/itemId），
     * 后端自行按名称匹配一次，避免「未匹配到系统商品」导致建单失败。
     * 匹配不到时返回 null，由调用方给出明确提示。
     */
    private Long resolveSkuIdByKeyword(String keyword) {
        String kw = keyword.trim();
        if (StrUtil.isBlank(kw)) {
            return null;
        }
        // 1) 先按商品名/商品编码精确匹配
        List<WmsItemDO> items = itemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmsItemDO>()
                        .eq(WmsItemDO::getName, kw)
                        .or().eq(WmsItemDO::getCode, kw));
        if (CollUtil.isNotEmpty(items)) {
            Long skuId = resolveFirstSkuId(items.get(0).getId());
            if (skuId != null) {
                return skuId;
            }
        }
        // 2) 名称可能是前端拼接出来的重复串（如"岩棉板 1200×600×50 岩棉板 1200×600×50"），
        //    按空格切分后逐个去重片段，取最长的片段做模糊匹配。
        for (String token : candidateKeywords(kw)) {
            List<WmsItemDO> fuzzy = itemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmsItemDO>()
                            .like(WmsItemDO::getName, token)
                            .or().like(WmsItemDO::getCode, token)
                            .last("LIMIT 1"));
            if (CollUtil.isNotEmpty(fuzzy)) {
                Long skuId = resolveFirstSkuId(fuzzy.get(0).getId());
                if (skuId != null) {
                    return skuId;
                }
            }
        }
        // 3) 最后再试 SKU 名（规格）匹配
        List<WmsItemSkuDO> skus = itemSkuMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmsItemSkuDO>()
                        .like(WmsItemSkuDO::getName, kw)
                        .last("LIMIT 1"));
        return CollUtil.isEmpty(skus) ? null : skus.get(0).getId();
    }

    /**
     * 把可能重复/超长的商品名拆成候选关键词，长的优先。
     *
     * <p>例："岩棉板 1200×600×50 岩棉板 1200×600×50"
     * → ["岩棉板 1200×600×50", "岩棉板", "1200×600×50"]
     */
    private List<String> candidateKeywords(String keyword) {
        String kw = keyword.trim().replaceAll("\\s+", " ");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(kw);
        String[] parts = kw.split(" ");
        // 取前一半（拼接串的前半段就是完整名称）
        if (parts.length >= 2) {
            int half = parts.length / 2;
            out.add(String.join(" ", java.util.Arrays.copyOfRange(parts, 0, half)));
        }
        // 再按单个片段补充（从长到短）
        java.util.Arrays.stream(parts)
                .filter(p -> p.length() >= 2)
                .sorted((a, b) -> b.length() - a.length())
                .forEach(out::add);
        return new ArrayList<>(out.stream()
                .filter(s -> StrUtil.isNotBlank(s) && s.length() >= 2)
                .collect(Collectors.toList()));
    }

    private Long createReceipt(WmsAiCreateOrderReqVO reqVO, List<SkuLine> lines) {
        cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO save =
                new cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO();
        save.setNo(generateOrderNo("RK"));
        save.setType(reqVO.getType() == null
                ? WmsOrderTypeConstants.RECEIPT_PURCHASE : reqVO.getType());
        save.setOrderTime(LocalDateTime.now());
        save.setWarehouseId(reqVO.getWarehouseId());
        save.setMerchantId(reqVO.getMerchantId());
        save.setRemark(StrUtil.blankToDefault(reqVO.getRemark(), "AI 助手建单"));
        save.setDetails(lines.stream().map(l -> {
            var d = new cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.detail.WmsReceiptOrderDetailSaveReqVO();
            d.setSkuId(l.skuId());
            d.setQuantity(l.quantity());
            d.setPrice(l.price());
            // 验收数量先与申请数量一致，避免提交审批时因「实际数≠合格数+不合格数」被拦；
            // 用户仍可在单据页按实际收货情况修改。
            d.setActualQuantity(l.quantity());
            d.setQualifiedQuantity(l.quantity());
            d.setUnqualifiedQuantity(BigDecimal.ZERO);
            return d;
        }).collect(Collectors.toList()));
        return receiptOrderService.createReceiptOrder(save);
    }

    private Long createShipment(WmsAiCreateOrderReqVO reqVO, List<SkuLine> lines) {
        cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderSaveReqVO save =
                new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderSaveReqVO();
        save.setNo(generateOrderNo("CK"));
        save.setType(reqVO.getType() == null
                ? WmsOrderTypeConstants.SHIPMENT_SALE : reqVO.getType());
        save.setOrderTime(LocalDateTime.now());
        save.setWarehouseId(reqVO.getWarehouseId());
        save.setMerchantId(reqVO.getMerchantId());
        save.setRemark(StrUtil.blankToDefault(reqVO.getRemark(), "AI 助手建单"));
        save.setDetails(lines.stream().map(l -> {
            var d = new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.detail.WmsShipmentOrderDetailSaveReqVO();
            d.setSkuId(l.skuId());
            d.setQuantity(l.quantity());
            d.setPrice(l.price());
            return d;
        }).collect(Collectors.toList()));
        return shipmentOrderService.createShipmentOrder(save);
    }

    /** 移库单：复用既有移库服务落库（单号规则、状态机、审批联动与手工建单一致） */
    private Long createMovement(WmsAiCreateOrderReqVO reqVO, List<SkuLine> lines) {
        cn.iocoder.txgy.module.wms.controller.admin.order.movement.vo.order.WmsMovementOrderSaveReqVO save =
                new cn.iocoder.txgy.module.wms.controller.admin.order.movement.vo.order.WmsMovementOrderSaveReqVO();
        save.setNo(generateOrderNo("YK"));
        save.setOrderTime(LocalDateTime.now());
        save.setSourceWarehouseId(reqVO.getWarehouseId());
        save.setTargetWarehouseId(reqVO.getTargetWarehouseId());
        save.setRemark(StrUtil.blankToDefault(reqVO.getRemark(), "AI 助手建单"));
        save.setDetails(lines.stream().map(l -> {
            var d = new cn.iocoder.txgy.module.wms.controller.admin.order.movement.vo.detail.WmsMovementOrderDetailSaveReqVO();
            d.setSkuId(l.skuId());
            d.setQuantity(l.quantity());
            d.setPrice(l.price());
            return d;
        }).collect(Collectors.toList()));
        return movementOrderService.createMovementOrder(save);
    }

    /**
     * 盘库单：复用既有盘库服务落库。
     *
     * <p>盘库的「实盘数量」必须由人现场清点，AI 只能按用户口述的数量预填，
     * 因此这里把 quantity（账面申请数）与 checkQuantity（实盘数）都置为用户给出的数量，
     * 用户到单据页核对后再改。
     */
    private Long createCheck(WmsAiCreateOrderReqVO reqVO, List<SkuLine> lines) {
        cn.iocoder.txgy.module.wms.controller.admin.order.check.vo.order.WmsCheckOrderSaveReqVO save =
                new cn.iocoder.txgy.module.wms.controller.admin.order.check.vo.order.WmsCheckOrderSaveReqVO();
        save.setNo(generateOrderNo("PK"));
        save.setOrderTime(LocalDateTime.now());
        save.setWarehouseId(reqVO.getWarehouseId());
        save.setRemark(StrUtil.blankToDefault(reqVO.getRemark(), "AI 助手建单"));
        save.setDetails(lines.stream().map(l -> {
            var d = new cn.iocoder.txgy.module.wms.controller.admin.order.check.vo.detail.WmsCheckOrderDetailSaveReqVO();
            d.setSkuId(l.skuId());
            d.setQuantity(l.quantity());
            d.setCheckQuantity(l.quantity());
            d.setPrice(l.price());
            return d;
        }).collect(Collectors.toList()));
        return checkOrderService.createCheckOrder(save);
    }

    private String readOrderNo(String action, Long orderId) {
        if ("IN".equals(action)) {
            var order = receiptOrderService.getReceiptOrder(orderId);
            return order == null ? null : order.getNo();
        }
        if ("MOVE".equals(action)) {
            var order = movementOrderService.getMovementOrder(orderId);
            return order == null ? null : order.getNo();
        }
        if ("CHECK".equals(action)) {
            var order = checkOrderService.getCheckOrder(orderId);
            return order == null ? null : order.getNo();
        }
        var order = shipmentOrderService.getShipmentOrder(orderId);
        return order == null ? null : order.getNo();
    }

}
