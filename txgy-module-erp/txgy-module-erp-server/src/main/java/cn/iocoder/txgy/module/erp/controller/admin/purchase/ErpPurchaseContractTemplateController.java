package cn.iocoder.txgy.module.erp.controller.admin.purchase;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractTemplateRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract.ErpPurchaseContractTemplateSaveReqVO;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseContractTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - ERP 采购合同模板")
@RestController
@RequestMapping("/erp/purchase-contract-template")
@Validated
public class ErpPurchaseContractTemplateController {
    @Resource private ErpPurchaseContractTemplateService service;

    @GetMapping("/list") @Operation(summary = "获得可用采购合同模板")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:query')")
    public CommonResult<List<ErpPurchaseContractTemplateRespVO>> list() {
        return success(BeanUtils.toBean(service.getEnabledList(), ErpPurchaseContractTemplateRespVO.class));
    }

    @PostMapping("/create") @Operation(summary = "创建采购合同模板")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:template')")
    public CommonResult<Long> create(@Valid @RequestBody ErpPurchaseContractTemplateSaveReqVO reqVO) {
        return success(service.create(reqVO));
    }

    @PutMapping("/update") @Operation(summary = "修改采购合同模板")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:template')")
    public CommonResult<Boolean> update(@Valid @RequestBody ErpPurchaseContractTemplateSaveReqVO reqVO) {
        service.update(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete") @Operation(summary = "删除采购合同模板")
    @PreAuthorize("@ss.hasPermission('erp:purchase-contract:template')")
    public CommonResult<Boolean> delete(@RequestParam Long id) {
        service.delete(id);
        return success(true);
    }
}
