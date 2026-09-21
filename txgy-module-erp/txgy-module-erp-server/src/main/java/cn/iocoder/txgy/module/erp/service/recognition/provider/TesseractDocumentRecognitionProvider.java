package cn.iocoder.txgy.module.erp.service.recognition.provider;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ERP 本地 Tesseract OCR 适配器。
 *
 * <p>Tesseract 负责图片文字识别，本类将常见中文采购单据字段转换为 ERP 统一文档结构。
 * 最终结果仍然进入原有的人工复核流程，无法可靠匹配的字段由用户手动修正。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erp.recognition.provider", havingValue = "tesseract", matchIfMissing = true)
public class TesseractDocumentRecognitionProvider implements DocumentRecognitionProvider {

    private static final Pattern DOCUMENT_NO_PATTERN = Pattern.compile(
            "(?:订单号|单据号|单号|采购订单号|外部单号)\\s*[:：]?\\s*([A-Za-z0-9][A-Za-z0-9_./-]*)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?:订单日期|单据日期|日期|开单日期)\\s*[:：]?\\s*(\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)");
    private static final Pattern GENERIC_DATE_PATTERN = Pattern.compile(
            "(?<!\\d)(20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?)(?!\\d)");
    private static final Pattern SUPPLIER_PATTERN = Pattern.compile(
            "(?:供应商名称|供货单位|供应商|供方)\\s*[:：]?\\s*([^\\r\\n，,;；]{2,80})");
    private static final Pattern CUSTOMER_PATTERN = Pattern.compile(
            "(?:客户名称|购货单位|收货单位|客户|购方)\\s*[:：]?\\s*([^\\r\\n，,;；]{2,80})");
    private static final Pattern REMARK_LINE_PATTERN = Pattern.compile(
            "^\\s*(?:备注|说明)\\s*[:：]?\\s*(.*?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "(?<!开户)(?:结算账户名称|结算帐号|结算账号|收款账户|付款账户|账户名称|账户编码|收款账号|付款账号|结算账户|账号)"
                    + "[\\t \\f]*[:：]?[\\t \\f]*([^\\r\\n，,;；|｜]{1,80})");
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "(?:联系人姓名|联系人)\\s*[:：]?\\s*([^\\r\\n，,;；]{2,40})");
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "(?:手机号码|移动电话|手机)\\s*[:：]?\\s*(1\\d{10})");
    private static final Pattern TELEPHONE_PATTERN = Pattern.compile(
            "(?:联系电话|固定电话|电话)\\s*[:：]?\\s*([0-9\\-()（）+ ]{6,30})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?:电子邮箱|邮箱|email)\\s*[:：]?\\s*([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern FAX_PATTERN = Pattern.compile(
            "(?:传真号码|传真)\\s*[:：]?\\s*([0-9\\-()（）+ ]{6,30})");
    private static final Pattern TAX_NO_PATTERN = Pattern.compile(
            "(?:纳税人识别号|税号|统一社会信用代码)\\s*[:：]?\\s*([A-Z0-9-]{8,30})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_NAME_PATTERN = Pattern.compile(
            "(?:开户银行|银行名称|开户行)\\s*[:：]?\\s*([^\\r\\n，,;；]{2,80})");
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile(
            "(?:开户账号|银行账号|银行账户)\\s*[:：]?\\s*([0-9\\- ]{6,40})");
    private static final Pattern BANK_ADDRESS_PATTERN = Pattern.compile(
            "(?:开户地址|开户银行地址)\\s*[:：]?\\s*([^\\r\\n，,;；]{2,120})");
    private static final Pattern GENERIC_DOCUMENT_NO_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])((?:PO|CGDD|PURCHASE)[-_][A-Za-z0-9._/-]{3,})(?![A-Za-z0-9])");
    private static final Pattern ITEM_CODE_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])((?:[A-Za-z][A-Za-z0-9_./-]*\\d[A-Za-z0-9_./-]*|\\d{3,}(?![-/.]\\d)))(?![A-Za-z0-9])");
    private static final Pattern ITEM_TAIL_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9.])(\\d+(?:[.,]\\d+)?)(?:\\s+|[|｜,，;；/]+\\s*)"
                    + "(\\d+(?:[.,]\\d+)?)(?:(?:\\s+|[|｜,，;；/]+\\s*)"
                    + "(\\d+(?:[.,]\\d+)?)(?:\\s*[%％])?)?"
                    + "(?:[\\s|｜,，;；/]+(.*))?$");
    private static final Pattern TAX_RATE_LABEL_PATTERN = Pattern.compile(
            "(?i)税率\\s*(?:[（(]\\s*[%％]\\s*[）)])?\\s*[:：]?\\s*"
                    + "(\\d+(?:[.,]\\d+)?)\\s*[%％]?");
    private static final Pattern TAX_RATE_VALUE_PATTERN = Pattern.compile(
            "(?<![\\d.])(\\d+(?:[.,]\\d+)?)\\s*[%％]");
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "^\\s*(?:序号\\s*)?(\\d{1,4})?[.、)）\\s]*"
                    + "([A-Za-z0-9][A-Za-z0-9_./-]*)?\\s*"
                    + "([^\\s,，;；]{2,40})\\s+"
                    + "(\\d+(?:\\.\\d+)?)\\s*"
                    + "(\\d+(?:\\.\\d+)?)?\\s*$");
    private static final Pattern FIELD_LABEL_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:供应商名称|供货单位|供应商|供方|客户名称|购货单位|收货单位|客户|购方|"
                    + "订单日期|单据日期|开单日期|外部单号|采购订单号|单号|单据号|订单号|日期|"
                    + "结算账户名称|结算帐号|结算账号|收款账户|付款账户|账户名称|账户编码|收款账号|付款账号|结算账户|账号|"
                    + "产品编码|产品名称|数量|单价|税率|税率\\s*\\(%\\)|"
                    + "备注|说明|联系人姓名|联系人|手机号码|移动电话|手机|联系电话|固定电话|电话|"
                    + "电子邮箱|邮箱|email|传真号码|传真|纳税人识别号|统一社会信用代码|税号|"
                    + "开户银行地址|开户银行|银行名称|开户行|开户账号|银行账号|银行账户|开户地址)"
                    + "\\s*[:：]?\\s*",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private RestTemplate restTemplate;

    @Value("${erp.recognition.source-file-base-url:}")
    private String sourceFileBaseUrl;

    @Value("${erp.recognition.tesseract.executable:tesseract}")
    private String executable;

    @Value("${erp.recognition.tesseract.language:chi_sim+eng}")
    private String language;

    @Value("${erp.recognition.tesseract.psm:6}")
    private String pageSegmentationMode;

    @Value("${erp.recognition.tesseract.timeout-seconds:120}")
    private long timeoutSeconds;

    @Override
    public String getType() {
        return "TESSERACT";
    }

    @Override
    public String recognize(String sourceFileUrl, String bizType) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("txgy-erp-ocr-");
            Path inputFile = downloadSourceFile(sourceFileUrl, workDir);
            String text = recognizeFile(inputFile, workDir);
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("Tesseract 未识别到文字，请上传清晰的中文单据图片或 PDF");
            }
            return buildResponse(text, bizType);
        } catch (IOException e) {
            throw new IllegalStateException("Tesseract OCR 文件处理失败：" + e.getMessage(), e);
        } finally {
            deleteDirectory(workDir);
        }
    }

    private Path downloadSourceFile(String sourceFileUrl, Path workDir) throws IOException {
        if (!StringUtils.hasText(sourceFileUrl)) {
            throw new IllegalArgumentException("识别文件地址不能为空");
        }
        URI uri = parseUri(sourceFileUrl);
        String fileName = sourceFileUrl.substring(sourceFileUrl.lastIndexOf('/') + 1);
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            fileName = "source.bin";
        }
        Path target = workDir.resolve(fileName).normalize();
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            Files.copy(Path.of(uri), target);
            return target;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("不支持的文件地址协议：" + uri.getScheme());
        }
        ResponseEntity<byte[]> response = restTemplate.getForEntity(resolveDownloadUri(uri), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().length == 0) {
            throw new IllegalStateException("无法下载待识别文件：" + response.getStatusCode());
        }
        Files.write(target, response.getBody(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return target;
    }

    private URI resolveDownloadUri(URI sourceUri) {
        if (!StringUtils.hasText(sourceFileBaseUrl)
                || sourceUri.getHost() == null
                || !isLoopbackHost(sourceUri.getHost())) {
            return sourceUri;
        }
        try {
            URI baseUri = URI.create(sourceFileBaseUrl.trim());
            // 保留原始 path 的百分号编码。使用 URI 多参数构造器会重新处理中文路径，
            // Apache HttpClient 可能将文件名误解析为 query，导致 infra 只收到目录路径。
            String base = baseUri.toASCIIString();
            String rawPath = sourceUri.getRawPath();
            if (!StringUtils.hasText(rawPath)) {
                throw new IllegalStateException("ERP OCR 文件访问地址缺少路径：" + sourceUri);
            }
            if (base.endsWith("/") && rawPath.startsWith("/")) {
                base = base.substring(0, base.length() - 1);
            } else if (!base.endsWith("/") && !rawPath.startsWith("/")) {
                base = base + "/";
            }
            StringBuilder resolved = new StringBuilder(base).append(rawPath);
            if (sourceUri.getRawQuery() != null) {
                resolved.append('?').append(sourceUri.getRawQuery());
            }
            if (sourceUri.getRawFragment() != null) {
                resolved.append('#').append(sourceUri.getRawFragment());
            }
            return URI.create(resolved.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ERP OCR 文件访问地址配置不正确：" + sourceFileBaseUrl, e);
        }
    }

    private String recognizeFile(Path inputFile, Path workDir) throws IOException {
        List<Path> images = isPdf(inputFile)
                ? convertPdfToImages(inputFile, workDir)
                : List.of(inputFile);
        StringBuilder text = new StringBuilder();
        for (Path image : images) {
            text.append(runTesseract(image)).append(System.lineSeparator());
        }
        return text.toString().trim();
    }

    private List<Path> convertPdfToImages(Path pdfFile, Path workDir) throws IOException {
        Path prefix = workDir.resolve("page");
        runCommand(List.of("pdftoppm", "-png", "-r", "300", pdfFile.toString(), prefix.toString()),
                "PDF 转图片失败");
        try (var stream = Files.list(workDir)) {
            List<Path> pages = stream
                    .filter(path -> path.getFileName().toString().startsWith("page-")
                            && path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            if (pages.isEmpty()) {
                throw new IllegalStateException("PDF 未生成可识别的页面图片");
            }
            return pages;
        }
    }

    private String runTesseract(Path image) throws IOException {
        return runCommand(List.of(executable, image.toString(), "stdout",
                        "-l", language, "--psm", pageSegmentationMode),
                "Tesseract OCR 执行失败");
    }

    private String runCommand(List<String> command, String errorPrefix) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(errorPrefix + "：执行超时（" + timeoutSeconds + " 秒）");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(errorPrefix + "：" + output.trim());
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException(errorPrefix + "：线程被中断", e);
        }
    }

    private String buildResponse(String ocrText, String bizType) {
        Map<String, Object> response = new LinkedHashMap<>();
        ErpRecognitionDocument document = new ErpRecognitionDocument();
        document.setDocumentType(bizType);
        document.setFinalConfidence(java.math.BigDecimal.valueOf(0.65));
        document.setDocumentNo(firstText(
                findGroup(DOCUMENT_NO_PATTERN, ocrText, 1),
                firstText(findGroup(GENERIC_DOCUMENT_NO_PATTERN, ocrText, 1),
                        inferDocumentNo(ocrText))));
        document.setDocumentDate(normalizeDate(firstText(
                findGroup(DATE_PATTERN, ocrText, 1),
                findGroup(GENERIC_DATE_PATTERN, ocrText, 1))));
        document.setAccountName(cleanBoundedValue(findGroup(ACCOUNT_PATTERN, ocrText, 1)));
        String partyName = cleanSupplierName(findGroup(
                bizType.equals("SALE_ORDER") ? CUSTOMER_PATTERN : SUPPLIER_PATTERN, ocrText, 1));
        if (!StringUtils.hasText(partyName)) {
            partyName = inferSupplierName(ocrText);
        }
        if ("SALE_ORDER".equals(bizType)) {
            document.setCustomerName(partyName);
        } else {
            document.setSupplierName(partyName);
        }
        document.setItems(parseItems(ocrText));
        document.setRemark(extractDocumentRemark(ocrText));
        document.setContact(cleanBoundedValue(findGroup(CONTACT_PATTERN, ocrText, 1)));
        document.setMobile(cleanTelephone(findGroup(MOBILE_PATTERN, ocrText, 1)));
        document.setTelephone(cleanTelephone(findGroup(TELEPHONE_PATTERN, ocrText, 1)));
        document.setEmail(cleanBoundedValue(findGroup(EMAIL_PATTERN, ocrText, 1)));
        document.setFax(cleanTelephone(findGroup(FAX_PATTERN, ocrText, 1)));
        document.setTaxNo(cleanBoundedValue(findGroup(TAX_NO_PATTERN, ocrText, 1)));
        document.setBankName(cleanBoundedValue(findGroup(BANK_NAME_PATTERN, ocrText, 1)));
        document.setBankAccount(cleanTelephone(findGroup(BANK_ACCOUNT_PATTERN, ocrText, 1)));
        document.setBankAddress(cleanBoundedValue(findGroup(BANK_ADDRESS_PATTERN, ocrText, 1)));

        Map<String, Object> documentMap = JsonUtils.parseObject(
                JsonUtils.toJsonString(document), new TypeReference<>() {});
        response.putAll(documentMap);
        response.put("ocrText", ocrText);
        response.put("ocrProvider", "TESSERACT");
        return JsonUtils.toJsonString(response);
    }

    private List<ErpRecognitionDocument.Item> parseItems(String text) {
        List<ErpRecognitionDocument.Item> items = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalizedLine = line.trim().replaceAll("\\s+", " ");
            if (!StringUtils.hasText(normalizedLine)) {
                continue;
            }

            Matcher tableMatcher = ITEM_TAIL_PATTERN.matcher(normalizedLine);
            if (tableMatcher.find()) {
                ErpRecognitionDocument.Item item = buildTableItem(
                        normalizedLine.substring(0, tableMatcher.start()).trim(), tableMatcher);
                if (item != null) {
                    items.add(item);
                    continue;
                }
            }

            Matcher matcher = ITEM_PATTERN.matcher(normalizedLine);
            if (!matcher.matches()) {
                continue;
            }
            ErpRecognitionDocument.Item item = new ErpRecognitionDocument.Item();
            item.setProductCode(cleanField(matcher.group(2)));
            item.setProductName(cleanField(matcher.group(3)));
            item.setCount(parseDecimal(matcher.group(4)));
            if (StringUtils.hasText(matcher.group(5))) {
                item.setProductPrice(parseDecimal(matcher.group(5)));
            }
            item.setConfidence(java.math.BigDecimal.valueOf(0.55));
            items.add(item);
        }
        return items;
    }

    private ErpRecognitionDocument.Item buildTableItem(String prefix, Matcher tableMatcher) {
        Matcher codeMatcher = ITEM_CODE_PATTERN.matcher(prefix);
        List<String> candidates = new ArrayList<>();
        List<Integer> candidateEnds = new ArrayList<>();
        while (codeMatcher.find()) {
            String candidate = codeMatcher.group(1);
            if (!isIgnorableCode(candidate)) {
                candidates.add(candidate);
                candidateEnds.add(codeMatcher.end());
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // OCR 经常把“外部单号”和“产品编码”拼在同一行。表格中明细编码
        // 位于日期/外部单号之后，优先使用最后一个合理编码，避免把订单号当产品。
        int candidateIndex = candidates.size() - 1;
        String productCode = candidates.get(candidateIndex);
        String productName = prefix.substring(candidateEnds.get(candidateIndex)).trim();
        if (!StringUtils.hasText(productName) && candidateIndex > 0) {
            candidateIndex--;
            productCode = candidates.get(candidateIndex);
            productName = prefix.substring(candidateEnds.get(candidateIndex)).trim();
        }
        if (!StringUtils.hasText(productCode) || !StringUtils.hasText(productName)
                || looksLikeTableHeader(productName)) {
            return null;
        }
        ErpRecognitionDocument.Item item = new ErpRecognitionDocument.Item();
        item.setProductCode(cleanField(productCode));
        item.setProductName(cleanItemName(productName));
        item.setCount(parseDecimal(tableMatcher.group(1)));
        item.setProductPrice(parseDecimal(tableMatcher.group(2)));
        String rawRemark = cleanField(tableMatcher.group(4));
        java.math.BigDecimal taxPercent = parseTaxPercent(tableMatcher.group(3));
        if (taxPercent == null) {
            taxPercent = parseTaxPercentFromRemark(rawRemark);
        }
        item.setTaxPercent(taxPercent);
        item.setRemark(removeTaxPercent(rawRemark));
        item.setConfidence(java.math.BigDecimal.valueOf(0.55));
        return item;
    }

    private static boolean isIgnorableCode(String value) {
        return !StringUtils.hasText(value)
                || value.matches("20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?")
                || value.matches("(?i)(PO|CGDD|PURCHASE)[-_].*")
                || value.length() <= 2;
    }

    private static boolean looksLikeTableHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        int matched = 0;
        String[] headers = {"供应商名称", "订单日期", "外部单号", "产品编码", "产品名称",
                "数量", "单价", "税率", "备注", "客户名称", "销售员"};
        for (String header : headers) {
            if (value.contains(header)) {
                matched++;
            }
        }
        return matched >= 2;
    }

    private static String inferSupplierName(String text) {
        for (String line : text.split("\\R")) {
            String normalizedLine = line.trim().replaceAll("\\s+", " ");
            Matcher dateMatcher = GENERIC_DATE_PATTERN.matcher(normalizedLine);
            Matcher codeMatcher = ITEM_CODE_PATTERN.matcher(normalizedLine);
            if (!dateMatcher.find() || !codeMatcher.find()) {
                continue;
            }
            String candidate = normalizedLine.substring(0, dateMatcher.start())
                    .replaceFirst("^[\\d\\s.、)）]+", "")
                    .trim();
            if (candidate.length() >= 2) {
                return cleanSupplierName(candidate);
            }
        }
        return null;
    }

    private static String inferDocumentNo(String text) {
        for (String line : text.split("\\R")) {
            String normalizedLine = line.trim().replaceAll("\\s+", " ");
            Matcher dateMatcher = GENERIC_DATE_PATTERN.matcher(normalizedLine);
            if (!dateMatcher.find()) {
                continue;
            }
            String afterDate = normalizedLine.substring(dateMatcher.end()).trim();
            Matcher codeMatcher = ITEM_CODE_PATTERN.matcher(afterDate);
            if (codeMatcher.find()) {
                String candidate = codeMatcher.group(1);
                if (candidate.matches("(?i)[A-Za-z][A-Za-z0-9_./-]*")) {
                    return cleanField(candidate);
                }
            }
        }
        return null;
    }

    private static String cleanSupplierName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = cleanBoundedValue(value);
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        candidate = candidate.replaceFirst(
                "^(?:供应商名称|供应商|供货单位|供方)\\s*[:：]?\\s*", "");
        Matcher dateMatcher = GENERIC_DATE_PATTERN.matcher(candidate);
        if (dateMatcher.find()) {
            candidate = candidate.substring(0, dateMatcher.start()).trim();
        }
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private static String cleanItemName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = value.replaceFirst("^[.、)）\\s]+", "").trim();
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private static String extractDocumentRemark(String text) {
        for (String line : text.split("\\R")) {
            String normalizedLine = line.trim();
            if (!StringUtils.hasText(normalizedLine)
                    || looksLikeTableHeader(normalizedLine)
                    || containsStructuredRow(normalizedLine)) {
                continue;
            }
            Matcher matcher = REMARK_LINE_PATTERN.matcher(normalizedLine);
            if (!matcher.find()) {
                continue;
            }
            String candidate = cleanField(matcher.group(1));
            if (StringUtils.hasText(candidate)
                    && !looksLikeTableHeader(candidate)
                    && !containsStructuredRow(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean containsStructuredRow(String value) {
        return GENERIC_DATE_PATTERN.matcher(value).find()
                && ITEM_TAIL_PATTERN.matcher(value).find();
    }

    private static String cleanBoundedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String candidate = cleanField(value).replaceAll("\\s+", " ");
        Matcher nextLabel = FIELD_LABEL_PATTERN.matcher(candidate);
        if (nextLabel.find()) {
            candidate = candidate.substring(0, nextLabel.start()).trim();
        }
        return StringUtils.hasText(candidate) ? candidate : null;
    }

    private static String cleanTelephone(String value) {
        return cleanField(value == null ? null : value.replaceAll("[\\s　]+", ""));
    }

    private static java.math.BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static java.math.BigDecimal parseTaxPercent(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace('％', '%')
                .replace(',', '.')
                .replaceAll("\\s+", "");
        boolean hasPercentSign = normalized.indexOf('%') >= 0;
        normalized = normalized.replace("%", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            java.math.BigDecimal percent = new java.math.BigDecimal(normalized);
            if (!hasPercentSign && percent.compareTo(java.math.BigDecimal.ZERO) > 0
                    && percent.compareTo(java.math.BigDecimal.ONE) < 0) {
                percent = percent.movePointRight(2);
            }
            return percent;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static java.math.BigDecimal parseTaxPercentFromRemark(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher labelMatcher = TAX_RATE_LABEL_PATTERN.matcher(value);
        if (labelMatcher.find()) {
            return parseTaxPercent(labelMatcher.group(1));
        }
        Matcher valueMatcher = TAX_RATE_VALUE_PATTERN.matcher(value);
        return valueMatcher.find() ? parseTaxPercent(valueMatcher.group(1)) : null;
    }

    private static String removeTaxPercent(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = TAX_RATE_LABEL_PATTERN.matcher(value).replaceAll(" ");
        cleaned = TAX_RATE_VALUE_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("[|｜,，;；/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return StringUtils.hasText(cleaned) ? cleaned : null;
    }

    private static String firstText(String current, String fallback) {
        return StringUtils.hasText(current) ? current.trim() : fallback;
    }

    private static String findGroup(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    private static String cleanField(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("[：:]$", "").trim();
    }

    private static String normalizeDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replace('年', '-').replace('月', '-').replace("日", "")
                .replace('/', '-').replace('.', '-');
    }

    private static boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("文件地址不是有效 URL：" + value, e);
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private static void deleteDirectory(Path directory) {
        if (directory == null) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("删除 OCR 临时文件失败：{}", path, e);
                }
            });
        } catch (IOException e) {
            log.debug("删除 OCR 临时目录失败：{}", directory, e);
        }
    }
}
