package cn.iocoder.txgy.module.wms.controller.admin.crm;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.crm.vo.WmsCrmProductRespVO;
import cn.iocoder.txgy.module.wms.service.inventory.WmsCrmProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "CRM 对接 - WMS 商品只读")
@RestController
@RequestMapping("/wms/crm/products")
@RequiredArgsConstructor
@Validated
public class WmsCrmProductController {
    private final WmsCrmProductQueryService service;

    @GetMapping("/page")
    @Operation(summary = "供 CRM 添加商品时读取商品 SKU 分页")
    @PreAuthorize("@ss.hasPermission('wms:item:query')")
    public CommonResult<PageResult<WmsCrmProductRespVO>> getPage(@Valid WmsCrmProductPageReqVO request) {
        return success(service.getPage(request));
    }
}
