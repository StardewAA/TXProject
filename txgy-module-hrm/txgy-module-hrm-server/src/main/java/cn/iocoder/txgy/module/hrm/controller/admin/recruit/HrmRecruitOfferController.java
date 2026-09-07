package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.application.HrmRecruitOfferOnboardReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 招聘 Offer")
@RestController
@RequestMapping("/hrm/recruit/offer")
@Validated
public class HrmRecruitOfferController {
    @Resource private HrmRecruitOfferService offerService;

    @PostMapping("/create")
    @Operation(summary = "创建 Offer")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:create')")
    public CommonResult<Long> create(@Valid @RequestBody HrmRecruitOfferSaveReqVO reqVO) {
        return success(offerService.create(reqVO));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:query')")
    public CommonResult<HrmRecruitOfferDO> get(@RequestParam("id") Long id) {
        return success(offerService.get(id));
    }

    @PutMapping("/submit")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:submit')")
    public CommonResult<Boolean> submit(@RequestParam("id") Long id) {
        offerService.submit(id); return success(true);
    }

    @PutMapping("/approval-callback")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:approve')")
    public CommonResult<Boolean> approvalCallback(@RequestParam("id") Long id,
                                                   @RequestParam("processInstanceId") String processInstanceId,
                                                   @RequestParam("approvalVersion") Integer approvalVersion,
                                                   @RequestParam("approved") @NotNull Boolean approved) {
        offerService.approvalCallback(id, processInstanceId, approvalVersion, approved);
        return success(true);
    }

    @PutMapping("/withdraw")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:withdraw')")
    public CommonResult<Boolean> withdraw(@RequestParam("id") Long id) {
        offerService.withdraw(id); return success(true);
    }

    @PutMapping("/send")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:send')")
    public CommonResult<Boolean> send(@RequestParam("id") Long id) {
        offerService.send(id); return success(true);
    }

    @PutMapping("/confirm")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:confirm')")
    public CommonResult<Boolean> confirm(@RequestParam("id") Long id) {
        offerService.confirm(id); return success(true);
    }

    @PutMapping("/onboard")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:offer:onboard')")
    public CommonResult<Long> onboard(@Valid @RequestBody HrmRecruitOfferOnboardReqVO reqVO) {
        return success(offerService.onboard(reqVO));
    }
}
