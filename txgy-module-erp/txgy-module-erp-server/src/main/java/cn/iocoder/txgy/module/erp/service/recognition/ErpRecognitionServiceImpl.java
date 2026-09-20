package cn.iocoder.txgy.module.erp.service.recognition;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.returns.ErpPurchaseReturnSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.order.ErpSaleOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionResultCorrectReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpPurchaseOrderImportExcelVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpSaleOrderImportExcelVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskCreateReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskPageReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpAccountDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionImportRecordDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionResultDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionTaskDO;
import cn.iocoder.txgy.module.erp.dal.mysql.recognition.ErpRecognitionImportRecordMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.recognition.ErpRecognitionResultMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.recognition.ErpRecognitionTaskMapper;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseReturnService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.txgy.module.erp.service.finance.ErpAccountService;
import cn.iocoder.txgy.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.txgy.module.erp.service.sale.ErpSaleOrderService;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionValidation;
import cn.iocoder.txgy.module.erp.service.recognition.provider.DocumentRecognitionProvider;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.RECOGNITION_RESULT_VERSION_EXPIRED;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.RECOGNITION_TASK_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.RECOGNITION_TASK_STATE_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.RECOGNITION_UNSUPPORTED_BIZ_TYPE;

/**
 * ERP 图片识别服务。识别结果始终需要人工确认，确认后按业务类型创建对应草稿。
 */
@Service
@Validated
public class ErpRecognitionServiceImpl implements ErpRecognitionService {

    private static final String BIZ_TYPE_PURCHASE_ORDER = "PURCHASE_ORDER";
    private static final String BIZ_TYPE_PURCHASE_RETURN = "PURCHASE_RETURN";
    private static final String BIZ_TYPE_SUPPLIER = "SUPPLIER";
    private static final String BIZ_TYPE_SALE_ORDER = "SALE_ORDER";
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_REVIEW_REQUIRED = "REVIEW_REQUIRED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_IMPORTED = "IMPORTED";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private ErpRecognitionTaskMapper taskMapper;
    @Resource
    private ErpRecognitionResultMapper resultMapper;
    @Resource
    private ErpRecognitionImportRecordMapper importRecordMapper;
    @Resource
    private DocumentRecognitionProvider documentRecognitionProvider;
    @Resource
    private ErpSupplierService supplierService;
    @Resource
    private ErpAccountService accountService;
    @Resource
    private ErpProductService productService;
    @Resource
    private ErpPurchaseOrderService purchaseOrderService;
    @Resource
    private ErpPurchaseReturnService purchaseReturnService;
    @Resource
    private ErpCustomerService customerService;
    @Resource
    private ErpSaleOrderService saleOrderService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long createTask(ErpRecognitionTaskCreateReqVO createReqVO) {
        validateBizType(createReqVO.getBizType());
        ErpRecognitionTaskDO task = ErpRecognitionTaskDO.builder()
                .bizType(createReqVO.getBizType())
                .status(STATUS_CREATED)
                .sourceFileUrl(createReqVO.getSourceFileUrl())
                .resultVersion(0)
                .build();
        taskMapper.insert(task);
        processTask(task.getId());
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importExcel(MultipartFile file, String sourceFileUrl, String bizType) throws Exception {
        if (!BIZ_TYPE_PURCHASE_ORDER.equals(bizType) && !BIZ_TYPE_SALE_ORDER.equals(bizType)) {
            throw exception(RECOGNITION_UNSUPPORTED_BIZ_TYPE, bizType);
        }
        ErpRecognitionDocument document;
        Object rows;
        if (BIZ_TYPE_SALE_ORDER.equals(bizType)) {
            List<ErpSaleOrderImportExcelVO> saleRows = ExcelUtils.read(file, ErpSaleOrderImportExcelVO.class, 5000);
            document = buildSaleOrderDocument(saleRows);
            rows = saleRows;
        } else {
            List<ErpPurchaseOrderImportExcelVO> purchaseRows =
                    ExcelUtils.read(file, ErpPurchaseOrderImportExcelVO.class, 5000);
            document = buildPurchaseOrderDocument(purchaseRows);
            rows = purchaseRows;
        }
        matchMasterData(document);
        hydrateProductUnits(document);
        ErpRecognitionValidation validation = validateDocument(document, bizType);
        ErpRecognitionTaskDO task = ErpRecognitionTaskDO.builder()
                .bizType(bizType)
                .status(STATUS_REVIEW_REQUIRED)
                .sourceFileUrl(sourceFileUrl)
                .documentType(bizType)
                .providerType("EXCEL")
                .finalConfidence(BigDecimal.ONE)
                .resultVersion(1)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build();
        taskMapper.insert(task);
        resultMapper.insert(ErpRecognitionResultDO.builder()
                .taskId(task.getId())
                .rawResponse(JsonUtils.toJsonString(rows))
                .normalizedJson(JsonUtils.toJsonString(document))
                .validationJson(JsonUtils.toJsonString(validation))
                .resultVersion(1)
                .build());
        return task.getId();
    }

    private ErpRecognitionDocument buildSaleOrderDocument(List<ErpSaleOrderImportExcelVO> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Excel 文件没有可导入的数据");
        }
        ErpRecognitionDocument document = new ErpRecognitionDocument();
        document.setDocumentType(BIZ_TYPE_SALE_ORDER);
        document.setItems(new ArrayList<>());
        String customerName = null;
        String documentDate = null;
        String documentNo = null;
        String saleUserName = null;
        String accountName = null;
        BigDecimal discountPercent = null;
        for (ErpSaleOrderImportExcelVO row : rows) {
            if (row == null) {
                continue;
            }
            customerName = firstText(row.getCustomerName(), customerName);
            documentDate = firstText(row.getDocumentDate(), documentDate);
            documentNo = firstText(row.getDocumentNo(), documentNo);
            saleUserName = firstText(row.getSaleUserName(), saleUserName);
            accountName = firstText(row.getAccountName(), accountName);
            discountPercent = firstDecimal(row.getDiscountPercent(), discountPercent);
            if (!StringUtils.hasText(row.getProductCode()) && !StringUtils.hasText(row.getProductName())
                    && row.getCount() == null) {
                continue;
            }
            ErpRecognitionDocument.Item item = new ErpRecognitionDocument.Item();
            item.setProductCode(trimToNull(row.getProductCode()));
            item.setProductName(trimToNull(row.getProductName()));
            item.setCount(row.getCount());
            item.setProductPrice(row.getProductPrice());
            item.setTaxPercent(normalizeTaxPercent(row.getTaxPercent()));
            item.setRemark(trimToNull(row.getRemark()));
            document.getItems().add(item);
        }
        document.setCustomerName(trimToNull(customerName));
        document.setCustomerId(findCustomerId(document.getCustomerName()));
        document.setSaleUserId(findSaleUserId(saleUserName));
        document.setAccountId(findAccountId(accountName));
        document.setDocumentDate(normalizeExcelDate(documentDate));
        document.setDocumentNo(trimToNull(documentNo));
        document.setDiscountPercent(discountPercent == null ? BigDecimal.ZERO : discountPercent);
        return document;
    }

