package cn.iocoder.txgy.module.crm.service.contract;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractConfigDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.contract.CrmContractProductDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.product.CrmProductDO;
import cn.iocoder.txgy.module.crm.dal.mysql.contract.CrmContractMapper;
import cn.iocoder.txgy.module.crm.dal.mysql.contract.CrmContractProductMapper;
import cn.iocoder.txgy.module.crm.enums.contract.CrmContractTemplateTypeEnum;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.service.product.CrmProductService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.CONTRACT_NOT_EXISTS;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * CRM 合同 Word 生成 Service 实现类
 *
 * 实现思路：
 * 1. 读取 resources 下与模板类型对应的 docx 模板（模板中需填写处已替换为 {{占位符}}）；
 * 2. 先处理「货物明细」表格的行循环（按产品数量复制模板行并逐行填充）；
 * 3. 再全文档（段落 + 表格）替换其余占位符；
 * 4. 最后清理未填充的占位符，避免模板标记残留到正式合同中。
 *
 * @author dhb52
 */
@Service
@Slf4j
public class CrmContractWordServiceImpl implements CrmContractWordService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{[^}]*}}");

    /**
     * 货物明细模板行的标记
     */
    private static final String PRODUCT_ROW_MARK = "{{productName}}";

    @Resource
    private CrmContractMapper contractMapper;
    @Resource
    private CrmContractProductMapper contractProductMapper;
    @Resource
    private CrmContractConfigService contractConfigService;
    @Resource
    private CrmProductService productService;
    @Resource
    private CrmCustomerService customerService;

    @Override
    public byte[] generateContractWord(Long id) {
        CrmContractDO contract = contractMapper.selectById(id);
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }

        Map<String, String> data = buildData(contract);
        String templatePath = CrmContractTemplateTypeEnum.getTemplatePathByType(contract.getTemplateType());
        try (InputStream is = new ClassPathResource(templatePath).getInputStream()) {
            XWPFDocument doc = new XWPFDocument(is);
            // 1) 货物明细：行循环
            fillProductTable(doc, contract);
            // 2) 其余占位符
            replaceInDocument(doc, data);
            // 3) 清理残留占位符
            cleanRemainingPlaceholders(doc);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            doc.close();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[generateContractWord][合同({}) Word 生成失败]", id, e);
            throw new ServiceException(500, StrUtil.format("合同 Word 文档生成失败：{}", e.getMessage()));
        }
    }

    // ==================== 占位符数据构建 ====================

    private Map<String, String> buildData(CrmContractDO contract) {
        Map<String, String> data = new HashMap<>();
        CrmContractConfigDO config = contractConfigService.getContractConfig();

        // ---------- 表头 ----------
        data.put("contractNo", str(contract.getNo()));
        data.put("signPlace", str(contract.getSignPlace()));
        LocalDateTime signDate = contract.getSignDate() != null ? contract.getSignDate() : contract.getOrderDate();
        data.put("signDate", formatDate(signDate));
        putDateParts(data, "sign", signDate);

        // ---------- 项目信息 ----------
        data.put("projectNo", str(contract.getProjectNo()));
        data.put("projectName", str(contract.getProjectName()));
        data.put("projectArea", str(contract.getProjectArea()));
        putInt(data, "attachmentCopies", contract.getAttachmentCopies(), config, "attachmentCopies");
        putInt(data, "attachmentPages", contract.getAttachmentPages(), config, "attachmentPages");
        putInt(data, "contractCopies", contract.getContractCopies(), config, "contractCopies");
        data.put("gpcName", defaultStr(contract.getGpcName(), config == null ? null : config.getDefaultGpcName()));

        // ---------- 质量、交货与验收 ----------
        data.put("qualityStandard",
                defaultStr(contract.getQualityStandard(), config == null ? null : config.getDefaultQualityStandard()));
        putInt(data, "sampleDays", contract.getSampleDays(), config, "sampleDays");
        putInt(data, "deliveryDays", contract.getDeliveryDays(), config, "deliveryDays");
        putInt(data, "deliveryArriveDays", contract.getDeliveryArriveDays(), config, "deliveryArriveDays");
        putInt(data, "installDays", contract.getInstallDays(), config, "installDays");
        putDateParts(data, "deadline", resolveDeliveryDeadline(contract, config));
        putInt(data, "firstAcceptDays", contract.getFirstAcceptDays(), config, "firstAcceptDays");
        putInt(data, "trialDays", contract.getTrialDays(), config, "trialDays");
        putInt(data, "finalAcceptDays", contract.getFinalAcceptDays(), config, "finalAcceptDays");
        putInt(data, "autoAcceptDays", contract.getAutoAcceptDays(), config, "autoAcceptDays");
        putInt(data, "maxRepairTimes", contract.getMaxRepairTimes(), config, "maxRepairTimes");

        // ---------- 金额与付款 ----------
        BigDecimal total = contract.getTotalPrice() != null ? contract.getTotalPrice() : BigDecimal.ZERO;
        data.put("totalPrice", formatMoney(total));
        data.put("totalPriceCn", toChineseAmount(total));

        // 比例：合同未填时取配置默认值；金额未填时再按「总价 × 比例」自动计算，
        // 因此金额计算必须使用回退后的比例，否则会出现「比例 60% 但金额 0.00」的矛盾。
        BigDecimal prepayRatio = nvl(contract.getPrepayRatio(), config == null ? null : config.getDefaultPrepayRatio());
        putDecimal(data, "prepayRatio", prepayRatio, config, "prepayRatio");
        putInt(data, "prepayDays", contract.getPrepayDays(), config, "prepayDays");
        BigDecimal prepayAmount = defaultAmount(contract.getPrepayAmount(), total, prepayRatio);
        data.put("prepayAmount", formatMoney(prepayAmount));
        data.put("prepayAmountCn", toChineseAmount(prepayAmount));

        putInt(data, "acceptPayDays", contract.getAcceptPayDays(), config, "acceptPayDays");
        BigDecimal acceptPayRatio = nvl(contract.getAcceptPayRatio(), config == null ? null : config.getDefaultAcceptPayRatio());
        putDecimal(data, "acceptPayRatio", acceptPayRatio, config, "acceptPayRatio");
        BigDecimal acceptPayAmount = defaultAmount(contract.getAcceptPayAmount(), total, acceptPayRatio);
        data.put("acceptPayAmount", formatMoney(acceptPayAmount));
        data.put("acceptPayAmountCn", toChineseAmount(acceptPayAmount));

        putInt(data, "depositMonths", contract.getDepositMonths(), config, "depositMonths");
        putInt(data, "depositDays", contract.getDepositDays(), config, "depositDays");
        BigDecimal depositRatio = nvl(contract.getDepositRatio(), config == null ? null : config.getDefaultDepositRatio());
        putDecimal(data, "depositRatio", depositRatio, config, "depositRatio");
        BigDecimal depositAmount = defaultAmount(contract.getDepositAmount(), total, depositRatio);
        data.put("depositAmount", formatMoney(depositAmount));
        data.put("depositAmountCn", toChineseAmount(depositAmount));

        // ---------- 售后服务 ----------
        putInt(data, "warrantyYears", contract.getWarrantyYears(), config, "warrantyYears");
        putInt(data, "responseHours", contract.getResponseHours(), config, "responseHours");
        putInt(data, "repairHours", contract.getRepairHours(), config, "repairHours");
        putInt(data, "repairTimes", contract.getRepairTimes(), config, "repairTimes");
        putInt(data, "reportRepairDays", contract.getReportRepairDays(), config, "reportRepairDays");

        // ---------- 双方主体信息 ----------
        putParty(data, contract, "partyA");
        putParty(data, contract, "partyB");
        fillCustomerFallback(data, contract);
        fillOurPartyFromConfig(data, contract, config);

        // ---------- 违约责任 ----------
        putPenaltyTerms(data, contract, config);

        return data;
    }

    private void putParty(Map<String, String> data, CrmContractDO contract, String prefix) {
        boolean isA = "partyA".equals(prefix);
        data.put(prefix + "Name", str(isA ? contract.getPartyAName() : contract.getPartyBName()));
        data.put(prefix + "LegalPerson", str(isA ? contract.getPartyALegalPerson() : contract.getPartyBLegalPerson()));
        data.put(prefix + "Agent", str(isA ? contract.getPartyAAgent() : contract.getPartyBAgent()));
        data.put(prefix + "Address", str(isA ? contract.getPartyAAddress() : contract.getPartyBAddress()));
        data.put(prefix + "BankName", str(isA ? contract.getPartyABankName() : contract.getPartyBBankName()));
        data.put(prefix + "BankAccount", str(isA ? contract.getPartyABankAccount() : contract.getPartyBBankAccount()));
        data.put(prefix + "Tel", str(isA ? contract.getPartyATel() : contract.getPartyBTel()));
        data.put(prefix + "Fax", str(isA ? contract.getPartyAFax() : contract.getPartyBFax()));
    }

    /**
     * 兜底：若合同未维护对方主体名称，则使用客户名称
     */
    private void fillCustomerFallback(Map<String, String> data, CrmContractDO contract) {
        if (contract.getCustomerId() == null) {
            return;
        }
        boolean isPurchase = !Objects.equals(contract.getTemplateType(),
                CrmContractTemplateTypeEnum.SALES.getType());
        String key = isPurchase ? "partyBName" : "partyAName";
        if (StrUtil.isNotBlank(data.get(key))) {
            return;
        }
        CrmCustomerDO customer = customerService.getCustomer(contract.getCustomerId());
        if (customer != null) {
            data.put(key, str(customer.getName()));
        }
    }

    /**
     * 兜底：合同未维护「我方」主体信息时，使用合同配置中的我方公司信息。
     *
     * 采购合同我方＝甲方，购销合同我方＝乙方（与 {@code CrmContractServiceImpl#fillOurParty} 口径一致）。
     * 历史合同可能在配置补全之前创建、双方主体字段为空，这里在生成 Word 时兜底，
     * 避免正式合同出现「甲方：（盖章）」这类缺字。
     */
    private void fillOurPartyFromConfig(Map<String, String> data, CrmContractDO contract,
                                        CrmContractConfigDO config) {
        if (config == null) {
            return;
        }
        boolean isPurchase = !Objects.equals(contract.getTemplateType(),
                CrmContractTemplateTypeEnum.SALES.getType());
        String prefix = isPurchase ? "partyA" : "partyB";
        putIfBlank(data, prefix + "Name", config.getOurCompanyName());
        putIfBlank(data, prefix + "LegalPerson", config.getOurLegalPerson());
        putIfBlank(data, prefix + "Agent", config.getOurAgent());
        putIfBlank(data, prefix + "Address", config.getOurAddress());
        putIfBlank(data, prefix + "BankName", config.getOurBankName());
        putIfBlank(data, prefix + "BankAccount", config.getOurBankAccount());
        putIfBlank(data, prefix + "Tel", config.getOurTel());
        putIfBlank(data, prefix + "Fax", config.getOurFax());
    }

    /** 仅当占位符当前为空时，才用兜底值填充 */
    private static void putIfBlank(Map<String, String> data, String key, String value) {
        if (StrUtil.isNotBlank(value) && StrUtil.isBlank(data.get(key))) {
            data.put(key, value);
        }
    }

    /**
     * 交货截止日期：合同未填时按「签订日期 + 交货期限天数」推算。
     *
     * 否则模板中的「最迟应在{{deadlineYear}}年{{deadlineMonth}}月{{deadlineDay}}日前」
     * 会被替换成「最迟应在年月日前」而缺字。
     */
    private LocalDateTime resolveDeliveryDeadline(CrmContractDO contract, CrmContractConfigDO config) {
        if (contract.getDeliveryDeadline() != null) {
            return contract.getDeliveryDeadline();
        }
        LocalDateTime base = contract.getSignDate() != null
                ? contract.getSignDate() : contract.getOrderDate();
        if (base == null) {
            return null;
        }
        Integer days = contract.getDeliveryDays() != null
                ? contract.getDeliveryDays() : configDefaultInt(config, "deliveryDays");
        return days == null ? base : base.plusDays(days);
    }

    private void putPenaltyTerms(Map<String, String> data, CrmContractDO contract,
                                 CrmContractConfigDO config) {
        JSONObject penalty = null;
        if (StrUtil.isNotBlank(contract.getPenaltyTerms())) {
            try {
                penalty = JSONUtil.parseObj(contract.getPenaltyTerms());
            } catch (Exception e) {
                log.warn("[putPenaltyTerms][合同({}) 违约责任条款解析失败]", contract.getId(), e);
            }
        }
        if (config == null && penalty == null) {
            return;
        }
        // 采购合同违约责任
        putPenaltyDecimal(data, penalty, "pBuyerRejectRatio", config == null ? null : config.getPBuyerRejectRatio());
        putPenaltyDecimal(data, penalty, "pBuyerOverdueWan", config == null ? null : config.getPBuyerOverdueWan());
        putPenaltyInt(data, penalty, "pBuyerOverdueDays", config == null ? null : config.getPBuyerOverdueDays());
        putPenaltyDecimal(data, penalty, "pSellerQuality", config == null ? null : config.getPSellerQuality());
        putPenaltyDecimal(data, penalty, "pSellerOverdueWan", config == null ? null : config.getPSellerOverdueWan());
        putPenaltyInt(data, penalty, "pSellerOverdueDays", config == null ? null : config.getPSellerOverdueDays());
        putPenaltyDecimal(data, penalty, "pSellerCompensate", config == null ? null : config.getPSellerCompensate());
        putPenaltyInt(data, penalty, "pSellerReplaceDays", config == null ? null : config.getPSellerReplaceDays());
        putPenaltyDecimal(data, penalty, "pSellerTerminate", config == null ? null : config.getPSellerTerminate());
        putPenaltyDecimal(data, penalty, "pSellerDefect", config == null ? null : config.getPSellerDefect());
        // 购销合同违约责任
        putPenaltyDecimal(data, penalty, "sBuyerLateRatio", config == null ? null : config.getSBuyerLateRatio());
        putPenaltyDecimal(data, penalty, "sBuyerBreachRatio", config == null ? null : config.getSBuyerBreachRatio());
        putPenaltyDecimal(data, penalty, "sSellerLateRatio", config == null ? null : config.getSSellerLateRatio());
        putPenaltyDecimal(data, penalty, "sSellerBreachRatio", config == null ? null : config.getSSellerBreachRatio());
    }

    private void putPenaltyDecimal(Map<String, String> data, JSONObject penalty, String key, BigDecimal configVal) {
        Object v = penalty == null ? null : penalty.get(key);
        BigDecimal val = toBigDecimal(v);
        if (val == null) {
            val = configVal;
        }
        data.put(key, val == null ? "" : formatDecimal(val));
    }

    private void putPenaltyInt(Map<String, String> data, JSONObject penalty, String key, Integer configVal) {
        Object v = penalty == null ? null : penalty.get(key);
        Integer val = null;
        if (v != null) {
            try {
                val = Integer.parseInt(v.toString());
            } catch (Exception ignore) {
                // 忽略格式错误，回退到配置默认值
            }
        }
        if (val == null) {
            val = configVal;
        }
        data.put(key, val == null ? "" : String.valueOf(val));
    }

    // ==================== 货物明细表格 ====================

    private void fillProductTable(XWPFDocument doc, CrmContractDO contract) {
        List<CrmContractProductDO> items = contractProductMapper.selectListByContractId(contract.getId());
        if (CollUtil.isEmpty(items)) {
            items = new ArrayList<>();
        }
        Map<Long, CrmProductDO> productMap = productService.getProductMap(
                convertList(items, CrmContractProductDO::getProductId));

        for (XWPFTable table : doc.getTables()) {
            int tplIdx = findTemplateRowIndex(table);
            if (tplIdx < 0) {
                continue;
            }
            XWPFTableRow tplRow = table.getRow(tplIdx);
            int insertIdx = tplIdx;
            for (CrmContractProductDO item : items) {
                XWPFTableRow newRow = new XWPFTableRow((CTRow) tplRow.getCtRow().copy(), table);
                replaceInRow(newRow, buildProductRowData(item, productMap.get(item.getProductId())));
                table.addRow(newRow, insertIdx++);
            }
            // 移除用于复制的模板行（复制完成后它位于最后一条数据的下一位）
            table.removeRow(insertIdx);
        }
    }

    private int findTemplateRowIndex(XWPFTable table) {
        for (int i = 0; i < table.getNumberOfRows(); i++) {
            for (XWPFTableCell cell : table.getRow(i).getTableCells()) {
                if (cell.getText() != null && cell.getText().contains(PRODUCT_ROW_MARK)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private Map<String, String> buildProductRowData(CrmContractProductDO item, CrmProductDO product) {
        Map<String, String> d = new HashMap<>();
        d.put("productName", product == null ? "" : str(product.getName()));
        d.put("productSpec", str(item.getSpec()));
        d.put("productUnit", str(item.getUnit()));
        d.put("productCount", item.getCount() == null ? "" : item.getCount().stripTrailingZeros().toPlainString());

        BigDecimal price = item.getContractPrice() != null ? item.getContractPrice() : item.getProductPrice();
        BigDecimal total = item.getTotalPrice();
        if (total == null && price != null && item.getCount() != null) {
            total = price.multiply(item.getCount());
        }
        d.put("productPrice", formatWan(price));
        d.put("productTotalPrice", formatWan(total));

        // 资金来源（万元）：按明细的资金来源勾选到对应列，其余列填 "/"
        String amount = formatWan(total);
        String in = "/", out = "/", other = "/";
        Integer fundSource = item.getFundSource();
        if (Objects.equals(fundSource, 1)) {
            in = amount;
        } else if (Objects.equals(fundSource, 2)) {
            out = amount;
        } else if (Objects.equals(fundSource, 3)) {
            other = amount;
        }
        d.put("fundSourceIn", in);
        d.put("fundSourceOut", out);
        d.put("fundSourceOther", other);

        d.put("accessories", str(item.getAccessories()));
        d.put("deliveryPeriod", str(item.getDeliveryPeriod()));
        return d;
    }

    // ==================== 占位符替换 ====================

    private void replaceInDocument(XWPFDocument doc, Map<String, String> data) {
        doc.getParagraphs().forEach(p -> replaceInParagraph(p, data));
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                replaceInRow(row, data);
            }
        }
    }

    private void replaceInRow(XWPFTableRow row, Map<String, String> data) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph p : cell.getParagraphs()) {
                replaceInParagraph(p, data);
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph p, Map<String, String> data) {
        List<XWPFRun> runs = p.getRuns();
        if (CollUtil.isEmpty(runs)) {
            return;
        }
        for (XWPFRun run : runs) {
            String text = run.getText(0);
            if (StrUtil.isEmpty(text) || !text.contains("{{")) {
                continue;
            }
            String replaced = text;
            for (Map.Entry<String, String> entry : data.entrySet()) {
                replaced = replaced.replace("{{" + entry.getKey() + "}}",
                        entry.getValue() == null ? "" : entry.getValue());
            }
            run.setText(replaced, 0);
        }
    }

    /**
     * 清理未填充的占位符，保证正式合同中不残留模板标记
     */
    private void cleanRemainingPlaceholders(XWPFDocument doc) {
        doc.getParagraphs().forEach(this::cleanInParagraph);
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    cell.getParagraphs().forEach(this::cleanInParagraph);
                }
            }
        }
    }

    private void cleanInParagraph(XWPFParagraph p) {
        for (XWPFRun run : p.getRuns()) {
            String text = run.getText(0);
            if (StrUtil.isNotEmpty(text) && text.contains("{{")) {
                run.setText(PLACEHOLDER_PATTERN.matcher(text).replaceAll(""), 0);
            }
        }
    }

    // ==================== 格式化工具 ====================

    private void putInt(Map<String, String> data, String key, Integer value,
                        CrmContractConfigDO config, String configField) {
        Integer def = configDefaultInt(config, configField);
        Integer val = value != null ? value : def;
        data.put(key, val == null ? "" : String.valueOf(val));
    }

    private void putDecimal(Map<String, String> data, String key, BigDecimal value,
                            CrmContractConfigDO config, String configField) {
        BigDecimal def = configDefaultDecimal(config, configField);
        BigDecimal val = value != null ? value : def;
        data.put(key, val == null ? "" : formatDecimal(val));
    }

    /**
     * 合同配置缺失（例如新租户还没维护合同配置）或单项未填时的内置默认值。
     *
     * 目的：保证生成的合同「不缺字」。否则模板里的「{{firstAcceptDays}}日内」
     * 「一式{{contractCopies}}份」会被替换成空字符串，出现
     * 「安装调试完毕后日内初步验收」这类断句。
     * 取值与合同配置页的推荐默认值一致；管理员在合同配置里填了值即以配置为准。
     */
    private static final Map<String, Integer> BUILTIN_INT_DEFAULTS = Map.ofEntries(
            Map.entry("attachmentCopies", 1),
            Map.entry("attachmentPages", 1),
            Map.entry("sampleDays", 5),
            Map.entry("deliveryDays", 30),
            Map.entry("deliveryArriveDays", 30),
            Map.entry("installDays", 10),
            Map.entry("firstAcceptDays", 7),
            Map.entry("trialDays", 30),
            Map.entry("finalAcceptDays", 7),
            Map.entry("autoAcceptDays", 15),
            Map.entry("maxRepairTimes", 3),
            Map.entry("contractCopies", 4),
            Map.entry("prepayDays", 15),
            Map.entry("acceptPayDays", 15),
            Map.entry("depositMonths", 12),
            Map.entry("depositDays", 15),
            Map.entry("warrantyYears", 1),
            Map.entry("responseHours", 24),
            Map.entry("repairHours", 48),
            Map.entry("repairTimes", 3),
            Map.entry("reportRepairDays", 3));

    private static final Map<String, BigDecimal> BUILTIN_DECIMAL_DEFAULTS = Map.of(
            "prepayRatio", new BigDecimal("30"),
            "acceptPayRatio", new BigDecimal("60"),
            "depositRatio", new BigDecimal("10"));

    private Integer configDefaultInt(CrmContractConfigDO config, String field) {
        Integer value = configIntFromConfig(config, field);
        return value != null ? value : BUILTIN_INT_DEFAULTS.get(field);
    }

    private Integer configIntFromConfig(CrmContractConfigDO config, String field) {
        if (config == null) {
            return null;
        }
        switch (field) {
            case "sampleDays": return config.getDefaultSampleDays();
            case "deliveryDays": return config.getDefaultDeliveryDays();
            case "deliveryArriveDays": return config.getDefaultDeliveryArriveDays();
            case "installDays": return config.getDefaultInstallDays();
            case "firstAcceptDays": return config.getDefaultFirstAcceptDays();
            case "trialDays": return config.getDefaultTrialDays();
            case "finalAcceptDays": return config.getDefaultFinalAcceptDays();
            case "autoAcceptDays": return config.getDefaultAutoAcceptDays();
            case "maxRepairTimes": return config.getDefaultMaxRepairTimes();
            case "attachmentCopies": return null;
            case "attachmentPages": return null;
            case "contractCopies": return config.getDefaultContractCopies();
            case "prepayDays": return config.getDefaultPrepayDays();
            case "acceptPayDays": return config.getDefaultAcceptPayDays();
            case "depositMonths": return config.getDefaultDepositMonths();
            case "depositDays": return config.getDefaultDepositDays();
            case "warrantyYears": return config.getDefaultWarrantyYears();
            case "responseHours": return config.getDefaultResponseHours();
            case "repairHours": return config.getDefaultRepairHours();
            case "repairTimes": return config.getDefaultRepairTimes();
            case "reportRepairDays": return config.getDefaultReportRepairDays();
            default: return null;
        }
    }

    private BigDecimal configDefaultDecimal(CrmContractConfigDO config, String field) {
        BigDecimal value = null;
        if (config != null) {
            switch (field) {
                case "prepayRatio": value = config.getDefaultPrepayRatio(); break;
                case "acceptPayRatio": value = config.getDefaultAcceptPayRatio(); break;
                case "depositRatio": value = config.getDefaultDepositRatio(); break;
                default: break;
            }
        }
        return value != null ? value : BUILTIN_DECIMAL_DEFAULTS.get(field);
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }

    private static BigDecimal nvl(BigDecimal value, BigDecimal defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static String defaultStr(String value, String defaultValue) {
        return StrUtil.isNotBlank(value) ? value : (defaultValue == null ? "" : defaultValue);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 金额为空时，按合同总价与比例自动计算
     */
    private static BigDecimal defaultAmount(BigDecimal amount, BigDecimal total, BigDecimal ratio) {
        if (amount != null) {
            return amount;
        }
        if (total == null || ratio == null) {
            return BigDecimal.ZERO;
        }
        return total.multiply(ratio).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 金额转为万元展示（模板表头单位为「万元」）
     */
    private static String formatWan(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private static String formatDate(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        return date.getYear() + "年" + String.format("%02d", date.getMonthValue())
                + "月" + String.format("%02d", date.getDayOfMonth()) + "日";
    }

    private static void putDateParts(Map<String, String> data, String prefix, LocalDateTime date) {
        if (date == null) {
            data.put(prefix + "Year", "");
            data.put(prefix + "Month", "");
            data.put(prefix + "Day", "");
            return;
        }
        data.put(prefix + "Year", String.valueOf(date.getYear()));
        data.put(prefix + "Month", String.valueOf(date.getMonthValue()));
        data.put(prefix + "Day", String.valueOf(date.getDayOfMonth()));
    }

    // ==================== 人民币大写 ====================

    private static final String[] CN_DIGITS = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_UNITS = {"", "拾", "佰", "仟"};
    private static final String[] CN_SECTIONS = {"", "万", "亿", "万亿"};

    /**
     * 金额转人民币大写，例如 1234.50 → 壹仟贰佰叁拾肆元伍角
     */
    private static String toChineseAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        boolean negative = amount.signum() < 0;
        long cents = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        if (negative) {
            cents = -cents;
        }
        long yuan = cents / 100;
        int jiao = (int) ((cents / 10) % 10);
        int fen = (int) (cents % 10);

        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append("负");
        }
        sb.append(yuan == 0 ? "零" : intToChinese(yuan)).append("元");
        if (jiao == 0 && fen == 0) {
            sb.append("整");
        } else {
            if (jiao > 0) {
                sb.append(CN_DIGITS[jiao]).append("角");
            } else if (fen > 0) {
                sb.append("零");
            }
            if (fen > 0) {
                sb.append(CN_DIGITS[fen]).append("分");
            }
        }
        return sb.toString();
    }

    private static String intToChinese(long num) {
        if (num == 0) {
            return "零";
        }
        StringBuilder sb = new StringBuilder();
        int section = 0;
        while (num > 0) {
            int part = (int) (num % 10000);
            num = num / 10000;
            if (part == 0) {
                if (sb.length() > 0 && !sb.toString().startsWith("零")) {
                    sb.insert(0, "零");
                }
            } else {
                String partStr = sectionToChinese(part);
                // 该节不足四位且后面还有更高位时，需要补「零」
                if (num > 0 && part < 1000) {
                    partStr = "零" + partStr;
                }
                sb.insert(0, partStr + CN_SECTIONS[section]);
            }
            section++;
        }
        String result = sb.toString().replaceAll("零{2,}", "零");
        while (result.endsWith("零")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String sectionToChinese(int num) {
        StringBuilder sb = new StringBuilder();
        boolean pendingZero = false;
        int[] digits = {num / 1000, (num / 100) % 10, (num / 10) % 10, num % 10};
        for (int i = 0; i < 4; i++) {
            int d = digits[i];
            if (d == 0) {
                if (sb.length() > 0) {
                    pendingZero = true;
                }
            } else {
                if (pendingZero) {
                    sb.append("零");
                    pendingZero = false;
                }
                sb.append(CN_DIGITS[d]).append(CN_UNITS[3 - i]);
            }
        }
        return sb.toString();
    }

}
