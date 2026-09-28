package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.evaluation.ErpSupplierEvaluationSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierEvaluationDO;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierEvaluationService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpSupplierService;
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

@Tag(name = "管理后台 - ERP 供应商评价")
@RestController
@RequestMapping("/erp/purchase-evaluation")
@Validated
public class ErpSupplierEvaluationController {
    @Resource private ErpSupplierEvaluationService supplierEvaluationService;
    @Resource private ErpSupplierService supplierService;
    @Resource private AdminUserApi adminUserApi;

    @PostMapping("/create") @Operation(summary = "创建供应商评价")
    @PreAuthorize("@ss.hasPermission('erp:purchase-evaluation:create')")
    public CommonResult<Long> create(@Valid @RequestBody ErpSupplierEvaluationSaveReqVO reqVO) { return success(supplierEvaluationService.createSupplierEvaluation(reqVO)); }

    @PutMapping("/update") @Operation(summary = "修改供应商评价")
    @PreAuthorize("@ss.hasPermission('erp:purchase-evaluation:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpSupplierEvaluationSaveReqVO reqVO) { supplierEvaluationService.updateSupplierEvaluation(reqVO); return success(true); }

    @DeleteMapping("/delete") @Operation(summary = "删除供应商评价")
    @Parameter(name = "ids", description = "编号数组", required = true)
    @PreAuthorize("@ss.hasPermission('erp:purchase-evaluation:delete')")
    public CommonResult<Boolean> delete(@RequestParam("ids") List<Long> ids) { supplierEvaluationService.deleteSupplierEvaluation(ids); return success(true); }

    @GetMapping("/get") @Operation(summary = "获得供应商评价")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('erp:purchase-evaluation:query')")
    public CommonResult<ErpSupplierEvaluationRespVO> get(@RequestParam("id") Long id) {
        ErpSupplierEvaluationDO evaluation = supplierEvaluationService.getSupplierEvaluation(id);
        return success(evaluation == null ? null : build(evaluation));
    }

    @GetMapping("/page") @Operation(summary = "获得供应商评价分页")
    @PreAuthorize("@ss.hasPermission('erp:purchase-evaluation:query')")
    public CommonResult<PageResult<ErpSupplierEvaluationRespVO>> page(@Valid ErpSupplierEvaluationPageReqVO reqVO) {
        PageResult<ErpSupplierEvaluationDO> result = supplierEvaluationService.getSupplierEvaluationPage(reqVO);
        if (CollUtil.isEmpty(result.getList())) return success(PageResult.empty(result.getTotal()));
        Map<Long, ErpSupplierDO> supplierMap = supplierService.getSupplierMap(convertSet(result.getList(), ErpSupplierEvaluationDO::getSupplierId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMapIgnoreTenant(convertSet(result.getList(), r -> Long.parseLong(r.getCreator())));
        return success(BeanUtils.toBean(result, ErpSupplierEvaluationRespVO.class, vo -> {
            MapUtils.findAndThen(supplierMap, vo.getSupplierId(), supplier -> vo.setSupplierName(supplier.getName()));
            MapUtils.findAndThen(userMap, Long.parseLong(vo.getCreator()), user -> vo.setCreatorName(user.getNickname()));
        }));
    }

    private ErpSupplierEvaluationRespVO build(ErpSupplierEvaluationDO evaluation) {
        ErpSupplierEvaluationRespVO vo = BeanUtils.toBean(evaluation, ErpSupplierEvaluationRespVO.class);
        if (vo.getSupplierId() != null) {
            ErpSupplierDO supplier = supplierService.getSupplier(vo.getSupplierId());
            if (supplier != null) vo.setSupplierName(supplier.getName());
        }
        return vo;
    }
}
