package cn.iocoder.txgy.module.member.controller.admin.platform;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkPageReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountLinkRespVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeReqVO;
import cn.iocoder.txgy.module.member.controller.admin.platform.vo.MemberPlatformAccountMergeRespVO;
import cn.iocoder.txgy.module.member.service.platform.MemberPlatformIdentityReviewService;
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
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.web.core.util.WebFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 平台账号身份审核")
@RestController
@RequestMapping("/member/platform-account-review")
@Validated
public class MemberPlatformAccountReviewController {

    @Resource
    private MemberPlatformIdentityReviewService reviewService;

    @GetMapping("/page")
    @Operation(summary = "查询平台账号冲突和关联链接")
    @PreAuthorize("@ss.hasPermission('member:platform-account:query')")
    public CommonResult<PageResult<MemberPlatformAccountLinkRespVO>> getPage(
            @Valid MemberPlatformAccountLinkPageReqVO request) {
        return success(reviewService.getLinkPage(request));
    }

    @PostMapping("/merge")
    @Operation(summary = "按人工身份审核单归并旧会员链接")
    @PreAuthorize("@ss.hasPermission('member:platform-account:review')")
    public CommonResult<MemberPlatformAccountMergeRespVO> merge(
            @Valid @RequestBody MemberPlatformAccountMergeReqVO request) {
        return success(reviewService.merge(request, getLoginUserId()));
    }
}
