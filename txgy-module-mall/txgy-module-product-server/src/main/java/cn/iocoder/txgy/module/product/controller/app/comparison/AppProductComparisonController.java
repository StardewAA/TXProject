package cn.iocoder.txgy.module.product.controller.app.comparison;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.service.comparison.*;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/product/comparison")
@Validated
public class AppProductComparisonController {
    @Resource private ProductComparisonService service;

    @GetMapping("/list")
    public CommonResult<ComparisonListRepository.Selection> list() {
        return success(service.getList(EnterpriseMembershipService.memberId()));
    }
    @PutMapping("/list")
    public CommonResult<ComparisonListRepository.Selection> save(@Valid @RequestBody ComparisonRequest request) {
        return success(service.saveList(EnterpriseMembershipService.memberId(), request));
    }
    @PostMapping("/calculate")
    @PermitAll
    public CommonResult<ComparisonResponse> calculate(@Valid @RequestBody ComparisonRequest request) {
        return success(service.calculate(request));
    }
}
