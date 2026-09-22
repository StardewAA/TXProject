package cn.iocoder.txgy.module.trade.controller.app.aftersale;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.service.aftersale.EnterprisePurchaseAfterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** PC 采购中心企业售后端点，与个人售后端点物理分离。 */
@Tag(name = "用户 App - 企业采购售后")
@RestController
@RequestMapping("/trade/purchase-after-sale")
@Validated
public class AppEnterprisePurchaseAfterSaleController {

    @Resource
    private EnterprisePurchaseAfterSaleService enterpriseAfterSales;

    @GetMapping("/page")
    @Operation(summary = "获得本企业询价成交订单售后分页")
    public CommonResult<PageResult<AppAfterSaleRespVO>> getPage(AppAfterSalePageReqVO reqVO) {
        Long memberId = getLoginUserId();
        PageResult<AfterSaleDO> page = enterpriseAfterSales.getPage(memberId, reqVO);
        PageResult<AppAfterSaleRespVO> response = BeanUtils.toBean(page, AppAfterSaleRespVO.class);
        response.getList().forEach(row -> row.setCreatedByCurrentMember(
                page.getList().stream().filter(source -> Objects.equals(source.getId(), row.getId()))
                        .findFirst().map(source -> Objects.equals(source.getUserId(), memberId)).orElse(false)));
        return success(response);
    }

    @GetMapping("/get")
    @Operation(summary = "获得本企业询价成交订单售后")
    @Parameter(name = "id", description = "售后编号", required = true)
    public CommonResult<AppAfterSaleRespVO> get(@RequestParam("id") Long id) {
        EnterprisePurchaseAfterSaleService.AfterSaleAccess access =
                enterpriseAfterSales.get(getLoginUserId(), id);
        AppAfterSaleRespVO response = BeanUtils.toBean(access.afterSale(), AppAfterSaleRespVO.class);
        response.setCreatedByCurrentMember(access.createdByCurrentMember());
        return success(response);
    }

    @PostMapping("/create")
    @Operation(summary = "实际下单人申请企业采购售后")
    public CommonResult<Long> create(@Valid @RequestBody AppAfterSaleCreateReqVO reqVO) {
        return success(enterpriseAfterSales.create(getLoginUserId(), reqVO));
    }

    @PutMapping("/delivery")
    @Operation(summary = "实际下单人填写企业采购退货物流")
    public CommonResult<Boolean> delivery(@Valid @RequestBody AppAfterSaleDeliveryReqVO reqVO) {
        enterpriseAfterSales.delivery(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/cancel")
    @Operation(summary = "实际下单人取消企业采购售后")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        enterpriseAfterSales.cancel(getLoginUserId(), id);
        return success(true);
    }
}
