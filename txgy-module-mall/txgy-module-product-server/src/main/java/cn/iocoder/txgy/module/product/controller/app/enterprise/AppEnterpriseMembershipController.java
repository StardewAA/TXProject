package cn.iocoder.txgy.module.product.controller.app.enterprise;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.product.dal.dataobject.enterprise.EnterpriseApplicationDO;
import cn.iocoder.txgy.module.product.controller.app.shop.AppProductShopRespVO;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.product.service.enterprise.EnterpriseMembershipService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/product/enterprise-membership")
public class AppEnterpriseMembershipController {
    @Resource private EnterpriseMembershipService service;
    @GetMapping("/mine")
    public CommonResult<EnterpriseApplicationDO> mine() { return success(service.mine(EnterpriseMembershipService.memberId())); }
    @PostMapping("/apply")
    public CommonResult<Long> apply(@Valid @RequestBody EnterpriseApplyReqVO req) {
        return success(service.apply(EnterpriseMembershipService.memberId(), req));
    }
    @GetMapping("/sales-shops")
    public CommonResult<List<AppProductShopRespVO>> shops() {
        return success(BeanUtils.toBean(service.salesShops(EnterpriseMembershipService.memberId()), AppProductShopRespVO.class));
    }
}
