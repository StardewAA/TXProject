package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.product.vo.product.ErpProductRespVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.purchase.*;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - ERP 采购合同")
@RestController
@RequestMapping("/erp/purchase-contract")
@Validated
public class ErpPurchaseContractController {
    @Resource private ErpPurchaseContractService purchaseContractService;
    @Resource private ErpSupplierService supplierService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private ErpPurchaseOrderService purchaseOrderService;
    @Resource private ErpProductService productService;
    @Resource private ErpInquiryService inquiryService;
    @Resource private ErpPurchaseContractTemplateService templateService;

    @PostMapping("/create") @Operation(summary = "创建采购合同")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpPurchaseContractSaveReqVO reqVO) { return success(purchaseContractService.createPurchaseContract(reqVO)); }

    @PutMapping("/update") @Operation(summary = "修改采购合同")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpPurchaseContractSaveReqVO reqVO) { purchaseContractService.updatePurchaseContract(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除采购合同")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { purchaseContractService.deletePurchaseContract(ids); return success(true); }

    @PutMapping("/submit") @Operation(summary = "提交采购合同审批")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) { purchaseContractService.submitPurchaseContract(id); return success(true); }

    @PutMapping("/approve") @Operation(summary = "审批通过采购合同")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:approve')")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) { purchaseContractService.approvePurchaseContract(id); return success(true); }

    @PutMapping("/reject") @Operation(summary = "驳回采购合同")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:approve')")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) { purchaseContractService.rejectPurchaseContract(id); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得采购合同")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:query')")
    public CommonResult<ErpPurchaseContractRespVO> get(@RequestParam("id") Long id) {
        ErpPurchaseContractDO contract = purchaseContractService.getPurchaseContract(id);
        return success(contract == null ? null : build(contract));
    }

    @GetMapping("/init-from-order") @Operation(summary = "从已审批采购订单初始化合同")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:create')")
    public CommonResult<ErpPurchaseContractRespVO> initFromOrder(@RequestParam("orderId") Long orderId) {
        ErpPurchaseOrderDO order = purchaseOrderService.validatePurchaseOrder(orderId);
        ErpSupplierDO supplier = supplierService.validateSupplier(order.getSupplierId());
        List<ErpPurchaseOrderItemDO> items = purchaseOrderService.getPurchaseOrderItemListByOrderId(orderId);
        Map<Long, ErpProductRespVO> products = productService.getProductVOMap(convertSet(items, ErpPurchaseOrderItemDO::getProductId));
        ErpPurchaseContractRespVO vo = new ErpPurchaseContractRespVO();
        vo.setPurchaseOrderId(order.getId()).setPurchaseOrderNo(order.getNo())
                .setContractName(supplier.getName() + "采购合同")
                .setSupplierId(supplier.getId()).setSupplierName(supplier.getName())
                .setSupplierNameSnapshot(supplier.getName())
                .setTotalAmount(order.getTotalPrice()).setTaxRate(commonTaxRate(items))
                .setCurrency("人民币").setSignDate(LocalDateTime.now())
                .setSignerId(getLoginUserId()).setQualityStandard(buildQualityStandard(order, items, products))
                .setSellerBankName(supplier.getBankName()).setSellerBankAccount(supplier.getBankAccount())
                .setSellerPhone(firstNotBlank(supplier.getTelephone(), supplier.getMobile()))
                .setSellerFax(supplier.getFax());
        applyPaymentDefaults(vo, order);
        applyInquiryDefaults(vo, order);
        templateService.getEnabledList().stream().findFirst().ifPresent(template -> vo
                .setTemplateId(template.getId()).setTemplateName(template.getName())
                .setTemplateVersion(template.getVersion()).setLegalTerms(template.getContent()));
        return success(vo);
    }

    @GetMapping("/export-pdf") @Operation(summary = "导出采购合同 PDF")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:export')")
    public void exportPdf(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        ErpPurchaseContractDO contract = purchaseContractService.validatePurchaseContract(id);
        ErpSupplierDO supplier = supplierService.getSupplier(contract.getSupplierId());
        ErpPurchaseOrderDO order = contract.getPurchaseOrderId() == null ? null
                : purchaseOrderService.getPurchaseOrder(contract.getPurchaseOrderId());
        List<ErpPurchaseOrderItemDO> items = order == null ? Collections.emptyList()
                : purchaseOrderService.getPurchaseOrderItemListByOrderId(order.getId());
        Map<Long, ErpProductRespVO> products = productService.getProductVOMap(convertSet(items, ErpPurchaseOrderItemDO::getProductId));
        String signerName = null;
        if (contract.getSignerId() != null) {
            AdminUserRespDTO signer = adminUserApi.getUser(contract.getSignerId()).getCheckedData();
            signerName = signer == null ? null : signer.getNickname();
        }
        ErpPurchaseContractPdfExporter.write(response, contract, order, items, products, supplier, signerName);
    }

    @GetMapping("/page") @Operation(summary = "获得采购合同分页")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:query')")
    public CommonResult<PageResult<ErpPurchaseContractRespVO>> page(@Valid ErpPurchaseContractPageReqVO reqVO) {
        PageResult<ErpPurchaseContractDO> result = purchaseContractService.getPurchaseContractPage(reqVO);
        if (CollUtil.isEmpty(result.getList())) return success(PageResult.empty(result.getTotal()));
        Map<Long, ErpSupplierDO> supplierMap = supplierService.getSupplierMap(convertSet(result.getList(), ErpPurchaseContractDO::getSupplierId));
        Set<Long> creatorIds = result.getList().stream().map(ErpPurchaseContractDO::getCreator)
                .map(ErpPurchaseContractController::parseLong).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = creatorIds.isEmpty() ? Collections.emptyMap()
                : adminUserApi.getUserMapIgnoreTenant(creatorIds);
        return success(BeanUtils.toBean(result, ErpPurchaseContractRespVO.class, vo -> {
            MapUtils.findAndThen(supplierMap, vo.getSupplierId(), supplier -> vo.setSupplierName(supplier.getName()));
            Long creatorId = parseLong(vo.getCreator());
            if (creatorId != null) {
                MapUtils.findAndThen(userMap, creatorId, user -> vo.setCreatorName(user.getNickname()));
            }
        }));
    }

    private ErpPurchaseContractRespVO build(ErpPurchaseContractDO contract) {
        ErpPurchaseContractRespVO vo = BeanUtils.toBean(contract, ErpPurchaseContractRespVO.class);
        if (vo.getSupplierId() != null) {
            ErpSupplierDO supplier = supplierService.getSupplier(vo.getSupplierId());
            if (supplier != null) vo.setSupplierName(supplier.getName());
        }
        if (vo.getSignerId() != null) {
            AdminUserRespDTO signer = adminUserApi.getUser(vo.getSignerId()).getCheckedData();
            if (signer != null) vo.setSignerName(signer.getNickname());
        }
        vo.setAdvancePaymentAmount(amount(vo.getTotalAmount(), vo.getAdvancePaymentRatio()));
        vo.setAcceptancePaymentAmount(amount(vo.getTotalAmount(), vo.getAcceptancePaymentRatio()));
        vo.setWarrantyRetentionAmount(amount(vo.getTotalAmount(), vo.getWarrantyRetentionRatio()));
        return vo;
    }

    private void applyInquiryDefaults(ErpPurchaseContractRespVO vo, ErpPurchaseOrderDO order) {
        if (!"AWARD_RESULT".equals(order.getSourceType()) || order.getSourceId() == null) return;
        ErpInquiryDO inquiry = inquiryService.getInquiry(order.getSourceId());
        if (inquiry == null) return;
        vo.setDeliveryDeadline(inquiry.getDeliveryDate()).setDeliveryPlace(inquiry.getDeliveryAddress())
                .setCurrency(firstNotBlank(inquiry.getCurrency(), "人民币"))
                .setPaymentTerms(inquiry.getPaymentTerm());
    }

    private String buildQualityStandard(ErpPurchaseOrderDO order, List<ErpPurchaseOrderItemDO> orderItems,
                                        Map<Long, ErpProductRespVO> products) {
        List<String> values = orderItems.stream().map(item -> products.get(item.getProductId()))
                .filter(Objects::nonNull).map(ErpProductRespVO::getStandard)
                .filter(value -> value != null && !value.isBlank()).distinct().collect(Collectors.toList());
        if ("AWARD_RESULT".equals(order.getSourceType()) && order.getSourceId() != null) {
            Collection<ErpInquiryItemDO> inquiryItems = inquiryService.getItemList(List.of(order.getSourceId()));
            values.addAll(inquiryItems.stream().map(ErpInquiryItemDO::getQualityRequirement)
                    .filter(value -> value != null && !value.isBlank()).distinct().toList());
        }
        return values.stream().distinct().collect(Collectors.joining("；"));
    }

    private void applyPaymentDefaults(ErpPurchaseContractRespVO vo, ErpPurchaseOrderDO order) {
        BigDecimal total = order.getTotalPrice();
        BigDecimal deposit = order.getDepositPrice();
        BigDecimal advance = total != null && total.signum() > 0 && deposit != null && deposit.signum() > 0
                ? deposit.multiply(new BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        vo.setAdvancePaymentRatio(advance).setAcceptancePaymentRatio(new BigDecimal("100").subtract(advance))
                .setWarrantyRetentionRatio(BigDecimal.ZERO);
        vo.setAdvancePaymentAmount(amount(total, advance));
        vo.setAcceptancePaymentAmount(amount(total, vo.getAcceptancePaymentRatio()));
        vo.setWarrantyRetentionAmount(BigDecimal.ZERO);
    }

    private static BigDecimal amount(BigDecimal total, BigDecimal ratio) {
        if (total == null || ratio == null) return null;
        return total.multiply(ratio).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal commonTaxRate(List<ErpPurchaseOrderItemDO> items) {
        List<BigDecimal> rates = items.stream().map(ErpPurchaseOrderItemDO::getTaxPercent)
                .filter(Objects::nonNull).distinct().limit(2).toList();
        return rates.size() == 1 ? rates.get(0) : null;
    }

    private static String firstNotBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
