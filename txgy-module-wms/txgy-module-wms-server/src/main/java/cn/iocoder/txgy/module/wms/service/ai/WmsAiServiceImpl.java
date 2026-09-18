package cn.iocoder.txgy.module.wms.service.ai;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeFileReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO.AiEntity;
import cn.iocoder.txgy.module.wms.controller.admin.ai.vo.WmsAiRecognizeRespVO.AiItem;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
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

    @Value("${txgy.ai.siliconflow.api-key:}")
    private String apiKey;

    @Value("${txgy.ai.siliconflow.base-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String baseUrl;

    @Value("${txgy.ai.siliconflow.model:deepseek-ai/DeepSeek-V4-Flash}")
    private String model;

    /**
     * 拉取前端上传文件（相对路径 URL）时的网关基础地址；容器网络内默认走服务名 gateway
     */
    @Value("${txgy.ai.file-base-url:http://gateway:48080}")
    private String fileBaseUrl;

    /** 视觉模型：识别上传的清单图片（打印/电子表格/手写） */
    @Value("${txgy.ai.siliconflow.vision-model:Qwen/Qwen3-VL-32B-Instruct}")
    private String visionModel;

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
        if (StrUtil.isBlank(apiKey)) {
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
            content = chatOnce(visionModel, buildExtractSystemPrompt(warehouses, suppliers, customers,
                    itemCatalog, skusByItem, true), raw, images);
        } else {
            content = chatOnce(model, buildExtractSystemPrompt(warehouses, suppliers, customers,
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
        if (StrUtil.isBlank(apiKey)) {
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
            resp.setReply(tip + "\n" + StrUtil.nullToDefault(resp.getReply(), ""));
            resp.getTips().add(0, tip);
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
        resp.setTips(Collections.singletonList(reply));
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
            AiItem aiItem = buildAiItem(name, parseQuantity(itemNode.get("quantity")),
                    parseQuantity(itemNode.get("price")), itemMap, skusByItem, "");
            if (aiItem != null) {
                resp.getItems().add(aiItem);
            }
        }
        return resp.getItems().isEmpty() ? null : resp;
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
                    + "· 或回复「分类=电子元器件，单位=个，价格=3.5，重量=0.5kg」，按你指定的信息建档（分类不存在我会自动新建）。\n"
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
                + "- name：这句话要处理/建档的“商品主体名”（如 鸡爪、预制直埋保温管）。\n"
                + "- categoryName：用户明确提到的分类/类别/类目名（如“分类=休闲食品”“休闲食品类”），未提及填空串。\n"
                + "- unit：用户提到的单位（箱/袋/kg/米/件等），未提及填空串。\n"
                + "- brandName：用户提到的品牌，未提及填空串。\n"
                + "- specs：规格名数组，例如用户说“30袋/箱”“DN200”且这是指该商品的规格时放入数组；其余为 [].\n"
                + "- barCode：明确出现的条码，没有填空串。\n"
                + "- costPrice：用户提到的单价/价格/金额（如“单价3.5元”“价格=8块”），输出纯数字字符串；未提及填空串。\n"
                + "- weight：用户明确提到的重量（如“每件10kg”“重500克”），原样输出保留单位；未提及填空串。\n"
                + "注意：全部字段只能来自用户原话，严禁推测或编造；纯闲聊/寒暄时 name 填空串。\n"
                + "用户输入：\n" + text;
        String content = chatOnce(model, prompt, "开始", null);
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
        } else if (isResolved(aiItem.getSku()) && aiItem.getSku().getPrice() != null) {
            aiItem.setPrice(aiItem.getSku().getPrice());
        }
        return aiItem;
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
            if (!isResolved(item.getSku()) || item.getQuantity() == null) {
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
        prompt.append("- items：每种商品一行。name 尽量与下方“商品名录”中的完整名称一致（含规格，如“预制直埋保温管 DN200”）；quantity 为阿拉伯数字；unit 为数量单位；price 为该商品的单价/金额（纯数字，单位元，来自用户文字或清单原文，没有就填 0，严禁根据商品名猜测）。用户一次说多种商品时拆成多行。中文数字请转阿拉伯数字。\n");
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), java.nio.charset.StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

}
