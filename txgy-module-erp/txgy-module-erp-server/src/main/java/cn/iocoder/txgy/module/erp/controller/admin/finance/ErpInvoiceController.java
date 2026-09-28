package cn.iocoder.txgy.module.erp.controller.admin.finance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoicePageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoiceRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.finance.vo.invoice.ErpInvoiceSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpInvoiceDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.service.finance.ErpInvoiceService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierService;
import cn.iocoder.txgy.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - ERP 发票")
@RestController
@RequestMapping("/erp/invoice")
@Validated
public class ErpInvoiceController {
    @Resource private ErpInvoiceService invoiceService;
    @Resource private ErpSupplierService supplierService;
    @Resource private ErpCustomerService customerService;
    @Resource private AdminUserApi adminUserApi;

    @PostMapping("/create") @Operation(summary = "创建发票")
    @PreAuthorize("@ss.hasPermission('erp:invoice:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpInvoiceSaveReqVO reqVO) { return success(invoiceService.createInvoice(reqVO)); }

    @PutMapping("/update") @Operation(summary = "修改发票")
    @PreAuthorize("@ss.hasPermission('erp:invoice:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpInvoiceSaveReqVO reqVO) { invoiceService.updateInvoice(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除发票")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('erp:invoice:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { invoiceService.deleteInvoice(ids); return success(true); }

    @PutMapping("/certify") @Operation(summary = "认证发票")
    @PreAuthorize("@ss.hasPermission('erp:invoice:update')")
    public CommonResult<Boolean> certify(@RequestParam("id") Long id) { invoiceService.certifyInvoice(id); return success(true); }

    @PutMapping("/void") @Operation(summary = "作废发票")
    @PreAuthorize("@ss.hasPermission('erp:invoice:update')")
    public CommonResult<Boolean> voidInvoice(@RequestParam("id") Long id) { invoiceService.voidInvoice(id); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得发票")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('erp:invoice:query')")
    public CommonResult<ErpInvoiceRespVO> get(@RequestParam("id") Long id) {
        ErpInvoiceDO invoice = invoiceService.getInvoice(id);
        return success(invoice == null ? null : build(invoice));
    }

    @GetMapping("/page") @Operation(summary = "获得发票分页")
    @PreAuthorize("@ss.hasPermission('erp:invoice:query')")
    public CommonResult<PageResult<ErpInvoiceRespVO>> page(@Valid ErpInvoicePageReqVO reqVO) {
        PageResult<ErpInvoiceDO> result = invoiceService.getInvoicePage(reqVO);
        if (CollUtil.isEmpty(result.getList())) return success(PageResult.empty(result.getTotal()));
        Map<Long, ErpSupplierDO> supplierMap = supplierService.getSupplierMap(convertSet(result.getList(), ErpInvoiceDO::getSupplierId));
        Map<Long, ErpCustomerDO> customerMap = customerService.getCustomerMap(convertSet(result.getList(), ErpInvoiceDO::getCustomerId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMapIgnoreTenant(convertSet(result.getList(),
                r -> NumberUtil.parseLong(r.getCreator(), 0L), r -> NumberUtil.parseLong(r.getCreator(), 0L) > 0));
        return success(BeanUtils.toBean(result, ErpInvoiceRespVO.class, vo -> {
            MapUtils.findAndThen(supplierMap, vo.getSupplierId(), supplier -> vo.setSupplierName(supplier.getName()));
            MapUtils.findAndThen(customerMap, vo.getCustomerId(), customer -> vo.setCustomerName(customer.getName()));
            Long creatorId = NumberUtil.parseLong(vo.getCreator(), 0L);
            MapUtils.findAndThen(userMap, creatorId, user -> vo.setCreatorName(user.getNickname()));
        }));
    }

    private ErpInvoiceRespVO build(ErpInvoiceDO invoice) {
        ErpInvoiceRespVO vo = BeanUtils.toBean(invoice, ErpInvoiceRespVO.class);
        if (vo.getSupplierId() != null) {
            ErpSupplierDO supplier = supplierService.getSupplier(vo.getSupplierId());
            if (supplier != null) vo.setSupplierName(supplier.getName());
        }
        if (vo.getCustomerId() != null) {
            ErpCustomerDO customer = customerService.getCustomer(vo.getCustomerId());
            if (customer != null) vo.setCustomerName(customer.getName());
        }
        return vo;
    }
}