    private ErpRecognitionDocument buildPurchaseOrderDocument(List<ErpPurchaseOrderImportExcelVO> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Excel 文件没有可导入的数据");
        }
        ErpRecognitionDocument document = new ErpRecognitionDocument();
        document.setDocumentType(BIZ_TYPE_PURCHASE_ORDER);
        document.setItems(new ArrayList<>());
        String supplierName = null;
        String documentDate = null;
        String documentNo = null;
        String accountName = null;
        for (ErpPurchaseOrderImportExcelVO row : rows) {
            if (row == null) {
                continue;
            }
            supplierName = firstText(row.getSupplierName(), supplierName);
            documentDate = firstText(row.getDocumentDate(), documentDate);
            documentNo = firstText(row.getDocumentNo(), documentNo);
            accountName = firstText(row.getAccountName(), accountName);
            if (!StringUtils.hasText(row.getProductCode()) && !StringUtils.hasText(row.getProductName())
                    && row.getCount() == null) {
                continue;
            }
            ErpRecognitionDocument.Item item = new ErpRecognitionDocument.Item();
            item.setProductCode(trimToNull(row.getProductCode()));
            item.setProductName(trimToNull(row.getProductName()));
            item.setCount(row.getCount());
            item.setProductPrice(row.getProductPrice());
            item.setTaxPercent(normalizeTaxPercent(row.getTaxPercent()));
            item.setRemark(trimToNull(row.getRemark()));
            document.getItems().add(item);
        }
        document.setSupplierName(trimToNull(supplierName));
        document.setDocumentDate(normalizeExcelDate(documentDate));
        document.setDocumentNo(trimToNull(documentNo));
        document.setAccountName(trimToNull(accountName));
        document.setAccountId(findAccountId(document.getAccountName()));
        return document;
    }

    private static String firstText(String current, String previous) {
        return StringUtils.hasText(current) ? current.trim() : previous;
    }

    private static BigDecimal firstDecimal(BigDecimal current, BigDecimal previous) {
        return current != null ? current : previous;
    }

    /**
     * Excel 单元格可能是普通数字、带百分号的文本，或者 Excel 百分比格式对应的
     * 小数值（例如 13% 读取为 0.13）。ERP 业务字段统一按百分数保存。
     */
    static BigDecimal normalizeTaxPercent(String value) {
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
            BigDecimal percent = new BigDecimal(normalized);
            if (!hasPercentSign && percent.compareTo(BigDecimal.ZERO) > 0
                    && percent.compareTo(BigDecimal.ONE) < 0) {
                percent = percent.movePointRight(2);
            }
            return percent;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeExcelDate(String value) {
        String date = trimToNull(value);
        if (date == null) {
            return null;
        }
        if (date.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            return date.replace('/', '-');
        }
        return date;
    }

    @Override
    public void retryTask(Long taskId) {
        ErpRecognitionTaskDO task = validateTaskExists(taskId);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw exception(RECOGNITION_TASK_STATE_INVALID, task.getStatus());
        }
        processTask(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long taskId) {
        ErpRecognitionTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw exception(RECOGNITION_TASK_NOT_EXISTS);
        }
        if (!STATUS_FAILED.equals(task.getStatus())
                && !STATUS_REVIEW_REQUIRED.equals(task.getStatus())
                && !STATUS_IMPORTED.equals(task.getStatus())) {
            throw exception(RECOGNITION_TASK_STATE_INVALID, task.getStatus());
        }

        ErpRecognitionImportRecordDO importRecord = importRecordMapper.selectByTaskId(taskId);
        if (importRecord != null) {
            importRecordMapper.deleteById(importRecord.getId());
        }
        ErpRecognitionResultDO result = resultMapper.selectByTaskId(taskId);
        if (result != null) {
            resultMapper.deleteById(result.getId());
        }
        taskMapper.deleteById(taskId);
    }

    private void processTask(Long taskId) {
        ErpRecognitionTaskDO task = validateTaskExists(taskId);
        taskMapper.updateById(new ErpRecognitionTaskDO().setId(taskId)
                .setStatus(STATUS_PROCESSING).setErrorMessage(null).setStartedAt(LocalDateTime.now()).setFinishedAt(null));
        try {
            String rawResponse = documentRecognitionProvider.recognize(task.getSourceFileUrl(), task.getBizType());
            ErpRecognitionDocument document = normalize(rawResponse, task.getBizType());
            ErpRecognitionValidation validation = validateDocument(document, task.getBizType());
            ErpRecognitionResultDO previousResult = resultMapper.selectByTaskId(taskId);
            int resultVersion = previousResult == null ? 1 : previousResult.getResultVersion() + 1;
            ErpRecognitionResultDO result = ErpRecognitionResultDO.builder()
                    .taskId(taskId)
                    .rawResponse(rawResponse)
                    .normalizedJson(JsonUtils.toJsonString(document))
                    .validationJson(JsonUtils.toJsonString(validation))
                    .resultVersion(resultVersion)
                    .build();
            if (previousResult == null) {
                resultMapper.insert(result);
            } else {
                result.setId(previousResult.getId());
                resultMapper.updateById(result);
            }
            taskMapper.updateById(new ErpRecognitionTaskDO().setId(taskId)
                    .setStatus(STATUS_REVIEW_REQUIRED)
                    .setDocumentType(document.getDocumentType())
                    .setProviderType(documentRecognitionProvider.getType())
                    .setFinalConfidence(document.getFinalConfidence())
                    .setResultVersion(resultVersion)
                    .setFinishedAt(LocalDateTime.now()));
        } catch (Exception e) {
            taskMapper.updateById(new ErpRecognitionTaskDO().setId(taskId)
                    .setStatus(STATUS_FAILED)
                    .setProviderType(documentRecognitionProvider.getType())
                    .setErrorMessage(safeErrorMessage(e))
                    .setFinishedAt(LocalDateTime.now()));
        }
    }

    @Override
    public PageResult<ErpRecognitionTaskDO> getTaskPage(ErpRecognitionTaskPageReqVO pageReqVO) {
        return taskMapper.selectPage(pageReqVO);
    }

    @Override
    public ErpRecognitionTaskDO getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public ErpRecognitionResultDO getResultByTaskId(Long taskId) {
        return resultMapper.selectByTaskId(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void correctResult(ErpRecognitionResultCorrectReqVO correctReqVO) {
        ErpRecognitionTaskDO task = validateTaskExists(correctReqVO.getTaskId());
        validateEditableStatus(task);
        ErpRecognitionResultDO result = validateResultVersion(task, correctReqVO.getResultVersion());
        ErpRecognitionDocument document = buildCorrectedDocument(correctReqVO, task.getBizType());
        hydrateProductUnits(document);
        ErpRecognitionValidation validation = validateDocument(document, task.getBizType());
        int nextVersion = result.getResultVersion() + 1;
        resultMapper.updateById(new ErpRecognitionResultDO().setId(result.getId())
                .setNormalizedJson(JsonUtils.toJsonString(document))
                .setValidationJson(JsonUtils.toJsonString(validation))
                .setResultVersion(nextVersion));
        taskMapper.updateById(new ErpRecognitionTaskDO().setId(task.getId())
                .setStatus(STATUS_REVIEW_REQUIRED)
                .setResultVersion(nextVersion)
                .setFinalConfidence(document.getFinalConfidence())
                .setReviewer(null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmTask(Long taskId, Integer resultVersion) {
        ErpRecognitionTaskDO task = validateTaskExists(taskId);
        validateEditableStatus(task);
        ErpRecognitionResultDO result = validateResultVersion(task, resultVersion);
        ErpRecognitionValidation validation = JsonUtils.parseObject(result.getValidationJson(), ErpRecognitionValidation.class);
        if (validation == null || !validation.isPassed()) {
            throw exception(RECOGNITION_TASK_STATE_INVALID, "识别结果存在未处理的校验错误");
        }
        taskMapper.updateById(new ErpRecognitionTaskDO().setId(taskId)
                .setStatus(STATUS_CONFIRMED)
                .setReviewer(String.valueOf(SecurityFrameworkUtils.getLoginUserId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importRecognitionTask(Long taskId, Integer resultVersion) {
        ErpRecognitionTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw exception(RECOGNITION_TASK_NOT_EXISTS);
        }
        if (!STATUS_CONFIRMED.equals(task.getStatus())) {
            throw exception(RECOGNITION_TASK_STATE_INVALID, task.getStatus());
        }
        ErpRecognitionResultDO result = validateResultVersion(task, resultVersion);
        ErpRecognitionImportRecordDO existingRecord = importRecordMapper.selectByTaskId(taskId);
        if (existingRecord != null && STATUS_IMPORTED.equals(existingRecord.getImportStatus())) {
            return existingRecord.getTargetId();
        }
        ErpRecognitionDocument document = JsonUtils.parseObject(result.getNormalizedJson(), ErpRecognitionDocument.class);
        ErpRecognitionValidation validation = validateDocument(document, task.getBizType());
        if (!validation.isPassed()) {
            throw exception(RECOGNITION_TASK_STATE_INVALID, "识别结果存在未处理的校验错误");
        }
        Long targetId;
        if (BIZ_TYPE_PURCHASE_ORDER.equals(task.getBizType())) {
            targetId = purchaseOrderService.createPurchaseOrder(toPurchaseOrderSaveReqVO(task, document));
        } else if (BIZ_TYPE_PURCHASE_RETURN.equals(task.getBizType())) {
            targetId = purchaseReturnService.createPurchaseReturn(toPurchaseReturnSaveReqVO(task, document));
        } else if (BIZ_TYPE_SUPPLIER.equals(task.getBizType())) {
            targetId = supplierService.createSupplier(toSupplierSaveReqVO(document));
        } else if (BIZ_TYPE_SALE_ORDER.equals(task.getBizType())) {
            targetId = saleOrderService.createSaleOrder(toSaleOrderSaveReqVO(task, document));
        } else {
            throw exception(RECOGNITION_UNSUPPORTED_BIZ_TYPE, task.getBizType());
        }
        importRecordMapper.insert(ErpRecognitionImportRecordDO.builder()
                .taskId(taskId)
                .bizType(task.getBizType())
                .targetId(targetId)
                .importStatus(STATUS_IMPORTED)
                .idempotencyKey("recognition-task-" + taskId)
                .confirmedJson(result.getNormalizedJson())
                .confirmedBy(String.valueOf(SecurityFrameworkUtils.getLoginUserId()))
                .confirmedAt(LocalDateTime.now())
                .build());
        taskMapper.updateById(new ErpRecognitionTaskDO().setId(taskId)
                .setStatus(STATUS_IMPORTED)
                .setImportedTargetId(targetId));
        return targetId;
    }

    private ErpRecognitionDocument normalize(String rawResponse, String bizType) {
        ErpRecognitionDocument document = JsonUtils.parseObject(rawResponse, ErpRecognitionDocument.class);
        if (document == null) {
            throw new IllegalStateException("OCR 服务返回的结果不是有效 JSON");
        }
        document.setDocumentType(StringUtils.hasText(document.getDocumentType()) ? document.getDocumentType() : bizType);
        if (document.getItems() == null) {
            document.setItems(new ArrayList<>());
        }
        matchMasterData(document);
        hydrateProductUnits(document);
        return document;
    }

    private ErpRecognitionDocument buildCorrectedDocument(ErpRecognitionResultCorrectReqVO reqVO, String bizType) {
        ErpRecognitionDocument document = new ErpRecognitionDocument();
        document.setDocumentType(bizType);
        document.setDocumentNo(reqVO.getDocumentNo());
        document.setDocumentDate(reqVO.getDocumentDate());
        document.setOrderNo(reqVO.getOrderNo());
        document.setReturnTime(reqVO.getReturnTime());
        document.setAccountId(reqVO.getAccountId());
        document.setDiscountPercent(reqVO.getDiscountPercent());
        document.setSupplierId(reqVO.getSupplierId());
        document.setSupplierName(reqVO.getSupplierName());
        document.setCustomerId(reqVO.getCustomerId());
        document.setCustomerName(reqVO.getCustomerName());
        document.setSaleUserId(reqVO.getSaleUserId());
        document.setContact(reqVO.getContact());
        document.setMobile(reqVO.getMobile());
        document.setTelephone(reqVO.getTelephone());
        document.setEmail(reqVO.getEmail());
        document.setFax(reqVO.getFax());
        document.setTaxNo(reqVO.getTaxNo());
        document.setTaxPercent(reqVO.getTaxPercent());
        document.setBankName(reqVO.getBankName());
        document.setBankAccount(reqVO.getBankAccount());
        document.setBankAddress(reqVO.getBankAddress());
        document.setRemark(reqVO.getRemark());
        document.setItems(reqVO.getItems() == null ? new ArrayList<>() : reqVO.getItems().stream().map(item -> {
            ErpRecognitionDocument.Item target = BeanUtils.toBean(item, ErpRecognitionDocument.Item.class);
            target.setRemark(item.getRemark());
            return target;
        }).toList());
        return document;
    }

    private void matchMasterData(ErpRecognitionDocument document) {
        if (document.getAccountId() == null && StringUtils.hasText(document.getAccountName())) {
            document.setAccountId(findAccountId(document.getAccountName()));
        }
        if (document.getSupplierId() == null && StringUtils.hasText(document.getSupplierName())) {
            List<ErpSupplierDO> matched = supplierService.getSupplierListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                    .filter(supplier -> sameText(supplier.getName(), document.getSupplierName())).toList();
            if (matched.size() == 1) {
                document.setSupplierId(matched.get(0).getId());
            }
        }
        if (document.getCustomerId() == null && StringUtils.hasText(document.getCustomerName())) {
            List<ErpCustomerDO> matched = customerService.getCustomerListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                    .filter(customer -> sameText(customer.getName(), document.getCustomerName())).toList();
            if (matched.size() == 1) {
                document.setCustomerId(matched.get(0).getId());
            }
        }
        List<ErpProductRespVO> products = productService.getProductVOListByStatus(CommonStatusEnum.ENABLE.getStatus());
        for (ErpRecognitionDocument.Item item : document.getItems()) {
            if (item.getProductId() != null) {
                continue;
            }
            List<ErpProductRespVO> matched = products.stream()
                    .filter(product -> sameText(product.getBarCode(), item.getProductCode())
                            || sameText(product.getName(), item.getProductName()))
                    .toList();
            if (matched.size() == 1) {
                item.setProductId(matched.get(0).getId());
                item.setProductUnitId(matched.get(0).getUnitId());
            }
        }
    }

    private void hydrateProductUnits(ErpRecognitionDocument document) {
        for (ErpRecognitionDocument.Item item : document.getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            ErpProductDO product = productService.getProduct(item.getProductId());
            if (product != null) {
                item.setProductUnitId(product.getUnitId());
                if (!StringUtils.hasText(item.getProductName())) {
                    item.setProductName(product.getName());
                }
            }
        }
    }

    private ErpRecognitionValidation validateDocument(ErpRecognitionDocument document, String bizType) {
        ErpRecognitionValidation validation = new ErpRecognitionValidation();
        if (!isSupportedBizType(bizType)) {
            validation.getErrors().add("不支持的识别业务类型：" + bizType);
            validation.setPassed(false);
            return validation;
        }
        if (BIZ_TYPE_SUPPLIER.equals(bizType)) {
            validateSupplierDocument(document, validation);
        } else if (BIZ_TYPE_SALE_ORDER.equals(bizType)) {
            validateSaleOrderDocument(document, validation);
        } else {
            if (document.getSupplierId() == null) {
                validation.getErrors().add("未能唯一匹配供应商，请人工选择");
            } else {
                try {
                    supplierService.validateSupplier(document.getSupplierId());
                } catch (RuntimeException e) {
                    validation.getErrors().add("所选供应商无效");
                }
            }
            String dateValue = BIZ_TYPE_PURCHASE_RETURN.equals(bizType)
                    ? document.getReturnTime() : document.getDocumentDate();
            if (!isDate(dateValue)) {
                validation.getErrors().add((BIZ_TYPE_PURCHASE_RETURN.equals(bizType) ? "退货时间" : "订单日期")
                        + "无效，请按 YYYY-MM-DD 或 YYYY-MM-DDTHH:mm:ss 填写");
            }
            if (BIZ_TYPE_PURCHASE_RETURN.equals(bizType)) {
                if (!StringUtils.hasText(document.getOrderNo())) {
                    validation.getErrors().add("未识别到关联采购订单号");
                }
                if (document.getAccountId() == null) {
                    validation.getErrors().add("请选择结算账户");
                }
                validateReturnItems(document, validation);
            } else {
                validatePurchaseOrderItems(document, validation);
            }
        }
        validation.setPassed(validation.getErrors().isEmpty());
        return validation;
    }

    private void validateSupplierDocument(ErpRecognitionDocument document, ErpRecognitionValidation validation) {
        if (!StringUtils.hasText(document.getSupplierName())) {
            validation.getErrors().add("供应商名称不能为空");
        }
        if (StringUtils.hasText(document.getEmail()) && !document.getEmail().contains("@")) {
            validation.getWarnings().add("电子邮箱格式可能不正确，请人工核对");
        }
    }

    private void validatePurchaseOrderItems(ErpRecognitionDocument document, ErpRecognitionValidation validation) {
        validateProductItems(document, validation, false, "采购");
    }

    private Long findCustomerId(String customerName) {
        if (!StringUtils.hasText(customerName)) {
            return null;
        }
        List<ErpCustomerDO> matched = customerService.getCustomerListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                .filter(customer -> sameText(customer.getName(), customerName)).toList();
        return matched.size() == 1 ? matched.get(0).getId() : null;
    }

    private Long findAccountId(String accountName) {
        if (!StringUtils.hasText(accountName)) {
            return null;
        }
        String normalizedName = normalizeMatchText(accountName);
        List<ErpAccountDO> accounts = accountService
                .getAccountListByStatus(CommonStatusEnum.ENABLE.getStatus());
        List<ErpAccountDO> exactMatches = accounts.stream()
                .filter(account -> normalizedName.equals(normalizeMatchText(account.getName()))
                        || normalizedName.equals(normalizeMatchText(account.getNo())))
                .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.get(0).getId();
        }

        // 账户字段经常会同时包含“账户名称/编码”或 OCR 误识别出的边界文字。
        // 仅在唯一匹配时采用包含关系，避免自动选错结算账户。
        List<ErpAccountDO> containsMatches = accounts.stream()
                .filter(account -> containsNormalized(normalizedName, account.getName())
                        || containsNormalized(normalizedName, account.getNo()))
                .toList();
        return containsMatches.size() == 1 ? containsMatches.get(0).getId() : null;
    }

    private Long findSaleUserId(String saleUserName) {
        if (!StringUtils.hasText(saleUserName)) {
            return null;
        }
        List<AdminUserRespDTO> matched = adminUserApi.getUserListByNickname(saleUserName.trim()).getCheckedData().stream()
                .filter(user -> sameText(user.getNickname(), saleUserName)).toList();
        return matched.size() == 1 ? matched.get(0).getId() : null;
    }

    private void validateReturnItems(ErpRecognitionDocument document, ErpRecognitionValidation validation) {
        validateProductItems(document, validation, true, "采购退货");
    }

    private void validateSaleOrderDocument(ErpRecognitionDocument document, ErpRecognitionValidation validation) {
        if (document.getCustomerId() == null) {
            validation.getErrors().add("未能唯一匹配客户，请人工选择");
        } else {
            try {
                customerService.validateCustomer(document.getCustomerId());
            } catch (RuntimeException e) {
                validation.getErrors().add("所选客户无效");
            }
        }
        if (!isDate(document.getDocumentDate())) {
            validation.getErrors().add("订单日期无效，请按 YYYY-MM-DD 或 YYYY-MM-DDTHH:mm:ss 填写");
        }
        validateProductItems(document, validation, false, "销售");
    }

    private void validateProductItems(ErpRecognitionDocument document, ErpRecognitionValidation validation,
                                     boolean requireWarehouse, String subject) {
        if (document.getItems() == null || document.getItems().isEmpty()) {
            validation.getErrors().add("未识别到" + subject + "明细");
            return;
        }
        for (int index = 0; index < document.getItems().size(); index++) {
            ErpRecognitionDocument.Item item = document.getItems().get(index);
            int lineNo = index + 1;
            if (item.getProductId() == null || productService.getProduct(item.getProductId()) == null) {
                validation.getErrors().add("第 " + lineNo + " 行未匹配有效产品");
            }
            if (item.getCount() == null || item.getCount().compareTo(BigDecimal.ZERO) <= 0) {
                validation.getErrors().add("第 " + lineNo + " 行数量必须大于 0");
            }
            if (requireWarehouse && item.getWarehouseId() == null) {
                validation.getErrors().add("第 " + lineNo + " 行请选择仓库");
            }
            if (item.getProductPrice() == null) {
                validation.getWarnings().add("第 " + lineNo + " 行未识别到单价，导入后可在业务草稿中补充");
            }
        }
    }

    private ErpPurchaseOrderSaveReqVO toPurchaseOrderSaveReqVO(ErpRecognitionTaskDO task,
                                                                ErpRecognitionDocument document) {
        ErpPurchaseOrderSaveReqVO reqVO = new ErpPurchaseOrderSaveReqVO();
        reqVO.setSupplierId(document.getSupplierId());
        reqVO.setAccountId(document.getAccountId());
        reqVO.setOrderTime(parseDateTime(document.getDocumentDate()));
        reqVO.setDiscountPercent(BigDecimal.ZERO);
        reqVO.setFileUrl(task.getSourceFileUrl());
        reqVO.setRemark(StringUtils.hasText(document.getRemark())
                ? document.getRemark() : buildImportRemark(document.getDocumentNo()));
        reqVO.setItems(document.getItems().stream().map(item -> {
            ErpProductDO product = productService.getProduct(item.getProductId());
            ErpPurchaseOrderSaveReqVO.Item target = new ErpPurchaseOrderSaveReqVO.Item();
            target.setProductId(item.getProductId());
            target.setProductUnitId(product.getUnitId());
            target.setProductPrice(item.getProductPrice());
            target.setCount(item.getCount());
            target.setTaxPercent(item.getTaxPercent());
            target.setRemark(item.getRemark());
            return target;
        }).toList());
        return reqVO;
    }

    private ErpSaleOrderSaveReqVO toSaleOrderSaveReqVO(ErpRecognitionTaskDO task,
                                                        ErpRecognitionDocument document) {
        ErpSaleOrderSaveReqVO reqVO = new ErpSaleOrderSaveReqVO();
        reqVO.setCustomerId(document.getCustomerId());
        reqVO.setOrderTime(parseDateTime(document.getDocumentDate()));
        reqVO.setSaleUserId(document.getSaleUserId());
        reqVO.setAccountId(document.getAccountId());
        reqVO.setDiscountPercent(document.getDiscountPercent() == null
                ? BigDecimal.ZERO : document.getDiscountPercent());
        reqVO.setFileUrl(task.getSourceFileUrl());
        reqVO.setRemark(StringUtils.hasText(document.getRemark())
                ? document.getRemark() : buildImportRemark(document.getDocumentNo()));
        reqVO.setItems(document.getItems().stream().map(item -> {
            ErpProductDO product = productService.getProduct(item.getProductId());
            ErpSaleOrderSaveReqVO.Item target = new ErpSaleOrderSaveReqVO.Item();
            target.setProductId(item.getProductId());
            target.setProductUnitId(product.getUnitId());
            target.setProductPrice(item.getProductPrice());
            target.setCount(item.getCount());
            target.setTaxPercent(item.getTaxPercent());
            target.setRemark(item.getRemark());
            return target;
        }).toList());
        return reqVO;
    }

    private ErpPurchaseReturnSaveReqVO toPurchaseReturnSaveReqVO(ErpRecognitionTaskDO task,
                                                                  ErpRecognitionDocument document) {
        ErpPurchaseOrderDO order = purchaseOrderService.getPurchaseOrderByNo(document.getOrderNo().trim());
        if (order == null) {
            throw new IllegalStateException("未找到关联采购订单：" + document.getOrderNo());
        }
        ErpPurchaseOrderDO approvedOrder = purchaseOrderService.validatePurchaseOrder(order.getId());
        List<ErpPurchaseOrderItemDO> orderItems = purchaseOrderService.getPurchaseOrderItemListByOrderId(order.getId());
        ErpPurchaseReturnSaveReqVO reqVO = new ErpPurchaseReturnSaveReqVO();
        reqVO.setAccountId(document.getAccountId() != null ? document.getAccountId() : approvedOrder.getAccountId());
        reqVO.setReturnTime(parseDateTime(document.getReturnTime()));
        reqVO.setOrderId(approvedOrder.getId());
        reqVO.setDiscountPercent(document.getDiscountPercent() == null ? BigDecimal.ZERO : document.getDiscountPercent());
        reqVO.setOtherPrice(BigDecimal.ZERO);
        reqVO.setFileUrl(task.getSourceFileUrl());
        reqVO.setRemark(StringUtils.hasText(document.getRemark())
                ? document.getRemark() : buildImportRemark(document.getDocumentNo()));
        reqVO.setItems(document.getItems().stream().map(item -> {
            ErpPurchaseOrderItemDO orderItem = findOrderItem(orderItems, item);
            ErpPurchaseReturnSaveReqVO.Item target = new ErpPurchaseReturnSaveReqVO.Item();
            target.setOrderItemId(orderItem.getId());
            target.setWarehouseId(item.getWarehouseId());
            target.setProductId(orderItem.getProductId());
            target.setProductUnitId(orderItem.getProductUnitId());
            target.setProductPrice(item.getProductPrice() != null ? item.getProductPrice() : orderItem.getProductPrice());
            target.setCount(item.getCount());
            target.setTaxPercent(item.getTaxPercent());
            target.setRemark(item.getRemark());
            return target;
        }).toList());
        return reqVO;
    }

    private ErpPurchaseOrderItemDO findOrderItem(List<ErpPurchaseOrderItemDO> orderItems,
                                                  ErpRecognitionDocument.Item item) {
        List<ErpPurchaseOrderItemDO> matched = orderItems.stream()
                .filter(orderItem -> item.getOrderItemId() != null
                        ? orderItem.getId().equals(item.getOrderItemId())
                        : orderItem.getProductId().equals(item.getProductId()))
                .toList();
        if (matched.size() != 1) {
            throw new IllegalStateException("识别明细无法唯一匹配关联采购订单项");
        }
        return matched.get(0);
    }

    private ErpSupplierSaveReqVO toSupplierSaveReqVO(ErpRecognitionDocument document) {
        ErpSupplierSaveReqVO reqVO = new ErpSupplierSaveReqVO();
        reqVO.setPartnerCode(buildSupplierPartnerCode());
        reqVO.setName(document.getSupplierName());
        reqVO.setContact(document.getContact());
        reqVO.setMobile(document.getMobile());
        reqVO.setTelephone(document.getTelephone());
        reqVO.setEmail(document.getEmail());
        reqVO.setFax(document.getFax());
        reqVO.setRemark(document.getRemark());
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setSort(0);
        reqVO.setTaxNo(document.getTaxNo());
        reqVO.setTaxPercent(document.getTaxPercent());
        reqVO.setBankName(document.getBankName());
        reqVO.setBankAccount(document.getBankAccount());
        reqVO.setBankAddress(document.getBankAddress());
        return reqVO;
    }

    private static String buildSupplierPartnerCode() {
        return "ERP-SUP-OCR-" + java.util.UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private ErpRecognitionTaskDO validateTaskExists(Long taskId) {
        ErpRecognitionTaskDO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw exception(RECOGNITION_TASK_NOT_EXISTS);
        }
        return task;
    }

    private ErpRecognitionResultDO validateResultVersion(ErpRecognitionTaskDO task, Integer resultVersion) {
        if (!resultVersion.equals(task.getResultVersion())) {
            throw exception(RECOGNITION_RESULT_VERSION_EXPIRED);
        }
        ErpRecognitionResultDO result = resultMapper.selectByTaskId(task.getId());
        if (result == null || !resultVersion.equals(result.getResultVersion())) {
            throw exception(RECOGNITION_RESULT_VERSION_EXPIRED);
        }
        return result;
    }

    private void validateEditableStatus(ErpRecognitionTaskDO task) {
        if (!STATUS_REVIEW_REQUIRED.equals(task.getStatus()) && !STATUS_CONFIRMED.equals(task.getStatus())) {
            throw exception(RECOGNITION_TASK_STATE_INVALID, task.getStatus());
        }
    }

    private void validateBizType(String bizType) {
        if (!isSupportedBizType(bizType)) {
            throw exception(RECOGNITION_UNSUPPORTED_BIZ_TYPE, bizType);
        }
    }

    private static boolean isSupportedBizType(String bizType) {
        return BIZ_TYPE_PURCHASE_ORDER.equals(bizType)
                || BIZ_TYPE_PURCHASE_RETURN.equals(bizType)
                || BIZ_TYPE_SUPPLIER.equals(bizType)
                || BIZ_TYPE_SALE_ORDER.equals(bizType);
    }

    private static boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && normalizeMatchText(left).equals(normalizeMatchText(right));
    }

    private static boolean containsNormalized(String source, String candidate) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(candidate)) {
            return false;
        }
        String normalizedCandidate = normalizeMatchText(candidate);
        return normalizedCandidate.length() >= 2
                && (source.contains(normalizedCandidate) || normalizedCandidate.contains(source));
    }

    private static String normalizeMatchText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replace('：', ':')
                .replace('－', '-')
                .replace('—', '-')
                .replaceAll("[\\s　:：,，;；|｜]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isDate(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            parseDateTime(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            throw new DateTimeParseException("日期不能为空", value == null ? "" : value, 0);
        }
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay();
        }
        return LocalDateTime.parse(value);
    }

    private static String buildImportRemark(String documentNo) {
        return StringUtils.hasText(documentNo) ? "图片识别导入，外部单号：" + documentNo : "图片识别导入";
    }

    private static String safeErrorMessage(Exception e) {
        String message = e.getMessage();
        return StringUtils.hasText(message) ? message.substring(0, Math.min(message.length(), 500)) : "识别服务调用失败";
    }

}
