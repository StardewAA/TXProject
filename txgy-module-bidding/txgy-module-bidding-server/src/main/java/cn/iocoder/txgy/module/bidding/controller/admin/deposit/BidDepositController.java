package cn.iocoder.txgy.module.bidding.controller.admin.deposit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositRefundReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.deposit.vo.BidDepositSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.deposit.BidDepositDO;
import cn.iocoder.txgy.module.bidding.service.deposit.BidDepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 投标保证金登记。
 *
 * <p>登记投标人缴纳的投标保证金，登记时校验金额 ≤ 估算价 2%（条例第26条），
 * 招标方可分页查询保证金台账，作为合同签订后 5 日内退还（条例第58条）的事实基础。
 */
@Tag(name = "管理后台 - 投标保证金")
@RestController
@RequestMapping("/bidding/deposit")
@Validated
public class BidDepositController {

    @Resource
    private BidDepositService bidDepositService;

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:deposit:save')")
    @Operation(summary = "登记保证金（投标方缴纳时调用，校验 2% 限额，幂等）")
    @Idempotent
    public CommonResult<Long> save(@Valid @RequestBody BidDepositSaveReqVO reqVO) {
        return success(bidDepositService.save(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:deposit:query')")
    @Operation(summary = "保证金详情")
    public CommonResult<BidDepositRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(bidDepositService.get(id), BidDepositRespVO.class));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:deposit:query')")
    @Operation(summary = "保证金台账分页（招标方查询）")
    public CommonResult<PageResult<BidDepositRespVO>> page(@Valid BidDepositPageReqVO reqVO) {
        PageResult<BidDepositDO> page = bidDepositService.page(reqVO);
        return success(BeanUtils.toBean(page, BidDepositRespVO.class));
    }

    @PostMapping("/refund")
    @PreAuthorize("@ss.hasPermission('bidding:deposit:refund')")
    @Operation(summary = "退还保证金（招标方合同签订后退还；已退还不可重复退还）")
    @Idempotent
    public CommonResult<Long> refund(@Valid @RequestBody BidDepositRefundReqVO reqVO) {
        return success(bidDepositService.refund(reqVO));
    }
}
