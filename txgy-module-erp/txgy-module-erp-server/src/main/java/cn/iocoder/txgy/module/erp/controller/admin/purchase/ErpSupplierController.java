package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.ErpSupplierSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierAdmissionCategorySaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierCreditRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierCreditStatusSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierQualificationCheckReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierQualificationCheckRespVO;
import cn.iocoder.txgy.module.erp.service.export.ErpPdfExportUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierQualificationSaveReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.supplier.credit.ErpSupplierRiskEventSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierCreditStatusDO;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierCreditService;
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
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;

@Tag(name = "管理后台 - ERP 供应商")
@RestController
@RequestMapping("/erp/supplier")
@Validated
public class ErpSupplierController {

    @Resource
    private ErpSupplierService supplierService;
    @Resource
    private ErpSupplierCreditService supplierCreditService;

    @PostMapping("/create")
    @Operation(summary = "创建供应商")
    @PreAuthorize("@ss.hasPermission('erp:supplier:create')")
    public CommonResult<Long> createSupplier(@Valid @RequestBody ErpSupplierSaveReqVO createReqVO) {
        return success(supplierService.createSupplier(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新供应商")
    @PreAuthorize("@ss.hasPermission('erp:supplier:update')")
    public CommonResult<Boolean> updateSupplier(@Valid @RequestBody ErpSupplierSaveReqVO updateReqVO) {
        supplierService.updateSupplier(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除供应商")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('erp:supplier:delete')")
    public CommonResult<Boolean> deleteSupplier(@RequestParam("id") Long id) {
        supplierService.deleteSupplier(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得供应商")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('erp:supplier:query')")
    public CommonResult<ErpSupplierRespVO> getSupplier(@RequestParam("id") Long id) {
        ErpSupplierDO supplier = supplierService.getSupplier(id);
        return success(BeanUtils.toBean(supplier, ErpSupplierRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得供应商分页")
    @PreAuthorize("@ss.hasPermission('erp:supplier:query')")
    public CommonResult<PageResult<ErpSupplierRespVO>> getSupplierPage(@Valid ErpSupplierPageReqVO pageReqVO) {
        PageResult<ErpSupplierDO> pageResult = supplierService.getSupplierPage(pageReqVO);
        Map<Long, ErpSupplierCreditStatusDO> creditMap = supplierCreditService.getStatusMap(
                convertList(pageResult.getList(), ErpSupplierDO::getId));
        return success(BeanUtils.toBean(pageResult, ErpSupplierRespVO.class, vo -> {
            ErpSupplierCreditStatusDO credit = creditMap.get(vo.getId());
            if (credit != null) {
                vo.setAuthStatus(credit.getAuthStatus()).setQualificationStatus(credit.getQualificationStatus())
                        .setRiskLevel(credit.getRiskLevel()).setPurchaseLimitStatus(credit.getPurchaseLimitStatus());
            }
        }));
    }

    @GetMapping("/credit/get")
    @Operation(summary = "获得供应商资信详情")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:query', 'erp:supplier-credit:query')")
    public CommonResult<ErpSupplierCreditRespVO> getSupplierCredit(@RequestParam("supplierId") Long supplierId) {
        supplierService.validateSupplier(supplierId);
        return success(supplierCreditService.getCredit(supplierId));
    }

    @PostMapping("/credit/status")
    @Operation(summary = "接收供应商资信状态快照")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:update', 'erp:supplier-credit:sync', "
            + "'erp:supplier-credit:freeze', 'erp:supplier-credit:restore')")
    public CommonResult<Boolean> receiveSupplierCreditStatus(
            @Valid @RequestBody ErpSupplierCreditStatusSaveReqVO reqVO) {
        supplierService.validateSupplier(reqVO.getSupplierId());
        supplierCreditService.receiveCreditStatus(reqVO);
        return success(true);
    }

    @PostMapping("/credit/admission-categories")
    @Operation(summary = "更新供应商准入品类")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:update', 'erp:supplier-credit:sync')")
    public CommonResult<Boolean> replaceAdmissionCategories(
            @Valid @RequestBody ErpSupplierAdmissionCategorySaveReqVO reqVO) {
        supplierService.validateSupplier(reqVO.getSupplierId());
        supplierCreditService.replaceAdmissionCategories(reqVO);
        return success(true);
    }

    @PostMapping("/credit/qualifications")
    @Operation(summary = "更新供应商资质")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:update', 'erp:supplier-credit:sync')")
    public CommonResult<Boolean> replaceQualifications(
            @Valid @RequestBody ErpSupplierQualificationSaveReqVO reqVO) {
        supplierService.validateSupplier(reqVO.getSupplierId());
        supplierCreditService.replaceQualifications(reqVO);
        return success(true);
    }

    @PostMapping("/credit/risk-events")
    @Operation(summary = "新增供应商风险事件")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:update', 'erp:supplier-credit:risk-create')")
    public CommonResult<Long> createRiskEvent(@Valid @RequestBody ErpSupplierRiskEventSaveReqVO reqVO) {
        supplierService.validateSupplier(reqVO.getSupplierId());
        return success(supplierCreditService.createRiskEvent(reqVO));
    }

    @PutMapping("/credit/risk-events/status")
    @Operation(summary = "更新供应商风险事件状态")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:update', 'erp:supplier-credit:risk-handle')")
    public CommonResult<Boolean> updateRiskEventStatus(@RequestParam("id") Long id,
                                                       @RequestParam("status") String status,
                                                       @RequestParam(value = "resolution", required = false)
                                                       String resolution) {
        supplierCreditService.updateRiskEventStatus(id, status, resolution);
        return success(true);
    }

    @PostMapping("/credit/check")
    @Operation(summary = "检查供应商采购资格")
    @PreAuthorize("@ss.hasAnyPermissions('erp:supplier:query', 'erp:supplier-credit:query')")
    public CommonResult<ErpSupplierQualificationCheckRespVO> checkQualification(
            @Valid @RequestBody ErpSupplierQualificationCheckReqVO reqVO) {
        supplierService.validateSupplier(reqVO.getSupplierId());
        return success(supplierCreditService.checkQualification(reqVO));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得供应商精简列表", description = "只包含被开启的供应商，主要用于前端的下拉选项")
    public CommonResult<List<ErpSupplierRespVO>> getSupplierSimpleList() {
        List<ErpSupplierDO> list = supplierService.getSupplierListByStatus(CommonStatusEnum.ENABLE.getStatus());
        return success(convertList(list, supplier -> new ErpSupplierRespVO().setId(supplier.getId()).setName(supplier.getName())));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出供应商 Excel")
    @PreAuthorize("@ss.hasPermission('erp:supplier:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportSupplierExcel(@Valid ErpSupplierPageReqVO pageReqVO,
              HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<ErpSupplierDO> list = supplierService.getSupplierPage(pageReqVO).getList();
        // 导出 Excel
        ExcelUtils.write(response, "供应商.xls", "数据", ErpSupplierRespVO.class,
                        BeanUtils.toBean(list, ErpSupplierRespVO.class));
    }

    @GetMapping("/export-pdf")
    @Operation(summary = "导出供应商 PDF")
    @PreAuthorize("@ss.hasPermission('erp:supplier:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportSupplierPdf(@Valid ErpSupplierPageReqVO pageReqVO,
                                  HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<ErpSupplierDO> list = supplierService.getSupplierPage(pageReqVO).getList();
        ErpPdfExportUtils.write(response, "供应商.pdf", "供应商列表",
                ErpSupplierRespVO.class, BeanUtils.toBean(list, ErpSupplierRespVO.class));
    }

}
