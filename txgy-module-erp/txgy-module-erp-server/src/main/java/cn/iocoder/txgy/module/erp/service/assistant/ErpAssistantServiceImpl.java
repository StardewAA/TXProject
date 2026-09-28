package cn.iocoder.txgy.module.erp.service.assistant;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.txgy.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.controller.admin.assistant.vo.ErpAssistantActionVO;
import cn.iocoder.txgy.module.erp.controller.admin.assistant.vo.ErpAssistantChatRespVO;
import cn.iocoder.txgy.module.erp.dal.mysql.assistant.ErpAssistantMapper;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.statistics.ErpPurchaseStatisticsService;
import cn.iocoder.txgy.module.erp.service.statistics.ErpSaleStatisticsService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * ERP 智能助手 Service 实现
 *
 * <p>实现思路（结合实际、不依赖外部大模型）：</p>
 * <ol>
 *     <li><b>先查数据</b>：把用户输入剥离口语词后，依次在销售订单、采购订单、产品、客商里模糊匹配
 *         （单号 / 客户名 / 供应商名 / 产品名 / 物料编码都能命中），命中就直接返回真实单据或主数据；</li>
 *     <li><b>再判意图</b>：没命中数据时，按关键词识别意图（库存 / 待办 / 销售统计 / 采购统计 / 业务入口）；</li>
 *     <li><b>兜底</b>：返回能力说明与各工作台快捷入口。</li>
 * </ol>
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpAssistantServiceImpl implements ErpAssistantService {

    /** 单次返回的最大命中条数 */
    private static final int LIMIT = 5;

    /** 统计 SQL 的 beginTime 不支持为空，"累计"用哨兵时间代替 */
    private static final LocalDateTime ALL_BEGIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);

    /** 库存意图下需要剔除的意图词，剩下的部分作为产品关键词 */
    private static final String[] STOCK_NOISE_WORDS = {
            "库存", "存货", "存量", "仓库里", "仓库", "还有多少", "有多少", "多少",
            "查一下", "查询", "查", "请问", "帮我", "看一下", "看看", "的", "？", "?", "。", "，", ","};

    /**
     * 数据检索前需要剥离的口语词
     *
     * <p>用户习惯整句提问（如「查一下 XSDD-2026-0003」），若直接拿整句去 LIKE 会一条都匹配不到，
     * 所以先剥离这些词得到真正的检索词。</p>
     */
    private static final String[] SEARCH_NOISE_WORDS = {
            "帮我查一下", "帮我查询", "帮我找一下", "帮我看看", "帮我", "请问", "麻烦", "我想",
            "查一下", "查询一下", "查询", "找一下", "查找", "搜一下", "看一下", "看看", "查", "找", "搜",
            "单号", "单据", "单子", "这个月", "今天", "现在", "的", "？", "?", "。", "，", ",", "！", "!"};

    @Resource
    private ErpAssistantMapper assistantMapper;
    @Resource
    private ErpSaleStatisticsService saleStatisticsService;
    @Resource
    private ErpPurchaseStatisticsService purchaseStatisticsService;

    @Override
    public ErpAssistantChatRespVO chat(String message) {
        String raw = message == null ? "" : message.trim();
        if (raw.isEmpty()) {
            return help();
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();

        // 1) 先按关键词找真实数据：单据 → 产品 → 客商
        //    用户习惯整句提问（「查一下 XSDD-2026-0003」），所以先剥离「查一下/帮我/看看」等口语词，
        //    再用「整句 + 剥离后 + 切词」多个候选关键词依次检索，避免整句 LIKE 导致一条都命中不了。
        List<String> keywords = keywords(raw);
        for (String keyword : keywords) {
            String queryKeyword = escapeLikeKeyword(keyword);
            List<Map<String, Object>> saleOrders = assistantMapper.selectSaleOrders(tenantId, queryKeyword, LIMIT);
            if (!saleOrders.isEmpty()) {
                return saleOrderAnswer(keyword, saleOrders);
            }
            List<Map<String, Object>> purchaseOrders = assistantMapper.selectPurchaseOrders(tenantId, queryKeyword, LIMIT);
            if (!purchaseOrders.isEmpty()) {
                return purchaseOrderAnswer(keyword, purchaseOrders);
            }
        }
        for (String keyword : keywords) {
            List<Map<String, Object>> products = assistantMapper.selectProducts(tenantId, escapeLikeKeyword(keyword), LIMIT);
            if (!products.isEmpty()) {
                return productAnswer(keyword, products);
            }
        }
        for (String keyword : keywords) {
            List<Map<String, Object>> partners = assistantMapper.selectPartners(tenantId, escapeLikeKeyword(keyword), LIMIT);
            if (!partners.isEmpty()) {
                return partnerAnswer(keyword, partners);
            }
        }

        // 2) 「打开 / 进入某个页面」的意图
        ErpAssistantChatRespVO openPage = openPageAnswer(raw);
        if (openPage != null) {
            return openPage;
        }

        // 3) 再按意图回答
        if (containsAny(raw, "库存", "存货", "存量", "仓库")) {
            return stockAnswer(tenantId, raw);
        }
        if (containsAny(raw, "待办", "待审", "审批中", "要审", "待我处理")) {
            return todoAnswer(tenantId);
        }
        if (containsAny(raw, "销售", "卖了多少", "业绩", "绩效", "回款", "营业额", "收入")) {
            return saleStatAnswer();
        }
        if (containsAny(raw, "采购", "花了多少", "进货")) {
            return purchaseStatAnswer();
        }
        if (raw.contains("供应商")) {
            return pageAnswer("SALE_HELP", "我可以帮你查供应商。试试直接说供应商名称，例如「查一下 西安建元」；"
                    + "也可以打开供应商信息页按条件筛选。",
                    action("打开供应商信息", "/erp/purchase/supplier", "支持按名称/编码筛选"));
        }
        if (raw.contains("客户")) {
            return pageAnswer("SALE_HELP", "我可以帮你查客户。试试直接说客户名称，例如「查一下 宝鸡石油」；"
                    + "也可以打开客户信息页按条件筛选。",
                    action("打开客户信息", "/erp/sale/customer", "支持按名称/编码筛选"));
        }
        if (containsAny(raw, "订单", "单据", "单子")) {
            return pageAnswer("SALE_HELP", "我可以按单号直接找单据。试试输入完整单号，例如「查一下 XSDD-2026-0003」。",
                    action("打开销售订单", "/erp/sale/order", "按单号/客户筛选"),
                    action("打开采购订单", "/erp/purchase/order", "按单号/供应商筛选"));
        }

        // 3) 兜底
        return help();
    }

    // ==================== 数据命中类回答 ====================

    private ErpAssistantChatRespVO saleOrderAnswer(String keyword, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(rows.size()).append(" 张销售订单（关键词：").append(keyword).append("）：");
        for (Map<String, Object> row : rows) {
            sb.append("\n· ").append(row.get("no"))
                    .append("（").append(auditStatusName(intValue(row.get("status")))).append("）")
                    .append(" 客户：").append(nullToDash(row.get("customerName")))
                    .append(" 金额：").append(amount(row.get("totalPrice"))).append(" 元");
        }
        return new ErpAssistantChatRespVO().setIntent("SALE_ORDER").setAnswer(sb.toString())
                .setActions(List.of(action("打开销售订单", "/erp/sale/order", "按单号 " + keyword + " 过滤")));
    }

    private ErpAssistantChatRespVO purchaseOrderAnswer(String keyword, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(rows.size()).append(" 张采购订单（关键词：").append(keyword).append("）：");
        for (Map<String, Object> row : rows) {
            sb.append("\n· ").append(row.get("no"))
                    .append("（").append(auditStatusName(intValue(row.get("status")))).append("）")
                    .append(" 供应商：").append(nullToDash(row.get("supplierName")))
                    .append(" 金额：").append(amount(row.get("totalPrice"))).append(" 元");
        }
        return new ErpAssistantChatRespVO().setIntent("PURCHASE_ORDER").setAnswer(sb.toString())
                .setActions(List.of(action("打开采购订单", "/erp/purchase/order", "按单号 " + keyword + " 过滤")));
    }

    private ErpAssistantChatRespVO productAnswer(String keyword, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(rows.size()).append(" 个产品（关键词：").append(keyword).append("）：");
        for (Map<String, Object> row : rows) {
            sb.append("\n· ").append(row.get("name"))
                    .append("（物料编码 ").append(nullToDash(row.get("materialCode"))).append("）")
                    .append(" 售价：").append(amount(row.get("salePrice"))).append(" 元");
        }
        return new ErpAssistantChatRespVO().setIntent("PRODUCT").setAnswer(sb.toString())
                .setActions(List.of(
                        action("打开产品信息", "/erp/product/product", "按名称/编码筛选"),
                        action("查看产品库存", "/erp/stock/stock", "按产品查看库存")));
    }

    private ErpAssistantChatRespVO partnerAnswer(String keyword, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(rows.size()).append(" 条客商记录（关键词：").append(keyword).append("）：");
        for (Map<String, Object> row : rows) {
            sb.append("\n· [").append(row.get("partnerType")).append("] ").append(row.get("name"));
        }
        return new ErpAssistantChatRespVO().setIntent("PARTNER").setAnswer(sb.toString())
                .setActions(List.of(
                        action("打开客户信息", "/erp/sale/customer", "按名称筛选"),
                        action("打开供应商信息", "/erp/purchase/supplier", "按名称筛选")));
    }

    private ErpAssistantChatRespVO stockAnswer(Long tenantId, String raw) {
        String filter = stripNoiseWords(raw);
        List<Map<String, Object>> rows = assistantMapper.selectStocks(tenantId, escapeLikeKeyword(filter), LIMIT);
        StringBuilder sb = new StringBuilder();
        if (!rows.isEmpty() && filter.isEmpty()) {
            sb.append("当前库存量最高的 ").append(rows.size()).append(" 个产品：");
        } else if (!rows.isEmpty()) {
            sb.append("与「").append(filter).append("」相关的库存 ").append(rows.size()).append(" 条：");
        }
        if (rows.isEmpty()) {
            // 关键词没命中（例如产品名记错了）：退回「库存量最高的产品」，比只回一句"没找到"更有用
            rows = assistantMapper.selectStocks(tenantId, "", LIMIT);
            if (!rows.isEmpty()) {
                sb.append("没有找到与「").append(filter).append("」匹配的库存，当前库存量最高的 ").append(rows.size())
                        .append(" 个产品如下（可换产品名或物料编码再问）：");
            }
        }
        for (Map<String, Object> row : rows) {
            sb.append("\n· ").append(row.get("productName"))
                    .append("（").append(nullToDash(row.get("materialCode"))).append("）")
                    .append(" @").append(nullToDash(row.get("warehouseName")))
                    .append("：").append(qty(row.get("count")));
        }
        if (rows.isEmpty()) {
            sb.append("当前还没有任何库存记录，可以先做采购入库或期初库存导入。");
        }
        return new ErpAssistantChatRespVO().setIntent("STOCK").setAnswer(sb.toString())
                .setActions(List.of(
                        action("打开产品库存", "/erp/stock/stock", "按产品/仓库查看"),
                        action("打开出入库明细", "/erp/stock/record", "查库存流水")));
    }

    private ErpAssistantChatRespVO todoAnswer(Long tenantId) {
        Long pending = assistantMapper.countPendingApprovals(tenantId);
        String answer = "当前审批中的单据共 " + (pending == null ? 0 : pending) + " 张"
                + "（统计口径：销售订单 + 采购订单 + 采购申请 + 询价单）。\n打开审批管理可以按状态筛选并处理。";
        return new ErpAssistantChatRespVO().setIntent("TODO").setAnswer(answer)
                .setActions(List.of(action("打开审批管理", "/erp/approval", "按状态分组查看")));
    }

    private ErpAssistantChatRespVO saleStatAnswer() {
        BigDecimal today = saleStatisticsService.getSalePrice(LocalDateTimeUtils.getToday(), null);
        BigDecimal last7 = saleStatisticsService.getSalePrice(
                LocalDateTimeUtil.beginOfDay(LocalDateTime.now().minusDays(6)), null);
        BigDecimal last30 = saleStatisticsService.getSalePrice(
                LocalDateTimeUtil.beginOfDay(LocalDateTime.now().minusDays(29)), null);
        BigDecimal all = saleStatisticsService.getSalePrice(ALL_BEGIN_TIME, null);
        String answer = "销售情况（口径：销售出库金额 − 销售退货金额）"
                + "\n· 今日：" + amount(today) + " 元"
                + "\n· 近 7 天：" + amount(last7) + " 元"
                + "\n· 近 30 天：" + amount(last30) + " 元"
                + "\n· 累计：" + amount(all) + " 元";
        return new ErpAssistantChatRespVO().setIntent("SALE_STAT").setAnswer(answer)
                .setActions(List.of(
                        action("打开销售绩效", "/erp/performance", "含销售员与回款归因"),
                        action("打开销售订单", "/erp/sale/order", "查看单据明细")));
    }

    private ErpAssistantChatRespVO purchaseStatAnswer() {
        BigDecimal today = purchaseStatisticsService.getPurchasePrice(LocalDateTimeUtils.getToday(), null);
        BigDecimal last7 = purchaseStatisticsService.getPurchasePrice(
                LocalDateTimeUtil.beginOfDay(LocalDateTime.now().minusDays(6)), null);
        BigDecimal last30 = purchaseStatisticsService.getPurchasePrice(
                LocalDateTimeUtil.beginOfDay(LocalDateTime.now().minusDays(29)), null);
        BigDecimal all = purchaseStatisticsService.getPurchasePrice(ALL_BEGIN_TIME, null);
        String answer = "采购情况（口径：采购入库金额 − 采购退货金额）"
                + "\n· 今日：" + amount(today) + " 元"
                + "\n· 近 7 天：" + amount(last7) + " 元"
                + "\n· 近 30 天：" + amount(last30) + " 元"
                + "\n· 累计：" + amount(all) + " 元";
        return new ErpAssistantChatRespVO().setIntent("PURCHASE_STAT").setAnswer(answer)
                .setActions(List.of(action("打开采购订单", "/erp/purchase/order", "查看采购单据")));
    }

    // ==================== 打开页面类意图 ====================

    /** 识别「打开 XX / 进入 XX / 跳转到 XX」这类导航意图，返回带跳转按钮的回答；识别不到返回 null */
    private ErpAssistantChatRespVO openPageAnswer(String raw) {
        if (!containsAny(raw, "打开", "进入", "跳转", "去哪", "看一下", "看看", "帮我到")) {
            return null;
        }
        if (raw.contains("销售订单")) {
            return openPage("销售订单", "/erp/sale/order", "按单号/客户筛选");
        }
        if (raw.contains("采购订单")) {
            return openPage("采购订单", "/erp/purchase/order", "按单号/供应商筛选");
        }
        if (raw.contains("采购申请")) {
            return openPage("采购申请", "/erp/purchase/request", "提交与审批采购需求");
        }
        if (raw.contains("采购计划")) {
            return openPage("采购计划", "/erp/purchase/plan", "编制与审批采购计划");
        }
        if (raw.contains("采购合同")) {
            return openPage("采购合同", "/erp/purchase/contract", "合同签订与履约");
        }
        if (containsAny(raw, "到货检验", "来料检验", "收货检验", "质检")) {
            return openPage("到货检验", "/erp/purchase/inspection", "到货验收与不合格处理");
        }
        if (containsAny(raw, "供应商评价", "供应商考核", "供应商评分")) {
            return openPage("供应商评价", "/erp/purchase/evaluation", "供应商绩效评分");
        }
        if (containsAny(raw, "发票", "进项", "销项")) {
            return openPage("发票管理", "/erp/finance/invoice", "进项/销项发票登记与认证");
        }
        if (raw.contains("询价")) {
            return openPage("采购询价", "/erp/purchase/inquiry", "发起询价与比价");
        }
        if (raw.contains("定标")) {
            return openPage("定标管理", "/erp/purchase/award", "查看定标结果");
        }
        if (containsAny(raw, "造价", "成本")) {
            return openPage("销售造价", "/erp/sale/cost", "造价→销售订单的起点");
        }
        if (raw.contains("退货")) {
            return openPage("退货管理", "/erp/sale/return", "销售退货登记");
        }
        if (containsAny(raw, "库存", "存量")) {
            return openPage("产品库存", "/erp/stock/stock", "按产品查库存");
        }
        if (raw.contains("仓库")) {
            return openPage("仓库信息", "/erp/stock/warehouse", "仓库与库位维护");
        }
        if (containsAny(raw, "盘点", "出入库", "出入库记录")) {
            return openPage("库存盘点", "/erp/stock/check", "盘点与三方比对");
        }
        if (containsAny(raw, "审批", "待办", "待我处理")) {
            return openPage("审批管理", "/erp/approval", "待办与已办审批");
        }
        if (containsAny(raw, "协同", "供应链")) {
            return openPage("供应链协同", "/erp/collaboration", "上下游单据协同");
        }
        if (containsAny(raw, "物流", "发货", "运输")) {
            return openPage("物流管理", "/erp/logistics", "发货与在途跟踪");
        }
        if (containsAny(raw, "绩效", "业绩", "统计")) {
            return openPage("销售绩效", "/erp/performance", "个人/团队业绩统计");
        }
        if (raw.contains("产品") || raw.contains("物料")) {
            return openPage("产品信息", "/erp/product/product", "产品与物料编码");
        }
        if (raw.contains("客户")) {
            return openPage("客户信息", "/erp/sale/customer", "客户档案");
        }
        if (raw.contains("供应商")) {
            return openPage("供应商信息", "/erp/purchase/supplier", "供应商档案");
        }
        if (raw.contains("付款")) {
            return openPage("付款管理", "/erp/finance/payment", "付款单据");
        }
        if (raw.contains("收款")) {
            return openPage("收款管理", "/erp/finance/receipt", "收款单据");
        }
        if (containsAny(raw, "首页", "工作台", "大屏")) {
            return openPage("系统首页", "/erp/home", "统计与快捷入口");
        }
        return null;
    }

    private ErpAssistantChatRespVO openPage(String label, String path, String hint) {
        return pageAnswer("OPEN_PAGE", "好的，正在为你打开「" + label + "」。",
                action("打开" + label, path, hint));
    }

    // ==================== 兜底与工具方法 ====================

    private ErpAssistantChatRespVO help() {
        String answer = "你好，我可以基于 ERP 的真实数据帮你做事，试试这样问："
                + "\n· 找单据：查一下 XSDD-2026-0003"
                + "\n· 找客户/供应商：查一下 宝鸡石油"
                + "\n· 找产品：查一下 镀铝锌彩钢板"
                + "\n· 看库存：镀铝锌彩钢板还有多少库存"
                + "\n· 看经营：今天的销售额是多少 / 这个月采购了多少"
                + "\n· 看待办：现在有多少待审批的单据";
        return new ErpAssistantChatRespVO().setIntent("HELP").setAnswer(answer).setActions(List.of(
                action("系统首页", "/erp/home", "统计与快捷入口"),
                action("销售订单", "/erp/sale/order", "按单号/客户查"),
                action("采购订单", "/erp/purchase/order", "按单号/供应商查"),
                action("产品库存", "/erp/stock/stock", "按产品查库存"),
                action("审批管理", "/erp/approval", "处理待办")));
    }

    private ErpAssistantChatRespVO pageAnswer(String intent, String answer, ErpAssistantActionVO... actions) {
        return new ErpAssistantChatRespVO().setIntent(intent).setAnswer(answer).setActions(List.of(actions));
    }

    private ErpAssistantActionVO action(String label, String path, String hint) {
        return new ErpAssistantActionVO().setLabel(label).setPath(path).setHint(hint);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 去掉意图词，剩下的部分作为产品关键词 */
    /**
     * 生成检索用的候选关键词：整句 → 剥离口语词 → 再按空白/标点切词
     *
     * <p>按顺序去重，调用方依次尝试，命中即返回。</p>
     */
    private List<String> keywords(String raw) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String text = raw.trim();
        if (!text.isEmpty()) {
            set.add(text);
        }
        String cleaned = text;
        for (String word : SEARCH_NOISE_WORDS) {
            cleaned = cleaned.replace(word, "");
        }
        cleaned = cleaned.trim();
        if (cleaned.length() >= 2) {
            set.add(cleaned);
        }
        for (String part : cleaned.split("[\\s,，。、;；:：/\\\\|]+")) {
            String item = part.trim();
            if (item.length() >= 2) {
                set.add(item);
            }
        }
        return new ArrayList<>(set);
    }

    private String stripNoiseWords(String raw) {
        String result = raw;
        for (String word : STOCK_NOISE_WORDS) {
            result = result.replace(word, "");
        }
        return result.trim();
    }

    /** 转义 LIKE 通配符，防止用户输入 % / _ 退化成大范围扫描。 */
    private String escapeLikeKeyword(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String auditStatusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        for (ErpAuditStatus item : ErpAuditStatus.values()) {
            if (item.getStatus().equals(status)) {
                return item.getName();
            }
        }
        return String.valueOf(status);
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }

    private String nullToDash(Object value) {
        return value == null || value.toString().isEmpty() ? "-" : value.toString();
    }

    /** 金额：两位小数 + 千分位 */
    private String amount(Object value) {
        if (value == null) {
            return "0.00";
        }
        BigDecimal decimal = value instanceof BigDecimal bigDecimal
                ? bigDecimal : new BigDecimal(value.toString());
        return String.format("%,.2f", decimal);
    }

    /** 数量：去掉多余的 0 */
    private String qty(Object value) {
        if (value == null) {
            return "0";
        }
        BigDecimal decimal = value instanceof BigDecimal bigDecimal
                ? bigDecimal : new BigDecimal(value.toString());
        return decimal.stripTrailingZeros().toPlainString();
    }

}
