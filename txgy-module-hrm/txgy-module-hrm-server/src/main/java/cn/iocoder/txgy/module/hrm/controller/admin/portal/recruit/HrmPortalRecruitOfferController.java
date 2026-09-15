package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 个人端招聘 Offer 操作。 */
@RestController
@RequestMapping("/hrm/portal/recruit/offer")
@PreAuthorize("isAuthenticated()")
@Validated
public class HrmPortalRecruitOfferController {

    @Resource
    private HrmRecruitOfferService offerService;

    @PutMapping("/accept")
    @Operation(summary = "接受招聘 Offer")
    public CommonResult<Boolean> accept(@RequestParam("id") Long id) {
        offerService.acceptByCandidate(id, getLoginUserId());
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "拒绝招聘 Offer")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id,
                                         @RequestParam("reason") @NotBlank String reason) {
        offerService.rejectByCandidate(id, getLoginUserId(), reason);
        return success(true);
    }
}
