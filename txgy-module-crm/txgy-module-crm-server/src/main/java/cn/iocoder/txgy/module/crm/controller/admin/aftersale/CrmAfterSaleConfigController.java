package cn.iocoder.txgy.module.crm.controller.admin.aftersale;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.config.CrmAfterSaleConfigSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import cn.iocoder.txgy.module.crm.service.aftersale.CrmAfterSaleConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CRM 售后设置")
@RestController
@RequestMapping("/crm/after-sale/config")
@Validated
public class CrmAfterSaleConfigController {

    @Resource
    private CrmAfterSaleConfigService configService;

    @GetMapping("/get")
    @Operation(summary = "获得售后设置")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-config:query')")
    public CommonResult<CrmAfterSaleConfigDO> getConfig() {
        return success(configService.getConfig());
    }

    @PutMapping("/update")
    @Operation(summary = "更新售后设置")
    @PreAuthorize("@ss.hasPermission('crm:after-sale-config:update')")
    public CommonResult<Boolean> updateConfig(@Valid @RequestBody CrmAfterSaleConfigSaveReqVO updateReqVO) {
        configService.updateConfig(updateReqVO);
        return success(true);
    }

}
